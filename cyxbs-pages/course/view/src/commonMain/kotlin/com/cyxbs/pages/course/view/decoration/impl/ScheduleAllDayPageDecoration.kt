package com.cyxbs.pages.course.view.decoration.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.utils.compose.color
import com.cyxbs.components.utils.compose.sharePointerInput
import com.cyxbs.pages.course.view.AbstractCourseFrame
import com.cyxbs.pages.course.view.item.impl.CourseScheduleItem
import com.cyxbs.pages.course.view.item.impl.PlatformScheduleItemFactory
import com.cyxbs.pages.course.view.item.impl.ScheduleAllDayDecorationItem
import com.cyxbs.pages.course.view.item.impl.ScheduleAllDayItem
import com.cyxbs.pages.course.view.item.impl.defaultScheduleTodoBackgroundColor
import com.cyxbs.pages.course.view.item.impl.defaultScheduleTodoContentColor
import com.cyxbs.pages.course.view.item.CourseItemDarkContentColor
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceKind
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceColor
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceTiming
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 把全天日程绘制为日期列背景。
 *
 * 该 Decoration 不向 ItemHierarchy 写入数据，必须放在 Manager 最后，因此普通课程、事务、时间段和
 * 截止时间都绘制在它上方；短按打开详情，长按事件继续共享给底层创建事务手势。
 */
class ScheduleAllDayPageDecoration(
  courseFrame: AbstractCourseFrame,
  coroutineScope: CoroutineScope,
  private val platformItemFactory: PlatformScheduleItemFactory,
) : SchedulePageDecoration<CourseScheduleItem>(courseFrame, coroutineScope) {

  private val items = scheduleRangeFlow.map { range ->
    if (range == null) emptyList() else projectAllDayRange(range)
  }.stateIn(
    scope = coroutineScope,
    started = SharingStarted.Eagerly,
    initialValue = emptyList(),
  )

  @Composable
  override fun CoursePageContent() {
    val value by items.collectAsState()
    val page = coursePage.page
    val pageItems = remember(value, page, platformItemFactory) {
      value
        .filter { it.page == page }
        .map { ScheduleAllDayItem(it, platformItemFactory) }
    }
    ScheduleAllDayBackground(
      items = pageItems,
    )
  }

  /** 只转换当前 Decoration 需要的全天 occurrence，不创建伪造的 00:00—23:59 Course Item。 */
  private fun projectAllDayRange(
    range: ScheduleRange,
  ): List<ScheduleAllDayDecorationItem> = buildList {
    val dayCount = range.startDate.daysUntil(range.endDateExclusive)
    range.occurrences.forEach { occurrence ->
      if (occurrence.kind != ScheduleOccurrenceKind.TODO) return@forEach
      val timing = occurrence.timing as? ScheduleOccurrenceTiming.AllDay ?: return@forEach
      repeat(dayCount) { dayIndex ->
        val date = range.startDate.plusDays(dayIndex)
        if (date < timing.startDate || date >= timing.startDate.plusDays(timing.durationDays)) {
          return@repeat
        }
        val page = courseFrame.getPage(date) ?: return@repeat
        add(
          ScheduleAllDayDecorationItem(
            stableId = "${occurrence.identity}|$date|$page|all-day",
            occurrence = occurrence,
            page = page,
            // page 已区分学期中的第几周；绘制层只需要当前周内的 0..6 列索引。
            dayIndex = dayIndex % DAYS_PER_WEEK,
            title = occurrence.title,
          ),
        )
      }
    }
  }
}

/**
 * 全天背景与普通课表 Item 使用相同的水平留白和圆角，但自身不占据任何时间区间。
 */
@Composable
private fun ScheduleAllDayBackground(
  items: List<ScheduleAllDayItem>,
) {
  val grouped = remember(items) { items.groupBy { it.data.dayIndex } }
  Row(
    modifier = Modifier
      .fillMaxSize()
      .sharePointerInput(true),
  ) {
    repeat(7) { dayIndex ->
      val dayItems = grouped[dayIndex].orEmpty()
      val content: @Composable ((() -> Unit)?) -> Unit = { onClick ->
        AllDayColumn(
          modifier = Modifier.weight(1F),
          items = dayItems,
          onClick = onClick,
        )
      }
      val first = dayItems.firstOrNull()
      if (first == null) content(null)
      else first.platform.AllDayItemContentWrapper(content)
    }
  }
}

/** 绘制一天的全天背景和标题列表；[onClick] 完全由具体平台提供。 */
@Composable
private fun AllDayColumn(
  modifier: Modifier,
  items: List<ScheduleAllDayItem>,
  onClick: (() -> Unit)?,
) {
  val defaultBackground = defaultScheduleTodoBackgroundColor()
  val defaultContent = defaultScheduleTodoContentColor()
  val categoryColor = items.firstOrNull()?.data?.occurrence?.categoryColor
  val backgroundColor = categoryColor?.backgroundColor()
    ?: defaultBackground
  val contentColor = categoryColor?.contentColor() ?: defaultContent
  Box(
    modifier = modifier
      .fillMaxHeight()
      .then(if (onClick == null) Modifier else Modifier.allDayTap(onClick))
      .padding(horizontal = 1.dp)
      .background(
        color = if (items.isEmpty()) Color.Transparent else backgroundColor,
        shape = RoundedCornerShape(8.dp),
      ),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 4.dp)) {
      items.forEach { item ->
        key(item.data.stableId) {
          Text(
            text = item.data.title,
            // 全天区域整列共用第一条日程的底色，文字也使用同一配对色以保证对比度。
            color = contentColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
          )
        }
      }
    }
  }
}

/** 按当前主题解析 Schedule API 中已经校验过的全天背景色。 */
@Composable
private fun ScheduleOccurrenceColor.backgroundColor(): Color = Color(
  if (MaterialTheme.colors.isLight) lightBackgroundArgb.toInt() else darkBackgroundArgb.toInt(),
)

/** 按当前主题解析与全天背景配对的字体色。 */
@Composable
private fun ScheduleOccurrenceColor.contentColor(): Color =
  if (MaterialTheme.colors.isLight) lightContentArgb.toInt().color() else CourseItemDarkContentColor

/**
 * 只在短按抬手时消费事件：DOWN/MOVE 仍传给长按创建事务，避免全天背景截断长按手势。
 */
private fun Modifier.allDayTap(onTap: () -> Unit): Modifier =
  pointerInput(onTap) {
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
      var isTap = true
      var upChange: PointerInputChange? = null
      while (upChange == null) {
        val change = awaitPointerEvent(PointerEventPass.Initial)
          .changes.firstOrNull { it.id == down.id } ?: break
        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
          isTap = false
        }
        if (change.changedToUpIgnoreConsumed()) upChange = change
      }
      if (
        isTap && upChange != null &&
        upChange.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
      ) {
        upChange.consume()
        onTap()
      }
    }
  }
