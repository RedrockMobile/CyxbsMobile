package com.cyxbs.pages.schedule.data.repository

import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.network.ApiException
import com.cyxbs.pages.schedule.data.local.SplitSettingsScheduleLocalDataSource
import com.cyxbs.pages.schedule.data.local.ScheduleLocalDataSource
import com.cyxbs.pages.schedule.data.local.ScheduleSettingsKeys
import com.cyxbs.pages.schedule.data.model.ScheduleEntity
import com.cyxbs.pages.schedule.data.model.ScheduleLocalMeta
import com.cyxbs.pages.schedule.data.model.SchedulePendingOperation
import com.cyxbs.pages.schedule.data.model.LegacyRecurrenceMigration
import com.cyxbs.pages.schedule.data.model.ScheduleMutations
import com.cyxbs.pages.schedule.data.model.ScheduleOccurrences
import com.cyxbs.pages.schedule.data.model.ScheduleRemindMode
import com.cyxbs.pages.schedule.data.remote.IScheduleRemoteDataSource
import com.cyxbs.pages.schedule.data.remote.ScheduleRemoteDataSource
import com.cyxbs.pages.schedule.recurrence.Recurrence
import com.cyxbs.pages.schedule.recurrence.RecurrenceOverride
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/**
 * todo 同步仓库，common 层核心数据层入口。
 *
 * 职责：
 * - 持有 [todos] 和 [syncState] 供 UI 订阅。
 * - 启动时读取本地快照立即展示，后台根据 sync_time 决定 `/list` 或 `/todos`。
 * - pending operations 在重启后继续 flush。
 * - 提供 create/update/delete/pin/complete 方法，离线时记录 pending，有网时自动同步。
 * - 使用 [syncMutex] 串行化同步与本地变更，避免并发冲突。
 * - 本地变更触发防抖同步，5 秒内多次修改只触发一次 [sync]。
 */
class ScheduleSyncRepository(
  private val localDataSource: ScheduleLocalDataSource = SplitSettingsScheduleLocalDataSource(),
  private val remoteDataSource: IScheduleRemoteDataSource = ScheduleRemoteDataSource,
) {

  private val syncMutex = Mutex()

  private val _todos = MutableStateFlow<List<ScheduleEntity>>(emptyList())
  val todos: StateFlow<List<ScheduleEntity>> = _todos.asStateFlow()

  private val _syncState = MutableStateFlow<ScheduleSyncState>(ScheduleSyncState.Idle)
  val syncState: StateFlow<ScheduleSyncState> = _syncState.asStateFlow()

  private val _categories = MutableStateFlow<List<String>>(emptyList())
  /** 分组候选池（含默认与自定义），UI 订阅用。 */
  val categories: StateFlow<List<String>> = _categories.asStateFlow()

  private var nextLocalId: Long = Clock.System.now().toEpochMilliseconds()

  /** 防抖同步任务，5 秒内多次修改只触发一次同步。 */
  private var debounceSyncJob: Job? = null
  private val syncDebounceMillis = 5000L

  /**
   * 初始化仓库。
   *
   * 读取本地快照并立即更新 [todos]，然后后台触发一次同步。
   * 该方法应在 App 启动或用户登录后调用一次。
   */
  suspend fun initialize() {
    syncMutex.withLock {
      val account = getCurrentAccount() ?: return
      val snapshot = localDataSource.loadSnapshot(account)
      nextLocalId = maxOf(nextLocalId, snapshot.meta.nextLocalId)
      _todos.value = LegacyRecurrenceMigration.migrate(snapshot.todos).sortedWith(todoComparator())
      loadCategoriesLocked(account)
    }

    // 后台触发同步
    sync()
  }

  /** 读取候选池；从未存过则用默认三类初始化并落盘。调用方需已持有 syncMutex。 */
  private suspend fun loadCategoriesLocked(account: String) {
    val stored = localDataSource.getCategories(account)
    _categories.value = stored ?: listOf(
      ScheduleEntity.TYPE_STUDY, ScheduleEntity.TYPE_LIFE, ScheduleEntity.TYPE_OTHER,
    ).also { localDataSource.saveCategories(account, it) }
  }

  /** 详情页等未走 initialize 的入口用：确保候选池已加载（已加载则跳过）。 */
  suspend fun ensureCategoriesLoaded() {
    if (_categories.value.isNotEmpty()) return
    syncMutex.withLock {
      if (_categories.value.isNotEmpty()) return
      val account = getCurrentAccount() ?: return
      loadCategoriesLocked(account)
    }
  }

  /** 确保 _todos 已从本地加载（不触发同步），详情页计算分类使用情况用。 */
  suspend fun ensureSchedulesLoaded() {
    if (_todos.value.isNotEmpty()) return
    syncMutex.withLock {
      if (_todos.value.isNotEmpty()) return
      val account = getCurrentAccount() ?: return
      val snapshot = localDataSource.loadSnapshot(account)
      _todos.value = LegacyRecurrenceMigration.migrate(snapshot.todos).sortedWith(todoComparator())
    }
  }

  /** 新增一个自定义分类到候选池（已存在则忽略）。 */
  suspend fun addCategory(name: String) {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return
    syncMutex.withLock {
      val account = getCurrentAccount() ?: return
      val current = _categories.value
      if (trimmed in current) return
      val updated = current + trimmed
      localDataSource.saveCategories(account, updated)
      _categories.value = updated
    }
  }

  /** 从候选池移除一个分类（UI 只对「未被任何 todo 使用」的项开放删除）。 */
  suspend fun removeCategory(name: String) {
    syncMutex.withLock {
      val account = getCurrentAccount() ?: return
      val current = _categories.value
      if (name !in current) return
      val updated = current - name
      localDataSource.saveCategories(account, updated)
      _categories.value = updated
    }
  }

  /**
   * 主动触发一次同步。
   *
   * **同步流程：**
   * ```
   * 1. tryLock() 互斥锁检查，已有同步进行中则直接返回
   * 2. 读取本地快照判断同步策略：
   *    - needsFullRebuild 或 syncTime == 0 → fullRebuild()
   *    - 否则 → incrementalSync()
   * 3. flushPending() 上传待同步操作
   * 4. 更新 syncState（Success 或 Error）
   * 5. unlock() 释放互斥锁
   * ```
   *
   * **调用时机：**
   * - [initialize] 启动时主动调用
   * - 本地修改后通过 [scheduleDebouncedSync] 防抖触发（5 秒延迟）
   * - UI 主动下拉刷新时调用
   *
   * 如果当前正在同步，直接返回不重复触发。
   */
  suspend fun sync() {
    if (!syncMutex.tryLock()) {
      // 已有同步在进行，跳过
      return
    }
    try {
      val account = getCurrentAccount() ?: return
      _syncState.value = ScheduleSyncState.Syncing("checking")

      val snapshot = localDataSource.loadSnapshot(account)
      val meta = snapshot.meta

      // 如果需要全量重建
      if (meta.needsFullRebuild || meta.syncTime == 0L) {
        fullRebuild(account)
      } else {
        incrementalSync(account, meta)
      }

      // flush pending
      flushPending(account)

      _syncState.value = ScheduleSyncState.Success(Clock.System.now().toEpochMilliseconds())
    } catch (e: Exception) {
      _syncState.value = ScheduleSyncState.Error(
        message = e.message ?: "同步失败",
        errorType = e::class.simpleName,
      )
    } finally {
      syncMutex.unlock()
    }
  }

  /**
   * 新增 todo。
   *
   * 本地立即写入并更新 [todos]，记录 pending upsert，通过防抖触发后台同步。
   */
  suspend fun createSchedule(
    title: String,
    detail: String = "",
    type: String = ScheduleEntity.TYPE_OTHER,
    startTime: String? = null,
    endTime: String? = null,
    remindMode: ScheduleRemindMode = ScheduleRemindMode(),
  ) {
    syncMutex.withLock {
      val account = getCurrentAccount() ?: return
      val now = Clock.System.now().toEpochMilliseconds()
      val todo = ScheduleEntity(
        todoId = generateLocalId(),
        title = title,
        detail = detail,
        type = type,
        startTime = startTime,
        endTime = endTime ?: "",
        remindMode = remindMode,
        isDone = 0,
        isPinned = 0,
        isOvered = 0,
        lastModifyTime = now,
      )

      localDataSource.upsertLocal(account, todo, recordPending = true)
      reloadSchedules(account)
    }

    // 防抖触发同步
    scheduleDebouncedSync()
  }

  /**
   * 更新 todo。
   *
   * 本地立即写入并更新 [todos]，记录 pending upsert，通过防抖触发后台同步。
   */
  suspend fun updateSchedule(todo: ScheduleEntity) {
    syncMutex.withLock {
      val account = getCurrentAccount() ?: return
      val updated = todo.copy(lastModifyTime = Clock.System.now().toEpochMilliseconds())
      localDataSource.upsertLocal(account, updated, recordPending = true)
      reloadSchedules(account)
    }

    scheduleDebouncedSync()
  }

  /**
   * 删除 todo。
   *
   * 本地立即删除并更新 [todos]，记录 pending delete，通过防抖触发后台同步。
   */
  suspend fun deleteSchedule(todoId: Long) {
    syncMutex.withLock {
      val account = getCurrentAccount() ?: return
      localDataSource.deleteLocal(account, todoId, recordPending = true)
      reloadSchedules(account)
    }

    scheduleDebouncedSync()
  }

  /**
   * 置顶 / 取消置顶。
   *
   * 修改 isPinned 后作为完整 todo upsert 上传，不走后端 `/pin`。
   */
  suspend fun pinSchedule(todoId: Long, isPinned: Boolean) {
    syncMutex.withLock {
      val account = getCurrentAccount() ?: return
      val todo = localDataSource.getById(account, todoId) ?: return
      val updated = todo.copy(
        isPinned = if (isPinned) 1 else 0,
        lastModifyTime = Clock.System.now().toEpochMilliseconds(),
      )
      localDataSource.upsertLocal(account, updated, recordPending = true)
      reloadSchedules(account)
    }

    scheduleDebouncedSync()
  }

  /**
   * 完成日程（RFC5545 语义）。
   *
   * - 单次（无重复）：直接删除。
   * - 重复：把对应的那一次加入 EXDATE（不影响系列其余）。
   *
   * @param occurrenceDate 被完成的那一次的「原始锚点日期」(occurrence.recurrenceId)；
   *   重复型必须由 UI 传入；为 null 时回退为最近一次 occurrence。
   */
  suspend fun completeSchedule(todoId: Long, occurrenceDate: Date? = null) {
    syncMutex.withLock {
      val account = getCurrentAccount() ?: return
      val todo = localDataSource.getById(account, todoId)?.let(LegacyRecurrenceMigration::migrate) ?: return
      if (!ScheduleMutations.isRecurring(todo)) {
        localDataSource.deleteLocal(account, todoId, recordPending = true)
      } else {
        val target = occurrenceDate ?: firstUpcomingOccurrence(todo)
        if (target == null) {
          localDataSource.deleteLocal(account, todoId, recordPending = true)
        } else {
          val updated = ScheduleMutations.addExdate(todo, target)
            .copy(lastModifyTime = Clock.System.now().toEpochMilliseconds())
          localDataSource.upsertLocal(account, updated, recordPending = true)
        }
      }
      reloadSchedules(account)
    }
    scheduleDebouncedSync()
  }

  /**
   * 删除重复系列里的某一次（EXDATE），保留其余。
   *
   * @param occurrenceDate 该次的原始锚点日期 (occurrence.recurrenceId)。
   */
  suspend fun deleteThisOccurrence(todoId: Long, occurrenceDate: Date) {
    syncMutex.withLock {
      val account = getCurrentAccount() ?: return
      val todo = localDataSource.getById(account, todoId)?.let(LegacyRecurrenceMigration::migrate) ?: return
      if (todo.recurrence == null) return
      val updated = ScheduleMutations.addExdate(todo, occurrenceDate)
        .copy(lastModifyTime = Clock.System.now().toEpochMilliseconds())
      localDataSource.upsertLocal(account, updated, recordPending = true)
      reloadSchedules(account)
    }
    scheduleDebouncedSync()
  }

  /**
   * 仅修改重复系列里的某一次（RECURRENCE-ID override）。
   *
   * @param occurrenceDate 该次的原始锚点日期；[patch].recurrenceId 应与之一致。
   */
  suspend fun editThisOccurrence(todoId: Long, occurrenceDate: Date, patch: RecurrenceOverride) {
    syncMutex.withLock {
      val account = getCurrentAccount() ?: return
      val todo = localDataSource.getById(account, todoId)?.let(LegacyRecurrenceMigration::migrate) ?: return
      if (todo.recurrence == null) return
      val updated = ScheduleMutations.applyOverride(todo, patch)
        .copy(lastModifyTime = Clock.System.now().toEpochMilliseconds())
      localDataSource.upsertLocal(account, updated, recordPending = true)
      reloadSchedules(account)
    }
    scheduleDebouncedSync()
  }

  /**
   * 「此次及后续」：原系列 UNTIL 截断到 [occurrenceDate] 前一天，并以 [newTodo] 新建一条从该次起的新系列。
   *
   * count→until 的精确换算由调用方在构造 [newTodo] 的规则时负责（这里仅做截断 + 新建）。
   */
  suspend fun editThisAndFollowing(todoId: Long, occurrenceDate: Date, newTodo: ScheduleEntity) {
    syncMutex.withLock {
      val account = getCurrentAccount() ?: return
      val todo = localDataSource.getById(account, todoId)?.let(LegacyRecurrenceMigration::migrate) ?: return
      if (todo.recurrence == null) return
      val truncated = ScheduleMutations.truncateBefore(todo, occurrenceDate)
        .copy(lastModifyTime = Clock.System.now().toEpochMilliseconds())
      localDataSource.upsertLocal(account, truncated, recordPending = true)
      val created = newTodo.copy(
        todoId = generateLocalId(),
        lastModifyTime = Clock.System.now().toEpochMilliseconds(),
      )
      localDataSource.upsertLocal(account, created, recordPending = true)
      reloadSchedules(account)
    }
    scheduleDebouncedSync()
  }

  /** 取该日程从今天起最近一次 occurrence 的原始锚点日期，用于无指定时的完成回退。 */
  private fun firstUpcomingOccurrence(todo: ScheduleEntity): Date? {
    val today = Date.now()
    return ScheduleOccurrences.expandInRange(todo, today, today.plusYears(1)).firstOrNull()?.recurrenceId
  }

  /**
   * 清空当前账号所有本地数据。
   *
   * 通常在退出登录时调用。
   */
  suspend fun clearAccount() {
    syncMutex.withLock {
      val account = getCurrentAccount() ?: return
      localDataSource.clearAccount(account)
      _todos.value = emptyList()
      _categories.value = emptyList()
    }
  }

  /**
   * 按 todoId 查询单个 todo。
   *
   * 供详情页使用：先加载本地快照返回最新状态，若无则返回 null。
   */
  suspend fun getScheduleById(todoId: Long): ScheduleEntity? {
    val account = getCurrentAccount() ?: return null
    return localDataSource.getById(account, todoId)?.let(LegacyRecurrenceMigration::migrate)
  }

  /**
   * 判断当前账号是否已完成首次使用引导（已插入过引导 todo）。
   */
  suspend fun isFirstUse(): Boolean {
    val account = getCurrentAccount() ?: return false
    val settings = com.cyxbs.components.config.sp.AccountSettings.get(account)
    return !settings.getBoolean(ScheduleSettingsKeys.SP_SCHEDULE_CMP_FIRST_USE_DONE, false)
  }

  /**
   * 标记首次使用引导已完成（不再插入引导 todo）。
   */
  suspend fun markFirstUseDone() {
    val account = getCurrentAccount() ?: return
    val settings = com.cyxbs.components.config.sp.AccountSettings.get(account)
    settings.putBoolean(ScheduleSettingsKeys.SP_SCHEDULE_CMP_FIRST_USE_DONE, true)
  }

  // --- 私有辅助方法 ---

  /**
   * 调度防抖同步任务。
   *
   * 取消之前的防抖任务，启动新的 5 秒延迟任务。
   * 多次调用会重置计时器，最终只触发一次 [sync]。
   */
  private fun scheduleDebouncedSync() {
    debounceSyncJob?.cancel()
    debounceSyncJob = appCoroutineScope.launch {
      delay(syncDebounceMillis.milliseconds)
      sync()
    }
  }

  /**
   * 全量重建本地缓存。
   *
   * 调用 GET /list 获取完整 todo 列表和最新 sync_time，
   * 全量替换本地数据，保留 pending operations。
   *
   * @throws ScheduleNetworkException 网络连接失败
   * @throws ScheduleDataException 数据异常
   */
  private suspend fun fullRebuild(account: String) {
    _syncState.value = ScheduleSyncState.Syncing("full rebuild")

    val result = remoteDataSource.getAllSchedules()
    if (result.isFailure) {
      val cause = result.exceptionOrNull()
      throw when {
        isNetworkException(cause) -> ScheduleNetworkException("全量拉取网络失败", cause)
        else -> ScheduleDataException("全量拉取失败", cause)
      }
    }

    val response = result.getOrThrow()
    localDataSource.replaceAll(
      account = account,
      todos = response.changedScheduleArray,
      syncTime = response.syncTime,
      preservePending = true,
    )

    reloadSchedules(account)
  }

  /**
   * 增量同步本地缓存。
   *
   * 流程：
   * 1. 调用 GET /sync-time?sync_time=xxx 检查 sync_time 是否存在
   *    - 不存在 → 回退 fullRebuild()
   *    - 相同 → 无需拉取，跳过
   * 2. 调用 GET /todos?sync_time=xxx 获取增量变化
   * 3. 合并到本地：changed 覆盖、del 删除
   * 4. replaceAll() 写入新快照，保留 pending operations
   *
   * @throws ScheduleNetworkException 网络连接失败
   * @throws ScheduleDataException 数据异常，会自动回退 fullRebuild
   */
  private suspend fun incrementalSync(account: String, meta: ScheduleLocalMeta) {
    _syncState.value = ScheduleSyncState.Syncing("checking sync_time")

    val syncTimeResult = remoteDataSource.getSyncTime(meta.syncTime)
    if (syncTimeResult.isFailure) {
      val cause = syncTimeResult.exceptionOrNull()
      when {
        isNetworkException(cause) -> throw ScheduleNetworkException("检查 sync_time 网络失败", cause)
        else -> {
          // 数据异常，尝试回退全量重建
          try {
            fullRebuild(account)
            return
          } catch (e: ScheduleNetworkException) {
            // fullRebuild 也是网络失败，继续抛出
            throw e
          } catch (e: Exception) {
            // fullRebuild 也失败，抛出数据异常
            throw ScheduleDataException("增量和全量同步均失败", e)
          }
        }
      }
    }

    val syncTimeResponse = syncTimeResult.getOrThrow()
    if (!syncTimeResponse.isSyncTimeExist) {
      // sync_time 不存在，回退全量重建
      fullRebuild(account)
      return
    }

    // sync_time 相同，无需拉取
    if (syncTimeResponse.syncTime == meta.syncTime) {
      return
    }

    // 增量拉取
    _syncState.value = ScheduleSyncState.Syncing("pulling")
    val pullResult = remoteDataSource.pullSchedules(meta.syncTime)
    if (pullResult.isFailure) {
      val cause = pullResult.exceptionOrNull()
      when {
        isNetworkException(cause) -> throw ScheduleNetworkException("增量拉取网络失败", cause)
        else -> {
          // 数据异常，尝试回退全量重建
          try {
            fullRebuild(account)
            return
          } catch (e: ScheduleNetworkException) {
            throw e
          } catch (e: Exception) {
            throw ScheduleDataException("增量拉取和全量重建均失败", e)
          }
        }
      }
    }

    val deltaResponse = pullResult.getOrThrow()

    // 合并远端变化到本地
    val currentSchedules = localDataSource.getAll(account).associateBy { it.todoId }.toMutableMap()

    deltaResponse.changedScheduleArray.forEach { todo ->
      currentSchedules[todo.todoId] = todo
    }

    deltaResponse.delScheduleArray.forEach { todoId ->
      currentSchedules.remove(todoId)
    }

    localDataSource.replaceAll(
      account = account,
      todos = currentSchedules.values.toList(),
      syncTime = deltaResponse.syncTime,
      preservePending = true,
    )

    reloadSchedules(account)
  }

  /**
   * 上传待同步操作到服务器。
   *
   * 流程：
   * 1. 读取 pending operations，为空则跳过
   * 2. 分离 upsert 和 delete 两类操作
   * 3. 先上传 upserts：
   *    - 调用 POST /batch-create
   *    - 成功 → removePendingOperations() + 更新 meta.syncTime
   *    - 失败 → markNeedsFullRebuild() + 抛异常
   * 4. 再上传 deletes：
   *    - 调用 DELETE /todos
   *    - 成功 → removePendingOperations() + 更新 meta.syncTime
   *    - 失败 → markNeedsFullRebuild() + 抛异常
   *
   * @throws ScheduleNetworkException 网络连接失败
   * @throws ScheduleSyncConflictException sync_time 冲突
   */
  private suspend fun flushPending(account: String) {
    val snapshot = localDataSource.loadSnapshot(account)
    val pending = snapshot.pendingOperations

    if (pending.isEmpty()) return

    val meta = snapshot.meta

    // 分离 upsert 和 delete
    val upserts = pending.filter { it.kind == SchedulePendingOperation.Kind.UPSERT }
    val deletes = pending.filter { it.kind == SchedulePendingOperation.Kind.DELETE }

    // 先上传 upserts
    if (upserts.isNotEmpty()) {
      _syncState.value = ScheduleSyncState.Syncing("pushing upserts")
      val todos = upserts.mapNotNull { it.todo }
      val pushResult = remoteDataSource.pushSchedules(todos, meta.syncTime)
      if (pushResult.isFailure) {
        val cause = pushResult.exceptionOrNull()
        // 标记需要重建
        localDataSource.markNeedsFullRebuild(account)
        throw when {
          isNetworkException(cause) -> ScheduleNetworkException("上传 upsert 网络失败", cause)
          cause is ApiException -> ScheduleSyncConflictException("上传 upsert 冲突", cause)
          else -> ScheduleSyncConflictException("上传 upsert 失败", cause)
        }
      }

      val pushResponse = pushResult.getOrThrow()
      localDataSource.removePendingOperations(account, upserts.map { it.todoId }.toSet())

      // 更新 meta syncTime
      val newMeta = meta.copy(
        syncTime = pushResponse.syncTime,
        lastSuccessfulSyncTime = Clock.System.now().toEpochMilliseconds(),
      )
      localDataSource.replaceAll(
        account = account,
        todos = localDataSource.getAll(account),
        syncTime = newMeta.syncTime,
        preservePending = true,
      )
    }

    // 再上传 deletes
    if (deletes.isNotEmpty()) {
      _syncState.value = ScheduleSyncState.Syncing("pushing deletes")
      val updatedMeta = localDataSource.loadSnapshot(account).meta
      val deleteResult = remoteDataSource.deleteSchedules(deletes.map { it.todoId }, updatedMeta.syncTime)
      if (deleteResult.isFailure) {
        val cause = deleteResult.exceptionOrNull()
        localDataSource.markNeedsFullRebuild(account)
        throw when {
          isNetworkException(cause) -> ScheduleNetworkException("上传 delete 网络失败", cause)
          cause is ApiException -> ScheduleSyncConflictException("上传 delete 冲突", cause)
          else -> ScheduleSyncConflictException("上传 delete 失败", cause)
        }
      }

      val deleteResponse = deleteResult.getOrThrow()
      localDataSource.removePendingOperations(account, deletes.map { it.todoId }.toSet())

      // 更新 meta syncTime
      val newMeta = updatedMeta.copy(
        syncTime = deleteResponse.syncTime,
        lastSuccessfulSyncTime = Clock.System.now().toEpochMilliseconds(),
      )
      localDataSource.replaceAll(
        account = account,
        todos = localDataSource.getAll(account),
        syncTime = newMeta.syncTime,
        preservePending = true,
      )
    }
  }

  private suspend fun reloadSchedules(account: String) {
    val todos = LegacyRecurrenceMigration.migrate(localDataSource.getAll(account))
    _todos.value = todos.sortedWith(todoComparator())
  }

  private fun generateLocalId(): Long {
    return nextLocalId++
  }

  private fun getCurrentAccount(): String? {
    return IAccountService::class.impl<IAccountService>().stuNum
  }

  private fun todoComparator(): Comparator<ScheduleEntity> {
    return compareByDescending<ScheduleEntity> { it.isPinned }
      .thenBy { it.isDone }
      .thenByDescending { it.lastModifyTime }
  }

  /**
   * 判断异常是否为网络连接异常。
   *
   * 网络异常包括超时、连接失败等底层网络问题，可以重试。
   * 非网络异常（如 ApiException、序列化失败）通常是数据或逻辑问题，需要其他处理。
   */
  private fun isNetworkException(throwable: Throwable?): Boolean {
    return when (throwable) {
      is ConnectTimeoutException,
      is SocketTimeoutException,
      is HttpRequestTimeoutException -> true
      else -> false
    }
  }
}
