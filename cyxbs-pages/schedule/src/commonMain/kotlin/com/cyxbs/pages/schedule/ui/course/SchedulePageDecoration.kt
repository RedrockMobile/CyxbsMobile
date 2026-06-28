package com.cyxbs.pages.schedule.ui.course

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.pages.course.view.AbstractCourseFrame
import com.cyxbs.pages.course.view.decoration.CoursePageDecoration
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
import com.cyxbs.pages.course.view.item.ItemHierarchyWhatTime
import com.cyxbs.pages.schedule.data.model.ScheduleEntity
import com.cyxbs.pages.schedule.data.model.ScheduleOccurrences
import com.cyxbs.pages.schedule.data.repository.ScheduleSyncRepository
import com.cyxbs.pages.schedule.ui.edit.EditScheduleDialog
import com.cyxbs.pages.schedule.ui.edit.applyScheduleDelete
import com.cyxbs.pages.schedule.ui.edit.applyScheduleEdit
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch

/**
 * 把日程渲染到课表上（与事务 [com.cyxbs.pages.course.view.decoration.impl.AffairPageDecoration] **并存**，
 * 注册在 `CoursePageDecorationManager` 里，不改动 affair）。
 *
 * 流程：观察 [repository] 的 todos + 课表 beginDate → 在可见周范围内把每条日程经 [ScheduleOccurrences]
 * 展开成 occurrence → 每个 occurrence 落到对应「页(周)·星期·起止时刻」→ `itemHierarchy.reset`。
 * 点击某条 item → 打开统一 [EditScheduleDialog]（与邮子清单同一套，三态路由共用 applyScheduleEdit/Delete）。
 *
 * v1 仅渲染「时间段型」（有开始时间）的 occurrence；截止型/未排期不画到网格上（在邮子清单时间轴里看）。
 */
class SchedulePageDecoration(
  private val courseFrame: AbstractCourseFrame,
  private val repository: ScheduleSyncRepository = ScheduleSyncRepository(),
) : CoursePageDecoration<ScheduleCourseItem>() {

  /** 当前被点击待编辑的 occurrence；page 用于只在其所在页渲染一次弹窗（HorizontalPager 会并存多页）。 */
  private data class Editing(val todo: ScheduleEntity, val date: Date, val page: Int)

  private val editing = MutableStateFlow<Editing?>(null)
  private val initStarted = atomic(false)

  private suspend fun observe() {
    if (initStarted.compareAndSet(false, true)) {
      runCatching { repository.initialize() }
    }
    combine(repository.todos, courseFrame.beginDate) { todos, begin -> todos to begin }
      .mapLatest { (todos, begin) ->
        if (begin == null) {
          itemHierarchy.reset(emptyList())
          return@mapLatest
        }
        // 课表真实起始日 = 开学周一 + 时间轴起始星期偏移；范围覆盖整学期可见周。
        val realBegin = begin.weekBeginDate.plusDays(courseFrame.timeline.beginDayOfWeek.ordinal)
        val rangeEnd = realBegin.plusWeeks(courseFrame.maxWeek)
        val list = ArrayList<ScheduleItemWhatTime>()
        todos.forEach { todo ->
          ScheduleOccurrences.expandInRange(todo, realBegin, rangeEnd).forEach { occ ->
            val start = occ.start ?: return@forEach // v1：仅时间段型上网格
            val page = courseFrame.getPage(occ.date) ?: return@forEach
            list += ScheduleItemWhatTime(
              todo = todo,
              recurrenceId = occ.recurrenceId,
              date = occ.date,
              start = start,
              end = occ.end,
              page = page,
              onClick = { editing.value = Editing(todo, occ.recurrenceId, page) },
            )
          }
        }
        itemHierarchy.reset(list)
      }.collect()
  }

  @Composable
  override fun CoursePageContent() {
    super.CoursePageContent()
    // 与 affair 一致：每页都起观察，保证滚动时始终有存活的收集者。
    LaunchedEffect(Unit) { observe() }

    val current by editing.collectAsState()
    val e = current
    if (e != null && e.page == coursePage.page) {
      val scope = rememberCoroutineScope()
      EditScheduleDialog(
        show = true,
        editSchedule = e.todo,
        occurrenceDate = e.date,
        onDismiss = { editing.value = null },
        onConfirm = { state, sc -> scope.launch { repository.applyScheduleEdit(state, sc, e.date) } },
        onDelete = { sc -> scope.launch { repository.applyScheduleDelete(e.todo.todoId, sc, e.date) } },
      )
    }
  }
}

/**
 * 一个 occurrence 在课表上的位置 + 懒加载 item。equals/hashCode 以 (todoId, 原锚点, 起止, 页, 日期) 为准，
 * 保证 [com.cyxbs.pages.course.view.item.CourseItemHierarchy] 在每次 reset 时能把同一 occurrence 视作同一项。
 */
private class ScheduleItemWhatTime(
  val todo: ScheduleEntity,
  val recurrenceId: Date,
  val date: Date,
  val start: MinuteTime,
  val end: MinuteTime,
  val page: Int,
  val onClick: () -> Unit,
) : ItemHierarchyWhatTime<ScheduleCourseItem>() {

  override val now: MutableStateFlow<CourseItemWhatTime.Fixed> = MutableStateFlow(
    CourseItemWhatTime.Fixed(
      page = page,
      dayOfWeek = date.dayOfWeek,
      beginTime = start,
      finalTime = end,
    )
  )

  override fun createItem(coroutineScope: CoroutineScope): ScheduleCourseItem =
    ScheduleCourseItem(
      whatTime = this,
      coroutineScope = coroutineScope,
      todo = todo,
      topText = todo.title,
      bottomText = todo.detail,
      onClick = onClick,
    )

  override fun equals(other: Any?): Boolean =
    other is ScheduleItemWhatTime &&
      other.todo.todoId == todo.todoId &&
      other.recurrenceId == recurrenceId &&
      other.date == date &&
      other.start == start &&
      other.end == end &&
      other.page == page

  override fun hashCode(): Int {
    var r = todo.todoId.hashCode()
    r = 31 * r + recurrenceId.hashCode()
    r = 31 * r + date.hashCode()
    r = 31 * r + start.hashCode()
    r = 31 * r + end.hashCode()
    r = 31 * r + page
    return r
  }
}
