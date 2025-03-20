package com.cyxbs.pages.course.view.frame

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.Today
import com.cyxbs.components.config.time.TodayNoEffect
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.compose.derivedStateOfStructure
import com.cyxbs.pages.course.view.data.CourseWeekDataPool
import com.cyxbs.pages.course.view.page.CoursePageCompose
import com.cyxbs.pages.course.view.page.CoursePageDecoration
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import com.cyxbs.pages.course.view.week.CourseWeekCompose

/**
 * 带有整学期的课表 UI 框架
 *
 * @author 985892345
 * @date 2025/2/16
 */
@Stable
abstract class CourseSemesterFrame : CourseFrame() {

  // 课表起始日期，如果为 null 则不会显示号数
  abstract val beginDate: Date?

  override val maxPage: Int
    get() = 22 // 最大显示页数，添加整学期页

  // 课表初始页，按 beginDate 自动计算(如果有值)，超出 maxPage 时默认显示第一页
  override val initialPage: Int
    get() {
      val beginDate = beginDate ?: return 0
      val page = (beginDate.daysUntil(TodayNoEffect) / 7 + 1).coerceAtLeast(0)
      return if (page >= maxPage) 0 else page
    }

  @Composable
  override fun CoursePageContent(pagerScope: PagerScope, page: Int) {
    val date = beginDate?.plusWeeks(page - 1)
      ?.weekBeginDate?.plusDays(timeline.beginDayOfWeek.ordinal)
    Column {
      CourseWeekCompose(
        weekBeginDate = if (page == 0) null else date, // 整学期页不显示日期
        beginDayOfWeek = timeline.beginDayOfWeek,
      )
      CoursePageCompose(
        timeline = timeline,
        weekDataPools = providerGroup.getWeekDataPool(page),
        decorations = listOf(TodayDecoration(weekBeginDate = if (page == 0) null else date)),
      )
    }
  }

  protected class TodayDecoration(
    val weekBeginDate: Date?,
  ) : CoursePageDecoration {
    @Composable
    override fun OuterCoursePageBottom(
      timeline: CourseTimeline,
      verticalScrollState: ScrollState,
      weekDataPools: List<CourseWeekDataPool>,
      scrollPaddingValues: PaddingValues,
      timelineWidth: Dp,
    ) {
      val todayIndex by derivedStateOfStructure {
        weekBeginDate?.daysUntil(Today) ?: Today.dayOfWeek.ordinal
      }
      if (todayIndex in 0..6) {
        Spacer(modifier = Modifier.layout { measurable, constraints ->
          val startPadding = timelineWidth.roundToPx() + scrollPaddingValues.calculateStartPadding(layoutDirection).roundToPx()
          val endPadding = scrollPaddingValues.calculateEndPadding(layoutDirection).roundToPx()
          val placeable = measurable.measure(
            Constraints.fixed(
              width = (constraints.maxWidth - startPadding - endPadding) / 7,
              height = constraints.maxHeight,
            )
          )
          layout(placeable.width, placeable.height) {
            placeable.placeRelative(todayIndex * placeable.width + startPadding, 0)
          }
        }.background(0x93E8F0FC.dark(0x26000101)))
      }
    }
  }
}