package com.cyxbs.pages.schedule.calendar

import android.content.Context
import com.cyxbs.pages.schedule.domain.calendar.CalendarExportAction
import com.cyxbs.pages.schedule.domain.calendar.CalendarExportPlan
import com.cyxbs.pages.schedule.domain.calendar.CalendarExportPlanner
import com.cyxbs.pages.schedule.domain.calendar.CalendarExportScope
import com.cyxbs.pages.schedule.domain.calendar.CalendarEventProjection
import com.cyxbs.pages.schedule.domain.calendar.ManagedCalendarEvent
import com.cyxbs.pages.schedule.domain.calendar.PlatformCalendarEventRef
import com.cyxbs.pages.schedule.domain.calendar.ScheduleCalendarProjectionFactory
import com.cyxbs.pages.schedule.domain.calendar.ScheduleCalendarSource
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.repository.ScheduleCalendarChange
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepository
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryStatus
import com.cyxbs.pages.schedule.domain.repository.ScheduleSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Android 系统日历的单向导出协调器。
 *
 * 协调器只读取 [ScheduleRepository.snapshot] 与 [ScheduleRepository.calendarChanges]，随后把当前 Schedule 投影
 * 写入应用自己的受管系统日历。系统日历内容永远不是 Schedule 的输入；本类不提供冲突决策、入站刷新或双向
 * 合并能力。
 */
internal class ScheduleCalendarExportCoordinator(
  context: Context?,
  private val repository: ScheduleRepository,
  private val accountId: String,
  private val exportScope: CalendarExportScope,
  private val coroutineScope: CoroutineScope,
  private val ensureAuthorized: () -> Unit,
  private val gateway: ScheduleCalendarExportEventGateway =
    requireNotNull(context) { "Android calendar context is required" }.applicationContext.let { appContext ->
      AndroidScheduleCalendarExportEventGateway(
        AndroidScheduleCalendarGateway(
          context = appContext,
          registry = AndroidManagedCalendarRegistry(appContext),
          accountId = accountId,
        ),
      )
    },
) {
  private val requests = Channel<Set<ScheduleId>?>(Channel.UNLIMITED)
  private var worker: Job? = null

  /**
   * 启动一次账号生命周期内的串行导出 worker。
   *
   * 首次启动执行全量投影；后续普通提交只重算受影响的 Schedule ID。请求使用单一 channel 串行消费，避免两个
   * Provider 批次同时修改同一受管日历。
   */
  fun start() {
    if (worker?.isActive == true) return
    worker = coroutineScope.launch {
      val changeCollector = launch {
        repository.calendarChanges.collect { change ->
          if (change.accountId != accountId) return@collect
          when (change) {
            is ScheduleCalendarChange.Initialized -> requests.send(null)
            is ScheduleCalendarChange.SchedulesCommitted -> requests.send(change.scheduleIds)
            is ScheduleCalendarChange.RemoteCommitted -> requests.send(change.scheduleIds)
          }
        }
      }
      try {
        requests.send(null)
        for (scheduleIds in requests) {
          reconcile(scheduleIds)
        }
      } finally {
        changeCollector.cancel()
      }
    }
  }

  /** 停止当前 worker；已经发出的单次 Provider 调用仍服从 Android Provider 的不可取消边界。 */
  fun stop() {
    worker?.cancel()
    worker = null
  }

  /**
   * 使用当前仓库快照构造并执行一次单向计划。
   *
   * [scheduleIds] 为 `null` 时执行全量；非空时同时截取这些日程的 occurrence exception，并读取 Provider 中同一
   * 身份集合，以便已删除日程产生精确 Delete。可信受管身份中的投射损坏允许重建一次；普通 Provider 失败仍然
   * 失败关闭，不跳过坏行或自动删除。
   */
  private fun reconcile(scheduleIds: Set<ScheduleId>?) {
    ensureAuthorized()
    ScheduleCalendarExportCoordinatorProvider.publishStatus(exportScope, ExportStatus.Running)
    try {
      val snapshot = repository.snapshot.value
      check(snapshot.accountId == accountId) { "Schedule snapshot does not belong to calendar export account" }
      when (val status = snapshot.status) {
        ScheduleRepositoryStatus.Loading -> error("Schedule snapshot is not initialized")
        is ScheduleRepositoryStatus.Corrupted -> throw CorruptedSnapshotException(status.cause)
        else -> Unit
      }

      val stats = try {
        reconcileOnce(snapshot, scheduleIds)
      } catch (_: ManagedCalendarRebuildRequiredException) {
        // 受管日历是 Schedule 的派生投影；版本或托管内容不兼容时只允许重建一次，并强制全量回写。
        ensureAuthorized()
        check(gateway.recreateManagedCalendarForRecovery(ensureAuthorized)) {
          "Calendar Provider did not recreate the managed calendar"
        }
        reconcileOnce(snapshot, scheduleIds = null)
      }
      val status = if (stats.failures.isEmpty()) {
        ExportStatus.Completed(stats)
      } else {
        ExportStatus.PartiallyFailed(stats)
      }
      ScheduleCalendarExportCoordinatorProvider.publishStatus(exportScope, status)
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (corrupted: CorruptedSnapshotException) {
      ScheduleCalendarExportCoordinatorProvider.publishStatus(
        exportScope,
        ExportStatus.CorruptedSnapshot(corrupted.cause ?: corrupted),
      )
    } catch (failure: Throwable) {
      ScheduleCalendarExportCoordinatorProvider.publishStatus(exportScope, ExportStatus.Failed(failure))
    }
  }

  /**
   * 对同一个不可变仓库快照执行一次严格对账。
   *
   * 本方法不捕获 [ManagedCalendarRebuildRequiredException]；外层仅允许恢复一次。重建后的调用必须传 `null`，确保
   * 已被清空的日历重新获得完整 Schedule 投影，而不是只写回触发本轮的增量 ID。
   */
  private fun reconcileOnce(
    snapshot: ScheduleSnapshot,
    scheduleIds: Set<ScheduleId>?,
  ): ExportStats {
    val selectedSchedules = if (scheduleIds == null) {
      snapshot.schedules
    } else {
      snapshot.schedules.filter { it.id in scheduleIds }
    }
    val selectedIds = selectedSchedules.mapTo(mutableSetOf()) { it.id }
    val selectedExceptions = snapshot.exceptions.filter { exception ->
      exception.scheduleId in selectedIds
    }
    val projection = ScheduleCalendarProjectionFactory.project(
      source = ScheduleCalendarSource(selectedSchedules, selectedExceptions),
      scope = exportScope,
    )
    ensureAuthorized()
    val managedEvents = gateway.queryManagedEvents(
      scope = exportScope,
      scheduleIds = scheduleIds,
      ensureAuthorized = ensureAuthorized,
    )
    val plan = CalendarExportPlanner.plan(projection, managedEvents, exportScope)
    return applyPlan(plan)
  }

  /**
   * 顺序执行纯 planner 给出的 Provider 操作。
   *
   * 单项失败不会阻止其他独立日程继续导出，但会记录为本轮部分失败；每个实际读写前后的授权复核由 gateway 与
   * [ensureAuthorized] 共同保证。
   */
  private fun applyPlan(plan: CalendarExportPlan): ExportStats {
    var created = 0
    var updated = 0
    var deleted = 0
    var unchanged = 0
    var unsupported = 0
    val failures = mutableListOf<ActionFailure>()
    for (action in plan.actions) {
      ensureAuthorized()
      try {
        when (action) {
          is CalendarExportAction.Create -> {
            checkNotNull(gateway.createEvent(action.projection, plan.scope, ensureAuthorized)) {
              "Calendar Provider did not create event"
            }
            created++
          }
          is CalendarExportAction.Update -> {
            check(gateway.updateEvent(action.projection, action.existingEventRef, plan.scope, ensureAuthorized)) {
              "Calendar Provider event changed before update"
            }
            updated++
          }
          is CalendarExportAction.Delete -> {
            check(gateway.deleteEvent(action.event, plan.scope, ensureAuthorized)) {
              "Calendar Provider event changed before delete"
            }
            deleted++
          }
          is CalendarExportAction.NoOp -> unchanged++
          is CalendarExportAction.Unsupported -> unsupported++
        }
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (failure: Throwable) {
        failures += ActionFailure(action::class.simpleName.orEmpty(), failure)
      }
    }
    return ExportStats(created, updated, deleted, unchanged, unsupported, failures)
  }

  /** 单向导出对设置页公开的最小运行状态。 */
  sealed interface ExportStatus {
    data object Idle : ExportStatus
    data object Running : ExportStatus
    data class Completed(val stats: ExportStats) : ExportStatus
    data class PartiallyFailed(val stats: ExportStats) : ExportStatus
    data class Failed(val cause: Throwable) : ExportStatus
    data class CorruptedSnapshot(val cause: Throwable) : ExportStatus
  }

  /** 一轮 Provider 计划的稳定计数与失败摘要。 */
  data class ExportStats(
    val created: Int,
    val updated: Int,
    val deleted: Int,
    val unchanged: Int,
    val unsupported: Int,
    val failures: List<ActionFailure>,
  )

  /** 单项失败只保留动作类型和异常，不复制完整 Schedule payload。 */
  data class ActionFailure(val actionType: String, val cause: Throwable)

  /** 本地快照已标记损坏，禁止继续形成 Provider 计划。 */
  private class CorruptedSnapshotException(cause: Throwable) : IllegalStateException(
    "Schedule snapshot is corrupted; calendar export is blocked",
    cause,
  )
}

/**
 * 协调器消费的最小系统日历写入端口。
 *
 * 该端口只为单向导出隔离 Android Provider 边界和 host 单测；它不承载入站读取、冲突选择或持久化状态。
 */
internal interface ScheduleCalendarExportEventGateway {
  /** 查询当前 scope 中已通过 Provider 身份校验的受管事件。 */
  fun queryManagedEvents(
    scope: CalendarExportScope,
    scheduleIds: Set<ScheduleId>?,
    ensureAuthorized: () -> Unit,
  ): List<ManagedCalendarEvent>

  /** 重建完整身份可信的受管日历；成功后调用方必须执行全量回写。 */
  fun recreateManagedCalendarForRecovery(ensureAuthorized: () -> Unit): Boolean

  /** 创建目标投影，返回正数 Provider event id；失败或无结果返回 `null`。 */
  fun createEvent(
    projection: CalendarEventProjection,
    scope: CalendarExportScope,
    ensureAuthorized: () -> Unit,
  ): Long?

  /** 用目标投影覆盖同一受管事件。 */
  fun updateEvent(
    projection: CalendarEventProjection,
    eventRef: PlatformCalendarEventRef,
    scope: CalendarExportScope,
    ensureAuthorized: () -> Unit,
  ): Boolean

  /** 删除已验证属于当前 scope 的受管事件。 */
  fun deleteEvent(
    event: ManagedCalendarEvent,
    scope: CalendarExportScope,
    ensureAuthorized: () -> Unit,
  ): Boolean
}

/** 把既有 Android gateway 收窄为协调器所需的四个单向操作。 */
private class AndroidScheduleCalendarExportEventGateway(
  private val delegate: AndroidScheduleCalendarGateway,
) : ScheduleCalendarExportEventGateway {
  override fun queryManagedEvents(
    scope: CalendarExportScope,
    scheduleIds: Set<ScheduleId>?,
    ensureAuthorized: () -> Unit,
  ): List<ManagedCalendarEvent> = delegate.queryManagedEvents(scope, scheduleIds, ensureAuthorized)

  override fun recreateManagedCalendarForRecovery(
    ensureAuthorized: () -> Unit,
  ): Boolean = delegate.recreateManagedCalendarForRecovery(ensureAuthorized)

  override fun createEvent(
    projection: CalendarEventProjection,
    scope: CalendarExportScope,
    ensureAuthorized: () -> Unit,
  ): Long? = delegate.createEvent(projection, scope, ensureAuthorized)

  override fun updateEvent(
    projection: CalendarEventProjection,
    eventRef: PlatformCalendarEventRef,
    scope: CalendarExportScope,
    ensureAuthorized: () -> Unit,
  ): Boolean = delegate.updateEvent(projection, eventRef, scope, ensureAuthorized)

  override fun deleteEvent(
    event: ManagedCalendarEvent,
    scope: CalendarExportScope,
    ensureAuthorized: () -> Unit,
  ): Boolean = delegate.deleteEvent(event, scope, ensureAuthorized)
}

/**
 * 进程内账号导出器注册表。
 *
 * 同一 scope 始终只有一个 worker；新账号代次启动时先停止旧 worker。注册表只管理协程生命周期和 UI 状态，
 * 不保存业务数据或 Provider 内容。
 */
internal object ScheduleCalendarExportCoordinatorProvider {
  private val lock = Any()
  private val coordinators = mutableMapOf<CalendarExportScope, ScheduleCalendarExportCoordinator>()
  private val statuses = mutableMapOf<CalendarExportScope, MutableStateFlow<ScheduleCalendarExportCoordinator.ExportStatus>>()

  /** 替换并启动当前 scope 的唯一协调器。 */
  fun replace(scope: CalendarExportScope, coordinator: ScheduleCalendarExportCoordinator) {
    synchronized(lock) {
      // worker 的替换与启停必须属于同一个临界区，否则并发 enable/resume 可能在新 worker 启动后再次启动旧 worker。
      coordinators.put(scope, coordinator)?.stop()
      coordinator.start()
    }
  }

  /** 停止并移除 scope worker；已导出的 Provider 事件保持不变。 */
  fun stop(scope: CalendarExportScope) {
    synchronized(lock) {
      // 与 replace 串行，确保关闭返回后不会有先前已进入替换流程的 worker 在锁外重新启动。
      coordinators.remove(scope)?.stop()
      statuses.getOrPut(scope) {
        MutableStateFlow(ScheduleCalendarExportCoordinator.ExportStatus.Idle)
      }.value = ScheduleCalendarExportCoordinator.ExportStatus.Idle
    }
  }

  /** 返回设置页可长期订阅的状态流。 */
  fun status(scope: CalendarExportScope): Flow<ScheduleCalendarExportCoordinator.ExportStatus> =
    synchronized(lock) {
      statuses.getOrPut(scope) {
        MutableStateFlow(ScheduleCalendarExportCoordinator.ExportStatus.Idle)
      }.asStateFlow()
    }

  /** 由当前协调器发布最新一轮状态。 */
  internal fun publishStatus(
    scope: CalendarExportScope,
    status: ScheduleCalendarExportCoordinator.ExportStatus,
  ) {
    synchronized(lock) {
      statuses.getOrPut(scope) {
        MutableStateFlow(ScheduleCalendarExportCoordinator.ExportStatus.Idle)
      }.value = status
    }
  }
}
