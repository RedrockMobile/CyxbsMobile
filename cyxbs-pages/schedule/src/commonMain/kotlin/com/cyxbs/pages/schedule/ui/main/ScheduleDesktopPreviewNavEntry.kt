package com.cyxbs.pages.schedule.ui.main

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.components.config.time.TodayNoEffect
import com.cyxbs.components.config.time.toMinuteTimeDate
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.pages.schedule.api.ScheduleMainNavArgument
import com.cyxbs.pages.schedule.api.ScheduleTodoNavArgument
import com.cyxbs.pages.schedule.domain.model.CategoryId
import com.cyxbs.pages.schedule.domain.model.FieldPatch
import com.cyxbs.pages.schedule.domain.model.IsoWeekDay
import com.cyxbs.pages.schedule.domain.model.OccurrencePatch
import com.cyxbs.pages.schedule.domain.model.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.model.RecurrenceEnd
import com.cyxbs.pages.schedule.domain.model.RecurrenceFrequency
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.RecurrenceRule
import com.cyxbs.pages.schedule.domain.model.ReminderChannel
import com.cyxbs.pages.schedule.domain.model.ReminderId
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleCategory
import com.cyxbs.pages.schedule.domain.model.ScheduleCompletion
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrenceException
import com.cyxbs.pages.schedule.domain.model.ScheduleReminder
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.repository.ScheduleCalendarChange
import com.cyxbs.pages.schedule.domain.repository.ScheduleCommand
import com.cyxbs.pages.schedule.domain.repository.ScheduleMutationBusinessRejectionReason
import com.cyxbs.pages.schedule.domain.repository.ScheduleRemoteError
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepository
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryStatus
import com.cyxbs.pages.schedule.domain.repository.ScheduleSnapshot
import com.cyxbs.pages.schedule.domain.repository.ScheduleSyncResult
import com.cyxbs.pages.schedule.ui.todo.ScheduleTodoPage
import com.cyxbs.pages.schedule.ui.todo.figma.ScheduleTodoDetailRoute
import com.cyxbs.pages.schedule.viewmodel.ScheduleMainViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

private const val NAV_SCHEDULE_DESKTOP_PREVIEW = "schedule/desktop-preview"
private const val NAV_SCHEDULE_TODO_DESKTOP_PREVIEW = "schedule/todo-preview"
private const val NAV_SCHEDULE_TODO_DETAIL_DESKTOP_PREVIEW = "schedule/todo-preview/detail"
private const val PREVIEW_ACCOUNT_ID = "desktop-preview"
private const val SHANGHAI_TIME_ZONE = "Asia/Shanghai"

/** Desktop mock 的独立无参数导航契约，避免与正式入口的 [ScheduleMainNavArgument] 使用同一个导航类型键。 */
@Serializable
data object ScheduleDesktopPreviewNavArgument : AppNavArgument

/** Desktop 邮子清单 mock 的独立参数类型，避免与课表预览在 Navigation3 中重复注册。 */
@Serializable
data object ScheduleDesktopTodoPreviewNavArgument : AppNavArgument

/** Desktop Mock 待办详情参数，仅携带 identity，详情数据仍从共享预览仓库读取。 */
@Serializable
data class ScheduleDesktopTodoDetailPreviewNavArgument(
  val scheduleId: ScheduleId,
  val recurrenceId: RecurrenceId? = null,
) : AppNavArgument

/**
 * Desktop 开发环境的日程主页面入口。
 *
 * 该入口复用正式 [SchedulePage]，但注入只存在于内存中的 mock 仓库；数据不会写入 Room、不会绑定登录账号，也不会
 * 发起 Ktorfit 请求。它只用于快速观察主页面、时间轴和编辑弹窗。
 */
@AppNav(route = NAV_SCHEDULE_DESKTOP_PREVIEW)
class ScheduleDesktopPreviewNavEntry : AppNavEntry<ScheduleDesktopPreviewNavArgument>() {

  /** Desktop mock 不依赖账号，便于从开发 Home 直接进入。 */
  override fun isNeedLogin(argument: ScheduleDesktopPreviewNavArgument): Boolean = false

  /** 预览页保持单例内容，避免重复点击 Home 后叠加多份内存状态。 */
  override fun getContentKey(argument: ScheduleDesktopPreviewNavArgument): String =
    "schedule_desktop_preview_singleton"

  /** 创建页面级内存仓库，并把它注入与正式页面相同的 ViewModel。 */
  @Composable
  override fun Content(argument: ScheduleDesktopPreviewNavArgument) {
    val viewModel = viewModel { ScheduleMainViewModel(DesktopSchedulePreviewRepository) }
    SchedulePage(argument = ScheduleMainNavArgument(), viewModel = viewModel)
  }
}

/**
 * Desktop 开发环境的邮子清单入口。
 *
 * 使用独立的纯内存仓库，初始数据只保留待办常用的时间点事项；课表预览所需的复杂时间段不会混入清单。
 * 所有新增、完成和删除只影响当前预览会话。
 */
@AppNav(route = NAV_SCHEDULE_TODO_DESKTOP_PREVIEW)
class ScheduleDesktopTodoPreviewNavEntry : AppNavEntry<ScheduleDesktopTodoPreviewNavArgument>() {

  /** 预览不依赖真实账号。 */
  override fun isNeedLogin(argument: ScheduleDesktopTodoPreviewNavArgument): Boolean = false

  /** 页面保持单例，避免重复点击 Home 叠加多个 mock 仓库。 */
  override fun getContentKey(argument: ScheduleDesktopTodoPreviewNavArgument): String =
    "schedule_desktop_todo_preview_singleton"

  /** 注入纯内存仓库并渲染正式邮子清单页面。 */
  @Composable
  override fun Content(argument: ScheduleDesktopTodoPreviewNavArgument) {
    val viewModel = viewModel { ScheduleMainViewModel(DesktopScheduleTodoPreviewRepository) }
    ScheduleTodoPage(
      argument = ScheduleTodoNavArgument(),
      viewModel = viewModel,
      onBack = argument::popBackStack,
    )
  }
}

/**
 * Desktop Mock 的 Figma 版待办详情预览入口。
 *
 * 正式 Mock 清单点击条目已改用共享编辑弹窗；此入口继续保留，供单独检查未删除的 Figma 详情方案。
 */
@AppNav(route = NAV_SCHEDULE_TODO_DETAIL_DESKTOP_PREVIEW)
class ScheduleDesktopTodoDetailPreviewNavEntry :
  AppNavEntry<ScheduleDesktopTodoDetailPreviewNavArgument>() {

  /** 开发预览不依赖真实登录账号。 */
  override fun isNeedLogin(argument: ScheduleDesktopTodoDetailPreviewNavArgument): Boolean = false

  /** 每个 Mock 待办实例使用稳定内容键，保持正常返回栈语义。 */
  override fun getContentKey(argument: ScheduleDesktopTodoDetailPreviewNavArgument): String =
    "schedule_desktop_todo_detail_${argument.scheduleId}_${argument.recurrenceId}"

  /** 用共享单例仓库创建详情 ViewModel，并复用正式详情页面。 */
  @Composable
  override fun Content(argument: ScheduleDesktopTodoDetailPreviewNavArgument) {
    val viewModel = viewModel { ScheduleMainViewModel(DesktopScheduleTodoPreviewRepository) }
    ScheduleTodoDetailRoute(
      scheduleId = argument.scheduleId,
      recurrenceId = argument.recurrenceId,
      viewModel = viewModel,
      onBack = argument::popBackStack,
    )
  }
}

/**
 * 仅供 Desktop UI 预览的内存仓库。
 *
 * 初始化快照覆盖分类、普通定时事项、截止事项、全天事项、周重复事项和一次 occurrence 覆盖；基本命令会直接更新
 * [snapshot]，从而可以在预览页实际体验新增、编辑、完成和删除。它不模拟同步失败与远端合并。
 */
private val DesktopSchedulePreviewRepository = DesktopPreviewRepository(createDesktopPreviewSnapshot())

/** Desktop 邮子清单与其详情页共享的单例 mock，所有编辑只在当前进程内生效。 */
private val DesktopScheduleTodoPreviewRepository: ScheduleRepository =
  DesktopPreviewRepository(createDesktopTodoPreviewSnapshot())

/**
 * 为 Android 临时验收创建账号绑定的邮子清单 mock 仓库。
 *
 * [accountId] 只用于满足账号 façade 的快照隔离校验；数据仍完全位于内存，不会持久化或请求后端。
 */
internal fun createScheduleTodoPreviewRepository(accountId: String): ScheduleRepository =
  DesktopPreviewRepository(createDesktopTodoPreviewSnapshot().copy(accountId = accountId))

/** 接收不同初始快照的轻量内存仓库，供两个 Desktop 预览入口隔离演示数据。 */
private class DesktopPreviewRepository(initialSnapshot: ScheduleSnapshot) : ScheduleRepository {
  private val mutableSnapshot = MutableStateFlow(initialSnapshot)

  override val snapshot: StateFlow<ScheduleSnapshot> = mutableSnapshot
  override val calendarChanges: Flow<ScheduleCalendarChange> = emptyFlow()

  /** mock 快照在构造时已经可用，无持久化或网络初始化副作用。 */
  override suspend fun initialize() = Unit

  /**
   * 在内存中应用页面会发出的基本命令。
   *
   * @return 支持的命令返回未触网成功；“此次及后续”等当前 v2 未支持操作返回业务拒绝，避免预览静默伪造能力。
   */
  override suspend fun execute(command: ScheduleCommand): ScheduleSyncResult {
    val current = mutableSnapshot.value
    val next = when (command) {
      is ScheduleCommand.Create -> current.copy(
        schedules = current.schedules + command.schedule,
      )
      is ScheduleCommand.Update -> current.copy(
        schedules = current.schedules.replaceBy({ it.id == command.schedule.id }, command.schedule),
      )
      is ScheduleCommand.Delete -> current.copy(
        schedules = current.schedules.filterNot { it.id == command.scheduleId },
        exceptions = current.exceptions.filterNot { it.scheduleId == command.scheduleId },
      )
      is ScheduleCommand.CompleteNonRepeating -> current.copy(
        schedules = current.schedules.map { schedule ->
          if (schedule.id != command.scheduleId) schedule
          else schedule.copy(
            completion = if (command.completed) ScheduleCompletion.COMPLETED else ScheduleCompletion.PENDING,
            updatedAt = Clock.System.now(),
          )
        },
      )
      is ScheduleCommand.UpsertOccurrenceException -> current.copy(
        exceptions = current.exceptions.replaceBy(
          { it.scheduleId == command.exception.scheduleId && it.recurrenceId == command.exception.recurrenceId },
          command.exception,
        ),
      )
      is ScheduleCommand.DeleteOccurrenceException -> current.copy(
        exceptions = current.exceptions.filterNot {
          it.scheduleId == command.scheduleId && it.recurrenceId == command.recurrenceId
        },
      )
      is ScheduleCommand.CreateCategory -> current.copy(
        categories = current.categories + command.category,
      )
      is ScheduleCommand.UpdateCategory -> current.copy(
        categories = current.categories.replaceBy({ it.id == command.category.id }, command.category),
      )
      is ScheduleCommand.SaveScheduleWithNewCategory -> current.copy(
        categories = current.categories.replaceBy({ it.id == command.category.id }, command.category),
        schedules = current.schedules.replaceBy({ it.id == command.schedule.id }, command.schedule),
      )
      is ScheduleCommand.DeleteCategory -> current.copy(
        categories = current.categories.filterNot { it.id == command.categoryId },
        schedules = current.schedules.map { schedule ->
          if (schedule.categoryId == command.categoryId) schedule.copy(categoryId = null) else schedule
        },
      )
      ScheduleCommand.RequestSync -> current
      is ScheduleCommand.SplitSeries,
      is ScheduleCommand.DeleteThisAndFollowing -> {
        return ScheduleSyncResult.Failure(
          ScheduleRemoteError.MutationRejected(
            ScheduleMutationBusinessRejectionReason.UNSUPPORTED_RECURRENCE,
          ),
          attempted = false,
        )
      }
    }
    mutableSnapshot.value = next.copy(
      status = ScheduleRepositoryStatus.Ready(pendingCount = 0, hasPendingDeletes = false),
    )
    return ScheduleSyncResult.Success(attempted = false)
  }
}

/** 以 identity 替换列表元素；不存在时追加，符合预览仓库的 upsert 语义。 */
private fun <T> List<T>.replaceBy(matches: (T) -> Boolean, value: T): List<T> =
  if (any(matches)) map { if (matches(it)) value else it } else this + value

/** 创建始终落在“今天”的示例数据，打开页面即可在默认时间轴看到。 */
private fun createDesktopPreviewSnapshot(): ScheduleSnapshot {
  val today = TodayNoEffect
  val now = Clock.System.now()
  val studyCategory = ScheduleCategory(
    id = CategoryId("desktop-study"),
    revision = 1,
    name = "学习",
    color = "#5B8FF9",
    sortOrder = 0,
  )
  val lifeCategory = ScheduleCategory(
    id = CategoryId("desktop-life"),
    revision = 1,
    name = "生活",
    color = "#61DDAA",
    sortOrder = 1,
  )
  val reviewSchedule = Schedule(
    id = ScheduleId("019c6f00-0000-7000-8000-000000000001"),
    revision = 1,
    title = "上午开发（贯穿事项）",
    description = "上半段两列、下半段三列的基准长事项",
    categoryId = studyCategory.id,
    timing = ScheduleTiming.Timed(
      start = MinuteTimeDate(today, 8, 0),
      durationMinutes = 240,
      timeZoneId = SHANGHAI_TIME_ZONE,
    ),
    recurrence = null,
    reminders = listOf(ScheduleReminder(ReminderId("review-reminder"), 15, ReminderChannel.DEVICE)),
    completion = ScheduleCompletion.PENDING,
    createdAt = now,
    updatedAt = now,
  )
  // 08:30–10:00 只有它与贯穿事项重叠，上午簇的上半部分稳定为两列。
  val overlappingDesignSchedule = Schedule(
    id = ScheduleId("019c6f00-0000-7000-8000-000000000005"),
    revision = 1,
    title = "交互方案讨论",
    description = "上午复杂簇的两列区间",
    categoryId = studyCategory.id,
    timing = ScheduleTiming.Timed(
      start = MinuteTimeDate(today, 8, 30),
      durationMinutes = 90,
      timeZoneId = SHANGHAI_TIME_ZONE,
    ),
    recurrence = null,
    reminders = emptyList(),
    completion = ScheduleCompletion.PENDING,
    createdAt = now,
    updatedAt = now,
  )
  val overlappingDebugSchedule = Schedule(
    id = ScheduleId("019c6f00-0000-7000-8000-000000000006"),
    revision = 1,
    title = "同步问题排查",
    description = "与贯穿事项组成下半部分基础两列",
    categoryId = studyCategory.id,
    timing = ScheduleTiming.Timed(
      start = MinuteTimeDate(today, 10, 0),
      durationMinutes = 120,
      timeZoneId = SHANGHAI_TIME_ZONE,
    ),
    recurrence = null,
    reminders = emptyList(),
    completion = ScheduleCompletion.PENDING,
    createdAt = now,
    updatedAt = now,
  )
  // 10:30–11:30 再插入一条短事项，使同一上午簇从上半部分两列切换为下半部分三列。
  val overlappingTestSchedule = Schedule(
    id = ScheduleId("019c6f00-0000-7000-8000-000000000008"),
    revision = 1,
    title = "时间轴布局验收",
    description = "上午复杂簇的三列区间",
    categoryId = lifeCategory.id,
    timing = ScheduleTiming.Timed(
      start = MinuteTimeDate(today, 10, 30),
      durationMinutes = 60,
      timeZoneId = SHANGHAI_TIME_ZONE,
    ),
    recurrence = null,
    reminders = emptyList(),
    completion = ScheduleCompletion.PENDING,
    createdAt = now,
    updatedAt = now,
  )
  val recurringId = RecurrenceId(
    originalDateTime = MinuteTimeDate(today, 13, 30),
    timeZoneId = SHANGHAI_TIME_ZONE,
    allDay = false,
  )
  val recurringSchedule = Schedule(
    id = ScheduleId("019c6f00-0000-7000-8000-000000000002"),
    revision = 1,
    title = "每周产品例会",
    description = "周重复 mock 数据",
    categoryId = studyCategory.id,
    timing = ScheduleTiming.Timed(
      start = recurringId.originalDateTime,
      durationMinutes = 90,
      timeZoneId = SHANGHAI_TIME_ZONE,
    ),
    recurrence = RecurrenceRule(
      frequency = RecurrenceFrequency.WEEKLY,
      byWeekDays = setOf(IsoWeekDay.fromIsoNumber(today.dayOfWeekNumber)!!),
      end = RecurrenceEnd.Never,
    ),
    reminders = emptyList(),
    completion = ScheduleCompletion.PENDING,
    createdAt = now,
    updatedAt = now,
  )
  val deadlineSchedule = Schedule(
    id = ScheduleId("019c6f00-0000-7000-8000-000000000003"),
    revision = 1,
    title = "提交同步验收记录",
    description = "截止事项 mock 数据",
    categoryId = studyCategory.id,
    timing = ScheduleTiming.Deadline(MinuteTimeDate(today, 18, 30), SHANGHAI_TIME_ZONE),
    recurrence = null,
    reminders = listOf(ScheduleReminder(ReminderId("deadline-reminder"), 30, ReminderChannel.DEVICE)),
    completion = ScheduleCompletion.PENDING,
    createdAt = now,
    updatedAt = now,
  )
  // 明天到期，保证邮子清单始终能看到“临期”标签和顶部临期提示条。
  val dueSoonTodo = Schedule(
    id = ScheduleId("019c6f00-0000-7000-8000-000000000016"),
    revision = 1,
    title = "临期：提交课程报告",
    description = "明天到期，用于观察临期样式",
    categoryId = studyCategory.id,
    timing = ScheduleTiming.Deadline(
      MinuteTimeDate(today.plusDays(1), 20, 0),
      SHANGHAI_TIME_ZONE,
    ),
    recurrence = null,
    reminders = listOf(ScheduleReminder(ReminderId("due-soon-reminder"), 30, ReminderChannel.DEVICE)),
    completion = ScheduleCompletion.PENDING,
    createdAt = now,
    updatedAt = now,
  )
  // 十天后到期，作为普通未到期事项与临期/超期卡片并排对比。
  val futureTodo = Schedule(
    id = ScheduleId("019c6f00-0000-7000-8000-000000000017"),
    revision = 1,
    title = "未到期：准备下周分享",
    description = "十天后到期，不显示临期标签",
    categoryId = lifeCategory.id,
    timing = ScheduleTiming.Deadline(
      MinuteTimeDate(today.plusDays(10), 18, 0),
      SHANGHAI_TIME_ZONE,
    ),
    recurrence = null,
    reminders = emptyList(),
    completion = ScheduleCompletion.PENDING,
    createdAt = now,
    updatedAt = now,
  )
  // 13:00、13:30、14:00 依次开始，14:00–14:30 形成阶梯式三列交叠。
  val overlappingAfternoonStartSchedule = Schedule(
    id = ScheduleId("019c6f00-0000-7000-8000-000000000009"),
    revision = 1,
    title = "接口字段核对",
    description = "阶梯式三重叠的第一阶",
    categoryId = studyCategory.id,
    timing = ScheduleTiming.Timed(
      start = MinuteTimeDate(today, 13, 0),
      durationMinutes = 90,
      timeZoneId = SHANGHAI_TIME_ZONE,
    ),
    recurrence = null,
    reminders = emptyList(),
    completion = ScheduleCompletion.PENDING,
    createdAt = now,
    updatedAt = now,
  )
  val overlappingIntegrationSchedule = Schedule(
    id = ScheduleId("019c6f00-0000-7000-8000-000000000007"),
    revision = 1,
    title = "Schedule 接口联调",
    description = "阶梯式三重叠的第三阶",
    categoryId = lifeCategory.id,
    timing = ScheduleTiming.Timed(
      start = MinuteTimeDate(today, 14, 0),
      durationMinutes = 90,
      timeZoneId = SHANGHAI_TIME_ZONE,
    ),
    recurrence = null,
    reminders = emptyList(),
    completion = ScheduleCompletion.PENDING,
    createdAt = now,
    updatedAt = now,
  )
  // 一条长事项包含两条互不相交的短事项，验证后两条能复用同一列而非错误扩成三列。
  val nestedLongSchedule = Schedule(
    id = ScheduleId("019c6f00-0000-7000-8000-000000000010"),
    revision = 1,
    title = "发布前集中处理",
    description = "包住两条短事项的长区间",
    categoryId = studyCategory.id,
    timing = ScheduleTiming.Timed(
      start = MinuteTimeDate(today, 16, 0),
      durationMinutes = 150,
      timeZoneId = SHANGHAI_TIME_ZONE,
    ),
    recurrence = null,
    reminders = emptyList(),
    completion = ScheduleCompletion.PENDING,
    createdAt = now,
    updatedAt = now,
  )
  val nestedFirstShortSchedule = Schedule(
    id = ScheduleId("019c6f00-0000-7000-8000-000000000011"),
    revision = 1,
    title = "第一次短会",
    description = "被长事项包含",
    categoryId = lifeCategory.id,
    timing = ScheduleTiming.Timed(
      start = MinuteTimeDate(today, 16, 15),
      durationMinutes = 30,
      timeZoneId = SHANGHAI_TIME_ZONE,
    ),
    recurrence = null,
    reminders = emptyList(),
    completion = ScheduleCompletion.PENDING,
    createdAt = now,
    updatedAt = now,
  )
  val nestedSecondShortSchedule = Schedule(
    id = ScheduleId("019c6f00-0000-7000-8000-000000000012"),
    revision = 1,
    title = "第二次短会",
    description = "应复用第一次短会所在列",
    categoryId = lifeCategory.id,
    timing = ScheduleTiming.Timed(
      start = MinuteTimeDate(today, 17, 15),
      durationMinutes = 30,
      timeZoneId = SHANGHAI_TIME_ZONE,
    ),
    recurrence = null,
    reminders = emptyList(),
    completion = ScheduleCompletion.PENDING,
    createdAt = now,
    updatedAt = now,
  )
  // 两条基础事项在 20:00 首尾相接并不重叠；跨界事项分别与它们重叠，把三条连接为同一布局簇。
  val bridgeFirstSchedule = Schedule(
    id = ScheduleId("019c6f00-0000-7000-8000-000000000013"),
    revision = 1,
    title = "晚间整理 A",
    description = "与 B 仅边界接触",
    categoryId = lifeCategory.id,
    timing = ScheduleTiming.Timed(
      start = MinuteTimeDate(today, 19, 0),
      durationMinutes = 60,
      timeZoneId = SHANGHAI_TIME_ZONE,
    ),
    recurrence = null,
    reminders = emptyList(),
    completion = ScheduleCompletion.COMPLETED,
    createdAt = now,
    updatedAt = now,
  )
  val bridgeSecondSchedule = Schedule(
    id = ScheduleId("019c6f00-0000-7000-8000-000000000014"),
    revision = 1,
    title = "晚间整理 B",
    description = "应与 A 复用同一列",
    categoryId = lifeCategory.id,
    timing = ScheduleTiming.Timed(
      start = MinuteTimeDate(today, 20, 0),
      durationMinutes = 60,
      timeZoneId = SHANGHAI_TIME_ZONE,
    ),
    recurrence = null,
    reminders = emptyList(),
    completion = ScheduleCompletion.PENDING,
    createdAt = now,
    updatedAt = now,
  )
  val bridgeAcrossSchedule = Schedule(
    id = ScheduleId("019c6f00-0000-7000-8000-000000000015"),
    revision = 1,
    title = "跨界联调",
    description = "同时连接 A 与 B，但最大并发仍为两条",
    categoryId = studyCategory.id,
    timing = ScheduleTiming.Timed(
      start = MinuteTimeDate(today, 19, 30),
      durationMinutes = 60,
      timeZoneId = SHANGHAI_TIME_ZONE,
    ),
    recurrence = null,
    reminders = emptyList(),
    completion = ScheduleCompletion.PENDING,
    createdAt = now,
    updatedAt = now,
  )
  val allDaySchedule = Schedule(
    id = ScheduleId("019c6f00-0000-7000-8000-000000000004"),
    revision = 1,
    title = "全天：校历检查",
    description = "全天事项 mock 数据",
    categoryId = lifeCategory.id,
    timing = ScheduleTiming.AllDay(today),
    recurrence = null,
    reminders = emptyList(),
    completion = ScheduleCompletion.COMPLETED,
    createdAt = now,
    updatedAt = now,
  )
  val occurrenceOverride = ScheduleOccurrenceException(
    scheduleId = recurringSchedule.id,
    recurrenceId = recurringId,
    revision = 1,
    status = OccurrenceStatus.ACTIVE,
    patch = OccurrencePatch(
      title = FieldPatch.Replace("本周产品例会（改为 UI 评审）"),
      description = FieldPatch.Replace("单次 occurrence 覆盖 mock 数据"),
    ),
    createdAt = now,
    updatedAt = now,
  )
  return ScheduleSnapshot(
    schedules = listOf(
      reviewSchedule,
      overlappingDesignSchedule,
      overlappingDebugSchedule,
      overlappingTestSchedule,
      overlappingAfternoonStartSchedule,
      recurringSchedule,
      overlappingIntegrationSchedule,
      nestedLongSchedule,
      nestedFirstShortSchedule,
      nestedSecondShortSchedule,
      deadlineSchedule,
      dueSoonTodo,
      futureTodo,
      bridgeFirstSchedule,
      bridgeSecondSchedule,
      bridgeAcrossSchedule,
      allDaySchedule,
    ),
    exceptions = listOf(occurrenceOverride),
    categories = listOf(studyCategory, lifeCategory),
    status = ScheduleRepositoryStatus.Ready(pendingCount = 0, hasPendingDeletes = false),
    accountId = PREVIEW_ACCOUNT_ID,
  )
}

/**
 * 创建邮子清单专用 mock。
 *
 * 数据只覆盖普通、已到期、临期、单个时间段、周重复、月重复与近期完成这些核心状态；所有时间都由
 * 打开预览时的当前上海时间换算，避免固定日期逐渐失去临期语义。课表的复杂重叠样例仍不混入清单。
 */
private fun createDesktopTodoPreviewSnapshot(): ScheduleSnapshot {
  val now = Clock.System.now()
  val zone = TimeZone.of(SHANGHAI_TIME_ZONE)
  val nowLocal = now.toLocalDateTime(zone).toMinuteTimeDate()
  val overdueDue = nowLocal.minusMinutes(2 * 60)
  val dueSoonDue = nowLocal.plusMinutes(24 * 60)
  val intervalStart = MinuteTimeDate(nowLocal.date.plusDays(2), 14, 0)
  val weeklyDue = nowLocal.plusDays(2)
  val monthlyDue = nowLocal.plusDays(3)
  val studyCategory = ScheduleCategory(
    id = CategoryId("desktop-todo-study"),
    revision = 1,
    name = "学习",
    color = "#5B8FF9",
    sortOrder = 0,
  )
  val lifeCategory = ScheduleCategory(
    id = CategoryId("desktop-todo-life"),
    revision = 1,
    name = "生活",
    color = "#61DDAA",
    sortOrder = 1,
  )

  /** 创建符合领域 identity 的精简待办；mock 不模拟远端版本推进。 */
  fun todo(
    suffix: String,
    title: String,
    description: String,
    categoryId: CategoryId,
    timing: ScheduleTiming,
    recurrence: RecurrenceRule? = null,
    reminders: List<ScheduleReminder> = emptyList(),
    completion: ScheduleCompletion = ScheduleCompletion.PENDING,
    updatedAt: Instant = now,
  ): Schedule = Schedule(
    id = ScheduleId("019c7f00-0000-7000-8000-000000000$suffix"),
    revision = 1,
    title = title,
    description = description,
    categoryId = categoryId,
    timing = timing,
    recurrence = recurrence,
    reminders = reminders,
    completion = completion,
    createdAt = now - 10.days,
    updatedAt = updatedAt,
  )

  return ScheduleSnapshot(
    schedules = listOf(
      todo(
        suffix = "001",
        title = "整理本周待办",
        description = "普通待办，不设置截止时间",
        categoryId = lifeCategory.id,
        timing = ScheduleTiming.Unscheduled,
      ),
      todo(
        suffix = "002",
        title = "已到期：补交实验报告",
        description = "两小时前到期，用于观察超期样式",
        categoryId = studyCategory.id,
        timing = ScheduleTiming.Deadline(overdueDue, SHANGHAI_TIME_ZONE),
      ),
      todo(
        suffix = "003",
        title = "临期：提交课程作业",
        description = "一天后到期，用于观察临期样式",
        categoryId = studyCategory.id,
        timing = ScheduleTiming.Deadline(dueSoonDue, SHANGHAI_TIME_ZONE),
        reminders = listOf(
          ScheduleReminder(
            id = ReminderId("desktop-todo-due-soon-reminder"),
            offsetMinutes = 30,
            channel = ReminderChannel.DEVICE,
          ),
        ),
      ),
      todo(
        suffix = "004",
        title = "项目方案讨论",
        description = "用于检查清单中的起止时间展示",
        categoryId = studyCategory.id,
        timing = ScheduleTiming.Timed(
          start = intervalStart,
          durationMinutes = 90,
          timeZoneId = SHANGHAI_TIME_ZONE,
        ),
      ),
      todo(
        suffix = "005",
        title = "每周复盘",
        description = "每周重复的时间点待办",
        categoryId = studyCategory.id,
        timing = ScheduleTiming.Deadline(weeklyDue, SHANGHAI_TIME_ZONE),
        recurrence = RecurrenceRule(
          frequency = RecurrenceFrequency.WEEKLY,
          byWeekDays = setOf(IsoWeekDay.fromIsoNumber(weeklyDue.date.dayOfWeekNumber)!!),
        ),
      ),
      todo(
        suffix = "006",
        title = "每月账单检查",
        description = "每月重复的时间点待办",
        categoryId = lifeCategory.id,
        timing = ScheduleTiming.Deadline(monthlyDue, SHANGHAI_TIME_ZONE),
        recurrence = RecurrenceRule(
          frequency = RecurrenceFrequency.MONTHLY,
          byMonthDays = setOf(monthlyDue.date.dayOfMonth),
        ),
      ),
      todo(
        suffix = "007",
        title = "已完成：整理资料",
        description = "两天前完成，仍在七天展示窗口内",
        categoryId = lifeCategory.id,
        timing = ScheduleTiming.Unscheduled,
        completion = ScheduleCompletion.COMPLETED,
        updatedAt = now - 2.days,
      ),
    ),
    exceptions = emptyList(),
    categories = listOf(studyCategory, lifeCategory),
    status = ScheduleRepositoryStatus.Ready(pendingCount = 0, hasPendingDeletes = false),
    accountId = PREVIEW_ACCOUNT_ID,
  )
}
