package com.cyxbs.pages.schedule.ui.main

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.components.config.time.toMinuteTimeDate
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.pages.schedule.api.ScheduleMainNavArgument
import com.cyxbs.pages.schedule.domain.model.CategoryId
import com.cyxbs.pages.schedule.domain.model.IsoWeekDay
import com.cyxbs.pages.schedule.domain.model.RecurrenceFrequency
import com.cyxbs.pages.schedule.domain.model.RecurrenceRule
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleCategory
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.model.ScheduleReminder
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.model.ScheduleTodoState
import com.cyxbs.pages.schedule.domain.repository.ScheduleCalendarChange
import com.cyxbs.pages.schedule.domain.repository.ScheduleCommand
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepository
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryStatus
import com.cyxbs.pages.schedule.domain.repository.ScheduleSnapshot
import com.cyxbs.pages.schedule.domain.repository.ScheduleSyncResult
import com.cyxbs.pages.schedule.ui.category.ScheduleCategoryColorPresets
import com.cyxbs.pages.schedule.ui.category.encodeScheduleCategoryColor
import com.cyxbs.pages.schedule.ui.todo.main.ScheduleTodoPage
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

private const val NAV_SCHEDULE_PREVIEW = "schedule/preview"
private const val PREVIEW_ACCOUNT_ID = "schedule-preview"
private const val SHANGHAI_TIME_ZONE = "Asia/Shanghai"

/** Mock 的独立无参数导航契约，避免与正式入口的 [ScheduleMainNavArgument] 使用同一个导航类型键。 */
@Serializable
data object SchedulePreviewNavArgument : AppNavArgument

/**
 * 开发环境的日程主页入口。
 *
 * 该入口复用正式清单/时间轴页面，但注入只存在于内存中的 mock 仓库；数据不会写入 Room、不会绑定登录账号，
 * 也不会发起 Ktorfit 请求。
 */
@AppNav(route = NAV_SCHEDULE_PREVIEW)
class SchedulePreviewNavEntry : AppNavEntry<SchedulePreviewNavArgument>() {

  /** Mock 不依赖账号，便于从开发 Home 直接进入。 */
  override fun isNeedLogin(argument: SchedulePreviewNavArgument): Boolean = false

  /** 预览页保持单例内容，避免重复点击 Home 后叠加多份内存状态。 */
  override fun getContentKey(argument: SchedulePreviewNavArgument): String =
    "schedule_preview_singleton"

  /** 创建页面级内存仓库，并把它注入与正式页面相同的 ViewModel。 */
  @Composable
  override fun Content(argument: SchedulePreviewNavArgument) {
    val viewModel = viewModel { ScheduleMainViewModel(SchedulePreviewRepository) }
    ScheduleTodoPage(
      argument = ScheduleMainNavArgument(),
      viewModel = viewModel,
      onBack = argument::popBackStack,
    )
  }
}

/**
 * 日程主页预览共享的单例 mock。
 *
 * 初始化快照覆盖分类、普通定时事项、截止事项、全天事项和重复事项；所有编辑只影响当前进程，
 * 不写 Room、不请求后端，也不模拟版本冲突与远端合并。
 */
private val SchedulePreviewRepository: ScheduleRepository =
  InMemorySchedulePreviewRepository(createSchedulePreviewSnapshot())

/**
 * 为 Android 临时验收创建账号绑定的邮子清单 mock 仓库。
 *
 * [accountId] 只用于满足账号 façade 的快照隔离校验；数据仍完全位于内存，不会持久化或请求后端。
 */
internal fun createScheduleTodoPreviewRepository(accountId: String): ScheduleRepository {
  val original = createSchedulePreviewSnapshot()
  // Android 验收 mock 直接在权威日程字段中关联三条重叠数据，不再依赖额外的进程内选择表。
  val snapshot = original.copy(
    accountId = accountId,
    schedules = original.schedules.map { schedule ->
      if (schedule.title.startsWith(COURSE_OVERLAP_MOCK_TITLE_PREFIX)) {
        schedule.copy(linkedToCourse = true)
      } else {
        schedule
      }
    },
  )
  return InMemorySchedulePreviewRepository(snapshot)
}

/** 接收不同初始快照的轻量内存仓库，供通用预览页和 Android 临时验收入口复用。 */
private class InMemorySchedulePreviewRepository(initialSnapshot: ScheduleSnapshot) : ScheduleRepository {
  private val mutableSnapshot = MutableStateFlow(initialSnapshot)

  override val snapshot: StateFlow<ScheduleSnapshot> = mutableSnapshot
  override val calendarChanges: Flow<ScheduleCalendarChange> = emptyFlow()

  /** mock 快照在构造时已经可用，无持久化或网络初始化副作用。 */
  override suspend fun initialize() = Unit

  /**
   * 在内存中应用页面会发出的命令，包括重复系列的拆分与后半段删除。
   *
   * @return 命令更新内存快照后返回未触网成功；该仓库只模拟本地结果，不模拟版本冲突和远端合并。
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
        occurrenceAdjustments = current.occurrenceAdjustments.filterNot { it.scheduleId == command.scheduleId },
      )
      is ScheduleCommand.CompleteNonRepeating -> current.copy(
        schedules = current.schedules.map { schedule ->
          if (schedule.id != command.scheduleId) schedule
          else schedule.copy(
            todoState = if (command.completed) ScheduleTodoState.COMPLETED else ScheduleTodoState.PENDING,
            updatedAt = Clock.System.now(),
          )
        },
      )
      is ScheduleCommand.UpsertOccurrenceAdjustment -> current.copy(
        occurrenceAdjustments = current.occurrenceAdjustments.replaceBy(
          { it.scheduleId == command.adjustment.scheduleId && it.recurrenceId == command.adjustment.recurrenceId },
          command.adjustment,
        ),
      )
      is ScheduleCommand.DeleteOccurrenceAdjustment -> current.copy(
        occurrenceAdjustments = current.occurrenceAdjustments.filterNot {
          it.scheduleId == command.scheduleId && it.recurrenceId == command.recurrenceId
        },
      )
      is ScheduleCommand.CreateCategory -> current.copy(
        categories = current.categories + command.category,
      )
      is ScheduleCommand.UpdateCategory -> current.copy(
        categories = current.categories.replaceBy({ it.id == command.category.id }, command.category),
      )
      is ScheduleCommand.ReorderCategories -> current.copy(
        categories = command.categories.mapIndexed { index, category ->
          category.copy(sortOrder = index)
        },
      )
      is ScheduleCommand.SaveScheduleWithNewCategory -> current.copy(
        categories = current.categories.replaceBy({ it.id == command.category.id }, command.category),
        schedules = current.schedules.replaceBy({ it.id == command.schedule.id }, command.schedule),
      )
      is ScheduleCommand.SaveOccurrenceWithNewCategory -> current.copy(
        categories = current.categories.replaceBy({ it.id == command.category.id }, command.category),
        occurrenceAdjustments = current.occurrenceAdjustments.replaceBy(
          {
            it.scheduleId == command.adjustment.scheduleId &&
              it.recurrenceId == command.adjustment.recurrenceId
          },
          command.adjustment,
        ),
      )
      is ScheduleCommand.DeleteCategory -> current.copy(
        categories = current.categories.filterNot { it.id == command.categoryId },
        schedules = current.schedules.map { schedule ->
          if (schedule.categoryId == command.categoryId) schedule.copy(categoryId = null) else schedule
        },
      )
      is ScheduleCommand.SplitSeries -> current.applyPreviewSeriesSplit(command)
      is ScheduleCommand.DeleteThisAndFollowing -> current.copy(
        schedules = current.schedules.replaceBy(
          { it.id == command.previousSchedule.id },
          command.previousSchedule,
        ),
        occurrenceAdjustments = current.occurrenceAdjustments.filterNot { adjustment ->
          adjustment.scheduleId == command.previousSchedule.id &&
            adjustment.recurrenceId.originalDateTime.date >= command.recurrenceId.originalDateTime.date
        },
      )
      ScheduleCommand.RequestSync -> current
    }
    mutableSnapshot.value = next.copy(
      status = ScheduleRepositoryStatus.Ready(pendingCount = 0, hasPendingDeletes = false),
    )
    return ScheduleSyncResult.Success(attempted = false)
  }
}

/**
 * 在预览快照中原子应用“此次及以后”拆分。
 *
 * 边界 occurrence 的有效字段已经提升到新系列，因此边界自身的旧调整不再保留；更晚的调整仍使用原始
 * recurrence identity，只把所属日程切换为新系列。该行为与正式 Room reducer 一致，但不模拟版本和上传状态。
 */
private fun ScheduleSnapshot.applyPreviewSeriesSplit(
  command: ScheduleCommand.SplitSeries,
): ScheduleSnapshot {
  val boundaryDate = command.recurrenceId.originalDateTime.date
  return copy(
    categories = command.newCategory?.let { category ->
      categories.replaceBy({ it.id == category.id }, category)
    } ?: categories,
    schedules = schedules
      .replaceBy({ it.id == command.previousSchedule.id }, command.previousSchedule)
      .replaceBy({ it.id == command.followingSchedule.id }, command.followingSchedule),
    occurrenceAdjustments = occurrenceAdjustments.mapNotNull { adjustment ->
      if (adjustment.scheduleId != command.previousSchedule.id) {
        adjustment
      } else when {
        adjustment.recurrenceId.originalDateTime.date < boundaryDate -> adjustment
        adjustment.recurrenceId.originalDateTime.date == boundaryDate -> null
        else -> adjustment.copy(scheduleId = command.followingSchedule.id)
      }
    },
  )
}

/** 以 identity 替换列表元素；不存在时追加，符合预览仓库的 upsert 语义。 */
private fun <T> List<T>.replaceBy(matches: (T) -> Boolean, value: T): List<T> =
  if (any(matches)) map { if (matches(it)) value else it } else this + value

/**
 * 创建邮子清单专用 mock。
 *
 * 数据覆盖普通、已到期、临期、时间段、三条下午重叠日程、全天、周重复、月重复与近期完成。
 * 已到期和临期仍按当前上海时间换算；用于课表投影的数据固定在 2026 年 3 月开学周，方便课表联调。
 */
private fun createSchedulePreviewSnapshot(): ScheduleSnapshot {
  val now = Clock.System.now()
  val zone = TimeZone.of(SHANGHAI_TIME_ZONE)
  val nowLocal = now.toLocalDateTime(zone).toMinuteTimeDate()
  val overdueDue = nowLocal.minusMinutes(2 * 60)
  val dueSoonDue = nowLocal.plusMinutes(24 * 60)
  val courseWeekStart = Date(2026, 3, 2)
  val pointDue = MinuteTimeDate(courseWeekStart, 10, 30)
  val intervalStart = MinuteTimeDate(courseWeekStart.plusDays(1), 14, 0)
  val overlapAfternoonDate = courseWeekStart.plusDays(1)
  val weeklyDue = MinuteTimeDate(courseWeekStart.plusDays(2), 15, 30)
  val monthlyDue = MinuteTimeDate(courseWeekStart.plusDays(3), 18, 0)
  val allDayDate = courseWeekStart.plusDays(4)
  // 直接复用分组管理页候选，保证课表中的 mock 与用户实际可选颜色始终一一对应。
  val colorPreviewCategories = ScheduleCategoryColorPresets.mapIndexed { index, preset ->
    ScheduleCategory(
      id = CategoryId("desktop-todo-color-$index"),
      revision = 1,
      name = preset.label,
      color = preset.value.encodeScheduleCategoryColor(),
      sortOrder = index,
    )
  }
  val studyCategory = colorPreviewCategories[0]
  val lifeCategory = colorPreviewCategories[1]

  /** 创建符合领域 identity 的精简待办；mock 不模拟远端版本推进。 */
  fun todo(
    suffix: String,
    title: String,
    description: String,
    categoryId: CategoryId,
    timing: ScheduleTiming,
    recurrence: RecurrenceRule? = null,
    reminder: ScheduleReminder? = null,
    todoState: ScheduleTodoState = ScheduleTodoState.PENDING,
    updatedAt: Instant = now,
    linkedToCourse: Boolean = false,
  ): Schedule = Schedule(
    id = ScheduleId("019c7f00-0000-7000-8000-000000000$suffix"),
    revision = 1,
    title = title,
    description = description,
    categoryId = categoryId,
    timing = timing,
    recurrence = recurrence,
    reminder = reminder,
    todoState = todoState,
    linkedToCourse = linkedToCourse,
    createdAt = now - 10.days,
    updatedAt = updatedAt,
  )

  /**
   * 为每套候选配色生成一条课表时间段，集中放在 3 月 7 日和 3 月 8 日。
   *
   * 两天交错分配并按一小时间隔排开，避免工作日课程和其他配色预览互相遮挡。
   */
  val colorPreviewSchedules = ScheduleCategoryColorPresets.mapIndexed { index, preset ->
    val date = courseWeekStart.plusDays(5 + index % 2)
    val startHour = 7 + index / 2
    todo(
      suffix = (20 + index).toString().padStart(3, '0'),
      title = "配色预览：${preset.label}",
      description = "分组管理候选色 ${index + 1}/${ScheduleCategoryColorPresets.size}",
      categoryId = colorPreviewCategories[index].id,
      timing = ScheduleTiming.Timed(
        start = MinuteTimeDate(date, startHour, 0),
        durationMinutes = 60,
        timeZoneId = SHANGHAI_TIME_ZONE,
      ),
      linkedToCourse = true,
    )
  }

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
        reminder = ScheduleReminder(offsetMinutes = 30),
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
        suffix = "010",
        title = "${COURSE_OVERLAP_MOCK_TITLE_PREFIX}项目联调",
        description = "3 月 3 日 14:00—17:00，作为三重叠的长时间段",
        categoryId = studyCategory.id,
        timing = ScheduleTiming.Timed(
          start = MinuteTimeDate(overlapAfternoonDate, 14, 0),
          durationMinutes = 180,
          timeZoneId = SHANGHAI_TIME_ZONE,
        ),
      ),
      todo(
        suffix = "011",
        title = "${COURSE_OVERLAP_MOCK_TITLE_PREFIX}UI 走查",
        description = "3 月 3 日 14:30—16:30，用于左右切换详情",
        categoryId = studyCategory.id,
        timing = ScheduleTiming.Timed(
          start = MinuteTimeDate(overlapAfternoonDate, 14, 30),
          durationMinutes = 120,
          timeZoneId = SHANGHAI_TIME_ZONE,
        ),
      ),
      todo(
        suffix = "012",
        title = "${COURSE_OVERLAP_MOCK_TITLE_PREFIX}接口验收",
        description = "3 月 3 日 15:00—17:30，与前两条形成三重叠",
        categoryId = studyCategory.id,
        timing = ScheduleTiming.Timed(
          start = MinuteTimeDate(overlapAfternoonDate, 15, 0),
          durationMinutes = 150,
          timeZoneId = SHANGHAI_TIME_ZONE,
        ),
      ),
      todo(
        suffix = "009",
        title = "时间点：领取实验材料",
        description = "3 月 2 日的普通时间点，用于检查课表最小高度和显示优先级",
        categoryId = studyCategory.id,
        timing = ScheduleTiming.Deadline(pointDue, SHANGHAI_TIME_ZONE),
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
        suffix = "008",
        title = "全天：项目验收日",
        description = "3 月开学周的全天事项，用于检查课表整列背景",
        categoryId = studyCategory.id,
        timing = ScheduleTiming.AllDay(allDayDate),
      ),
      todo(
        suffix = "007",
        title = "已完成：整理资料",
        description = "两天前完成，仍在七天展示窗口内",
        categoryId = lifeCategory.id,
        timing = ScheduleTiming.Unscheduled,
        todoState = ScheduleTodoState.COMPLETED,
        updatedAt = now - 2.days,
      ),
    ) + colorPreviewSchedules,
    occurrenceAdjustments = emptyList(),
    categories = colorPreviewCategories,
    status = ScheduleRepositoryStatus.Ready(pendingCount = 0, hasPendingDeletes = false),
    accountId = PREVIEW_ACCOUNT_ID,
  )
}

/** Android 课表验收时需要自动关联的下午重叠日程标题前缀。 */
private const val COURSE_OVERLAP_MOCK_TITLE_PREFIX = "课表重叠："
