package com.cyxbs.pages.course.home.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.config.time.Today
import com.cyxbs.components.config.time.TodayNoEffect
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.compose.rememberDerivedStateOfStructure
import com.cyxbs.pages.course.view.decoration.CoursePageDecoration
import com.cyxbs.pages.course.view.page.CoursePageCompose
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import com.cyxbs.pages.course.view.week.CourseWeekCompose
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.DayOfWeek

/**
 * 课表框架
 *
 * @author 985892345
 * @date 2025/9/22
 */
@Stable
abstract class AbstractHomeCourseFrame {

  // 课表时间轴
  open val timeline: CourseTimeline = CourseTimeline()

  // 课表起始日期，如果为 null 则不会显示号数
  open var beginDate: Date? by mutableStateOf(SchoolCalendar.getFirstMonDay())

  // 课表 HorizontalPager 状态
  open val pagerState: PagerState by lazy {
    PagerState(initialPage) { maxPage }
  }

  // 最大显示页数，添加整学期页
  open val maxPage: Int get() = 22

  // 课表初始页，按 beginDate 自动计算(如果有值)，超出 maxPage 时默认显示第一页
  open val initialPage: Int
    get() = getPage(TodayNoEffect)

  open fun getPage(date: Date): Int {
    val realBeginDate =
      beginDate?.weekBeginDate?.plusDays(timeline.beginDayOfWeek.ordinal) ?: return 0
    val page = (realBeginDate.daysUntil(date) / 7 + 1).coerceAtLeast(0)
    return if (page >= maxPage) 0 else page
  }
}

@Composable
fun AbstractHomeCourseFrame.HomeCoursePageContent(
  page: Int,
  decorations: ImmutableList<CoursePageDecoration>,
) {
  val pageBeginDate = beginDate?.plusWeeks(page - 1)
    ?.weekBeginDate?.plusDays(timeline.beginDayOfWeek.ordinal)
  val timelineWidth = 40.dp
  val scrollPaddingValues = PaddingValues(top = 4.dp, bottom = 16.dp)
  val showShadowDayOfWeek by rememberDerivedStateOfStructure(pageBeginDate) {
    if (page == 0) Today.dayOfWeek else {
      pageBeginDate?.daysUntil(Today)?.takeIf { it in 0..6 }?.let { Today.dayOfWeek }
    }
  }
  Column(
    modifier = Modifier.drawTodayShadow(
      showShadowDayOfWeek = showShadowDayOfWeek,
      beginDayOfWeek = timeline.beginDayOfWeek,
      timelineWidth = timelineWidth,
      scrollPaddingValues = scrollPaddingValues
    )
  ) {
    CourseWeekCompose(
      weekBeginDate = if (page == 0) null else pageBeginDate, // 整学期页不显示日期
      beginDayOfWeek = timeline.beginDayOfWeek,
      showShadowDayOfWeek = showShadowDayOfWeek,
      timelineWidth = timelineWidth,
      scrollPaddingValues = scrollPaddingValues,
    )
    CoursePageCompose(
      page = page,
      timeline = timeline,
      timelineWidth = timelineWidth,
      scrollPaddingValues = scrollPaddingValues,
      decorations = decorations,
    )
  }
}

// 绘制课表今日的背景阴影
@Composable
private fun Modifier.drawTodayShadow(
  showShadowDayOfWeek: DayOfWeek?, // 星期几展示今日阴影
  beginDayOfWeek: DayOfWeek,
  timelineWidth: Dp,
  scrollPaddingValues: PaddingValues,
): Modifier {
  showShadowDayOfWeek ?: return this
  val todayIndex = (showShadowDayOfWeek.ordinal - beginDayOfWeek.ordinal + 7) % 7
  val color = 0x93E8F0FC.dark(0x26000101)
  return drawBehind {
    val paddingStart = scrollPaddingValues.calculateStartPadding(layoutDirection).toPx()
    val paddingEnd = scrollPaddingValues.calculateEndPadding(layoutDirection).toPx()
    val width = (size.width - timelineWidth.toPx() - paddingStart - paddingEnd) / 7
    val offsetX = timelineWidth.toPx() + paddingStart + todayIndex * width
    drawRoundRect(
      color = color,
      topLeft = Offset(x = offsetX, y = 0F),
      size = Size(width = width, height = 10.dp.toPx()),
      cornerRadius = CornerRadius(x = 8.dp.toPx(), y = 8.dp.toPx())
    )
    drawRect(
      color = color,
      topLeft = Offset(x = offsetX, y = 10F),
      size = Size(width = width, height = size.height - 10.dp.toPx()),
    )
  }
}