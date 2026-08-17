package com.cyxbs.pages.schedule.ui.course

import androidx.compose.runtime.*
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.course.view.AbstractCourseFrame
import com.cyxbs.pages.course.view.decoration.CoursePageDecoration
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
import com.cyxbs.pages.course.view.item.ItemHierarchyWhatTime
import com.cyxbs.pages.schedule.data.repository.v2.ScheduleRepositoryProvider
import com.cyxbs.pages.schedule.domain.model.*
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepository
import com.cyxbs.pages.schedule.ui.edit.*
import com.cyxbs.pages.schedule.ui.model.*
import com.cyxbs.pages.schedule.ui.timeline.FULL_DAY_MINUTES
import com.cyxbs.pages.schedule.ui.timeline.timelineSchedulesForDate
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.DayOfWeek

/**
 * 在课表之上观察并渲染 Schedule v2 实例的装饰层。
 *
 * 该层与主页面共享进程级 local-first 仓库，只观察当前课表教学周并在快照、开学日期或可见页变化时重新展开；
 * 仅定时且 ACTIVE 的 occurrence 会进入课表，重复例外仍由业务引擎合并。弹窗命令异步写仓库，随后由 Flow
 * 反向刷新 itemHierarchy，装饰层不持有独立业务副本。
 */
class SchedulePageDecoration(
  private val courseFrame: AbstractCourseFrame,
  private val repository: ScheduleRepository = ScheduleRepositoryProvider.repository,
) : CoursePageDecoration<ScheduleCourseItem>() {
  /** 弹窗会话定位：同时保留实例覆盖值、系列原值与所属课表页，防止跨页重复挂载弹窗。 */
  private data class Editing(val occurrence: ScheduleUiOccurrence, val schedule: Schedule, val page: Int)
  private val editing = MutableStateFlow<Editing?>(null)
  private val initStarted = atomic(false)

  /**
   * 初始化仓库并持续把“快照 + 当前学期起始日 + 可见页”映射为可见页条目。
   *
   * `snapshotFlow` 让 PagerState 的页面切换立即触发展开；`page == 0` 是整学期页，不能按周回推。
   * mapLatest 会取消过期窗口计算，避免跨周污染当前课表。
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun observe() {
    if (initStarted.compareAndSet(false, true)) repository.initialize()
    combine(
      repository.snapshot,
      courseFrame.beginDate,
      snapshotFlow { courseFrame.pagerState.currentPage },
    ) { snapshot, begin, visiblePage -> Triple(snapshot, begin, visiblePage) }
      .mapLatest { (snapshot, begin, visiblePage) ->
        val weekStart = scheduleWeekStart(begin, courseFrame.timeline.beginDayOfWeek, visiblePage)
          ?: return@mapLatest itemHierarchy.reset(emptyList())
        val startInclusive = MinuteTimeDate(weekStart, 0, 0)
        val endExclusive = MinuteTimeDate(weekStart.plusDays(7), 0, 0)
        val items = snapshot.occurrencesInRange(startInclusive, endExclusive).flatMap { occurrence ->
          if (occurrence.status != OccurrenceStatus.ACTIVE) return@flatMap emptyList()
          // 课表网格只接受定时区间；全天、截止和未排期仍由各自页面展示，不能铺满课程格子。
          if (occurrence.timing !is ScheduleTiming.Timed) return@flatMap emptyList()
          (0..6).mapNotNull { dayOffset ->
            val date = weekStart.plusDays(dayOffset)
            val slice = timelineSchedulesForDate(listOf(occurrence), date).singleOrNull() ?: return@mapNotNull null
            val page = courseFrame.getPage(date) ?: return@mapNotNull null
            if (page != visiblePage || !slice.isInterval) return@mapNotNull null
            ScheduleItemWhatTime(
              occurrence = occurrence,
              date = date,
              start = minuteTimeForCourse(slice.startMin, isEnd = false),
              end = minuteTimeForCourse(slice.endMin, isEnd = true),
              page = page,
            ) {
              val schedule = snapshot.schedules.first { it.id == occurrence.scheduleId }
              editing.value = Editing(occurrence, schedule, page)
            }
          }
        }
        itemHierarchy.reset(items)
      }.collect()
  }

  @Composable override fun CoursePageContent() {
    super.CoursePageContent()
    LaunchedEffect(Unit) { observe() }
    val current by editing.collectAsState()
    val value = current
    if (value != null && value.page == coursePage.page) {
      val scope = rememberCoroutineScope()
      EditScheduleDialog(
        show = true,
        editSchedule = value.schedule,
        editOccurrence = value.occurrence.let {
          ScheduleOccurrence(
            it.scheduleId, it.recurrenceId, it.timing, it.title, it.description,
            it.categoryId, it.reminders, it.status, it.isOverridden,
          )
        },
        recurrenceId = value.occurrence.recurrenceId,
        onDismiss = { editing.value = null },
        onConfirm = { state, editScope -> scope.launch {
          repository.applyScheduleEdit(
            state, editScope, value.occurrence.recurrenceId,
            ScheduleRepositoryProvider.idGenerators, ScheduleRepositoryProvider.clock,
          )
        } },
        onDelete = { editScope -> scope.launch {
          repository.applyScheduleDelete(
            value.schedule.id, editScope, value.occurrence.recurrenceId, ScheduleRepositoryProvider.clock,
          )
        } },
      )
    }
  }
}

/**
 * 计算教学周页的第一显示日。
 *
 * `page == 0` 是整学期页，因此返回 null；教学周从 1 开始，首周以 [beginDate] 所在周并按
 * [beginDayOfWeek] 对齐，后续周按 `page - 1` 推进。这必须与 [AbstractCourseFrame.HomeCoursePageContent]
 * 使用的页面合同保持一致。
 */
internal fun scheduleWeekStart(beginDate: Date?, beginDayOfWeek: DayOfWeek, page: Int): Date? {
  if (beginDate == null || page < 1) return null
  return beginDate.plusWeeks(page - 1).weekBeginDate.plusDays(beginDayOfWeek.ordinal)
}

/**
 * 课表条目稳定身份：同一 occurrence 的跨日切片必须以实际日期区分，避免 HashMap 层级合并它们。
 */
internal data class ScheduleCourseItemIdentity(
  val scheduleId: ScheduleId,
  val recurrenceId: RecurrenceId?,
  val date: Date,
  val start: MinuteTime,
  val end: MinuteTime,
  val page: Int,
)

/**
 * 将日切片分钟映射为 CourseItem 可表达的时分。
 *
 * 查询与投影层保留 `endMin = 1440` 的半开合同；课程组件没有 24:00 时仅在最终适配点把它映射为
 * 当天最后一个可表示分钟，
 * 避免向上游传播伪结束值。
 */
private fun minuteTimeForCourse(minute: Int, isEnd: Boolean): MinuteTime {
  val representable = if (isEnd && minute >= FULL_DAY_MINUTES) FULL_DAY_MINUTES - 1 else minute.coerceIn(0, FULL_DAY_MINUTES - 1)
  return MinuteTime(representable / 60, representable % 60)
}

/**
 * 课表层使用的实例定位描述；相等性以 [identity] 包含稳定 occurrence identity、日期片段、时段与页码，
 * 供层级 diff 正确识别跨日实例。
 */
private class ScheduleItemWhatTime(
  val occurrence: ScheduleUiOccurrence,
  val date: Date,
  val start: MinuteTime,
  val end: MinuteTime,
  val page: Int,
  val onClick: () -> Unit,
) : ItemHierarchyWhatTime<ScheduleCourseItem>() {
  private val identity = ScheduleCourseItemIdentity(
    occurrence.scheduleId, occurrence.recurrenceId, date, start, end, page,
  )

  override val now = MutableStateFlow<CourseItemWhatTime.Fixed>(CourseItemWhatTime.Fixed(
    page, date.dayOfWeek, start, end,
  ))
  override fun createItem(coroutineScope: CoroutineScope) = ScheduleCourseItem(
    this, coroutineScope, occurrence, occurrence.title, occurrence.description, onClick,
  )
  override fun equals(other: Any?) = other is ScheduleItemWhatTime && other.identity == identity
  override fun hashCode() = identity.hashCode()
}
