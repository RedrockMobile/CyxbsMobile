package com.cyxbs.pages.schedule.ui.service

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.pages.schedule.api.IScheduleOccurrenceService
import com.cyxbs.pages.schedule.api.ScheduleExternalCreateRequest
import com.cyxbs.pages.schedule.api.ScheduleExternalCreateResult
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceKind
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceTiming
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceView
import com.cyxbs.pages.schedule.data.repository.ScheduleRepositoryProvider
import com.cyxbs.pages.schedule.domain.model.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleKind
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrence
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.ui.edit.EditScheduleDialog
import com.cyxbs.pages.schedule.ui.edit.applyScheduleCompletion
import com.cyxbs.pages.schedule.ui.edit.applyScheduleDelete
import com.cyxbs.pages.schedule.ui.edit.applyScheduleEdit
import com.cyxbs.pages.schedule.ui.category.decodeScheduleCategoryColor
import com.cyxbs.pages.schedule.ui.category.toOccurrenceColor
import com.cyxbs.pages.schedule.ui.model.ScheduleUiOccurrence
import com.cyxbs.pages.schedule.ui.model.occurrencesInRange
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** API occurrence 对应的 Schedule 领域详情，只在服务实现文件内用于点击与创建后的弹窗切换。 */
private data class Detail(
  val occurrence: ScheduleUiOccurrence,
  val schedule: Schedule,
)

/**
 * Schedule 只读服务实现。
 *
 * 该实现负责仓库初始化、周期展开和关联过滤，不创建任何 Course PageDecoration 或 CourseItem。
 */
@ImplProvider(clazz = IScheduleOccurrenceService::class)
object ScheduleOccurrenceServiceImpl : IScheduleOccurrenceService {
  private val repository
    get() = ScheduleRepositoryProvider.repository

  /** 外部业务创建统一经过该服务，避免直接触碰仓库命令和分类目录。 */
  private val externalCreateService by lazy {
    ScheduleExternalCreateService(repository, ScheduleRepositoryProvider.clock)
  }

  /** 多个课表页切换观察窗口时只启动一次仓库初始化；失败时允许后续观察重新尝试。 */
  private val initializationStarted = atomic(false)

  /**
   * 已向调用方发布的 occurrence 对应详情。
   *
   * key 与 API model 的 identity 一致；详情只供随后点击使用，不承担持久化或同步状态。
   */
  private val detailByIdentity = MutableStateFlow<Map<String, Detail>>(emptyMap())

  /** 将通知中心、活动中心等外部来源保存为原生日程。 */
  override suspend fun createExternalSchedule(
    request: ScheduleExternalCreateRequest,
  ): ScheduleExternalCreateResult = externalCreateService.create(request)

  override fun observeLinkedOccurrencesInRange(
    startInclusive: com.cyxbs.components.config.time.MinuteTimeDate,
    endExclusive: com.cyxbs.components.config.time.MinuteTimeDate,
  ): Flow<List<ScheduleOccurrenceView>> = repository.snapshot.map { snapshot ->
    val schedulesById = snapshot.schedules.associateBy(Schedule::id)
    val categoriesById = snapshot.categories.associateBy { it.id }
    val details = linkedMapOf<String, Detail>()
    val result = snapshot.occurrencesInRange(startInclusive, endExclusive)
      .mapNotNull { occurrence ->
        val schedule = schedulesById[occurrence.scheduleId] ?: return@mapNotNull null
        if (!schedule.isVisibleInCourse(occurrence.status)) return@mapNotNull null
        val identity = occurrenceIdentity(occurrence)
        details[identity] = Detail(occurrence, schedule)
        occurrence.toApiModel(
          identity = identity,
          schedule = schedule,
          // 分组属于清单语义；纯事务即使残留旧 categoryId，也不向课表暴露对应颜色。
          categoryColor = if (schedule.todoState == null) null else {
            schedule.categoryId?.let(categoriesById::get)?.color
              .let(::decodeScheduleCategoryColor)
              ?.toOccurrenceColor()
          },
        )
      }
    if (details.isNotEmpty()) {
      // 可能同时有多个课表框架观察不同周，采用增量合并避免一个窗口清掉另一个窗口的点击详情。
      detailByIdentity.update { current -> current + details }
    }
    result
  }.onStart {
    if (initializationStarted.compareAndSet(expect = false, update = true)) {
      try {
        repository.initialize()
      } catch (throwable: Throwable) {
        initializationStarted.value = false
        throw throwable
      }
    }
  }

  @Composable
  override fun ScheduleDetailContent(
    occurrence: ScheduleOccurrenceView,
    embeddedInHost: Boolean,
    onDismiss: () -> Unit,
    onEditModeChanged: (Boolean) -> Unit,
    onDismissRequestChanged: (((suspend () -> Boolean)?) -> Unit),
    onWindowOverlayContentChanged: (((@Composable () -> Unit)?) -> Unit),
  ) {
    val details by detailByIdentity.collectAsState()
    val detail = details[occurrence.identity] ?: return
    EditScheduleDialog(
      show = true,
      editSchedule = detail.schedule,
      editOccurrence = detail.occurrence.let {
        ScheduleOccurrence(
          it.scheduleId,
          it.recurrenceId,
          it.timing,
          it.title,
          it.description,
          it.categoryId,
          it.reminder,
          it.status,
          it.isAdjusted,
        )
      },
      recurrenceId = detail.occurrence.recurrenceId,
      scrimColor = Color.Transparent,
      embeddedInExternalHost = embeddedInHost,
      showCourseRelation = true,
      onEditModeChanged = onEditModeChanged,
      onDismissRequestChanged = onDismissRequestChanged,
      onWindowOverlayContentChanged = onWindowOverlayContentChanged,
      onDismiss = onDismiss,
      onConfirm = { state, editScope, newCategory ->
        // 保存后详情仍留在当前弹窗；使用应用级 scope，避免宿主页面切换时取消尚未落库的命令。
        appCoroutineScope.launch {
          repository.applyScheduleEdit(
            state,
            editScope,
            detail.occurrence.recurrenceId,
            ScheduleRepositoryProvider.idGenerators,
            ScheduleRepositoryProvider.clock,
            newCategory,
          )
        }
      },
      onDelete = { editScope ->
        // 删除同样必须独立于详情弹窗生命周期，否则关闭 Window 时可能静默丢失操作。
        appCoroutineScope.launch {
          repository.applyScheduleDelete(
            detail.schedule.id,
            editScope,
            detail.occurrence.recurrenceId,
            ScheduleRepositoryProvider.clock,
          )
        }
      },
      onToggleCompleted = { completed ->
        appCoroutineScope.launch {
          repository.applyScheduleCompletion(
            scheduleId = detail.schedule.id,
            recurrenceId = detail.occurrence.recurrenceId,
            completed = completed,
            clock = ScheduleRepositoryProvider.clock,
          )
          // 原生清单完成后会从课表投影消失；事务即使关联清单并完成也继续保留详情。
          if (detail.schedule.kind == ScheduleKind.TODO && completed) onDismiss()
        }
      },
    )
  }

  /**
   * 使用 Schedule 的统一编辑弹窗创建原生事务。
   *
   * 课表传入的时间段只作为表单初值；确认后才执行本地优先命令，并在命令完成后通知课表移除临时 Item。
   */
  @Composable
  override fun ScheduleCreateAffairContent(
    initialTiming: ScheduleOccurrenceTiming.Timed,
    embeddedInHost: Boolean,
    onDismiss: () -> Unit,
    onCreated: () -> Unit,
    onEditModeChanged: (Boolean) -> Unit,
    onDismissRequestChanged: (((suspend () -> Boolean)?) -> Unit),
    onWindowOverlayContentChanged: (((@Composable () -> Unit)?) -> Unit),
  ) {
    var createdOccurrence by remember(initialTiming) { mutableStateOf<ScheduleOccurrenceView?>(null) }
    createdOccurrence?.let { occurrence ->
      ScheduleDetailContent(
        occurrence = occurrence,
        embeddedInHost = embeddedInHost,
        onDismiss = onDismiss,
        onEditModeChanged = onEditModeChanged,
        onDismissRequestChanged = onDismissRequestChanged,
        onWindowOverlayContentChanged = onWindowOverlayContentChanged,
      )
      return
    }

    EditScheduleDialog(
      show = true,
      creationKind = ScheduleKind.AFFAIR,
      creationTiming = initialTiming.toDomainModel(),
      scrimColor = Color.Transparent,
      embeddedInExternalHost = embeddedInHost,
      showCourseRelation = true,
      // 创建成功后由本 Composable 把表单替换成详情，不能先让外层课表宿主收起。
      dismissAfterCreateSave = false,
      onEditModeChanged = onEditModeChanged,
      onDismissRequestChanged = onDismissRequestChanged,
      onWindowOverlayContentChanged = onWindowOverlayContentChanged,
      onDismiss = onDismiss,
      onConfirm = { state, editScope, newCategory ->
        // 创建命令由进程生命周期持有；成功后在同一个课表弹窗中原位切换到刚创建的事务详情。
        appCoroutineScope.launch {
          val created = repository.applyScheduleEdit(
            state,
            editScope,
            recurrenceId = null,
            idGenerators = ScheduleRepositoryProvider.idGenerators,
            clock = ScheduleRepositoryProvider.clock,
            newCategory = newCategory,
          ) ?: return@launch
          val detail = created.toInitialDetail()
          val identity = occurrenceIdentity(detail.occurrence)
          val apiOccurrence = detail.occurrence.toApiModel(identity, created, categoryColor = null)
          detailByIdentity.update { current -> current + (identity to detail) }
          createdOccurrence = apiOccurrence
          onCreated()
        }
      },
    )
  }
}

/**
 * 为刚创建的日程构造首个可查看实例，避免等待课表下一轮窗口订阅后才能把创建表单切换成详情。
 * 新建系列尚未发生整系列位移，其稳定 identity 直接由当前 timing 的日期、时分和时区构造。
 */
private fun Schedule.toInitialDetail(): Detail {
  val occurrence = ScheduleUiOccurrence(
    scheduleId = id,
    recurrenceId = recurrence?.let { timing.toInitialRecurrenceId(recurrenceAnchorDate) },
    title = title,
    description = description,
    categoryId = categoryId,
    timing = timing,
    reminder = reminder,
    status = OccurrenceStatus.ACTIVE,
    isAdjusted = false,
  )
  return Detail(occurrence, this)
}

/** 使用新建系列的原始墙上时间构造首个 occurrence identity；未排期重复已在领域校验中禁止。 */
private fun ScheduleTiming.toInitialRecurrenceId(anchorDate: Date?): RecurrenceId = when (this) {
  is ScheduleTiming.Timed -> RecurrenceId(
    MinuteTimeDate(anchorDate ?: start.date, start.time),
    timeZoneId,
    false,
  )
  is ScheduleTiming.Deadline -> RecurrenceId(
    MinuteTimeDate(anchorDate ?: due.date, due.time),
    timeZoneId,
    false,
  )
  is ScheduleTiming.AllDay -> RecurrenceId(
    MinuteTimeDate(anchorDate ?: date, 0, 0),
    null,
    true,
  )
  ScheduleTiming.Unscheduled -> error("unscheduled schedule cannot recur")
}

/**
 * 计算一次 occurrence 是否应投射到课表。
 *
 * 原生事务的课表身份不因后来关联清单并完成而丢失；原生清单完成后暂时隐藏，重新打开后自动恢复。
 */
internal fun Schedule.isVisibleInCourse(status: OccurrenceStatus): Boolean {
  if (!linkedToCourse || status == OccurrenceStatus.CANCELLED) return false
  return kind == ScheduleKind.AFFAIR || status == OccurrenceStatus.ACTIVE
}

/** 使用 Schedule/recurrence identity 定位 occurrence；移动实例不会因当前展示时间变化而换 key。 */
private fun occurrenceIdentity(occurrence: ScheduleUiOccurrence): String =
  buildString {
    append(occurrence.scheduleId.value)
    append('|')
    append(occurrence.recurrenceId?.toString().orEmpty())
  }

/** 将领域 occurrence 映射为不包含仓库实现和课表 UI 类型的 API 数据。 */
private fun ScheduleUiOccurrence.toApiModel(
  identity: String,
  schedule: Schedule,
  categoryColor: com.cyxbs.pages.schedule.api.ScheduleOccurrenceColor?,
): ScheduleOccurrenceView =
  ScheduleOccurrenceView(
    identity = identity,
    scheduleId = scheduleId,
    recurrenceId = recurrenceId,
    kind = when (schedule.kind) {
      ScheduleKind.TODO -> ScheduleOccurrenceKind.TODO
      ScheduleKind.AFFAIR -> ScheduleOccurrenceKind.AFFAIR
    },
    isInTodoList = schedule.todoState != null,
    title = title,
    description = description,
    categoryColor = categoryColor,
    timing = when (val value = timing) {
      is ScheduleTiming.Timed -> ScheduleOccurrenceTiming.Timed(
        start = value.start,
        durationMinutes = value.durationMinutes,
        timeZoneId = value.timeZoneId,
      )
      is ScheduleTiming.Deadline -> ScheduleOccurrenceTiming.Deadline(
        due = value.due,
        timeZoneId = value.timeZoneId,
      )
      is ScheduleTiming.AllDay -> ScheduleOccurrenceTiming.AllDay(
        date = value.date,
      )
      ScheduleTiming.Unscheduled -> ScheduleOccurrenceTiming.Unscheduled
    },
  )

/** API 时间模型只在服务边界转换，Course 模块无需依赖 Schedule 领域层。 */
private fun ScheduleOccurrenceTiming.Timed.toDomainModel(): ScheduleTiming.Timed =
  ScheduleTiming.Timed(
    start = start,
    durationMinutes = durationMinutes,
    timeZoneId = timeZoneId,
  )
