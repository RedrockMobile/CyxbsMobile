package com.cyxbs.pages.course.view.frame

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import com.cyxbs.components.config.time.TodayNoEffect
import com.cyxbs.pages.course.view.week.CourseWeekCompose

/**
 * 带有整学期的课表 UI 框架
 *
 * @author 985892345
 * @date 2025/2/16
 */
@Stable
abstract class CourseSemesterFrame : CourseFrame() {

  override val maxPage: Int
    get() = super.maxPage + 1 // 最大显示页数，添加整学期页

  override val initialPage: Int
    get() {
      val page = beginDate?.daysUntil(TodayNoEffect)?.div(7)?.and(1)
        ?.coerceAtLeast(0) ?: 0
      return if (page >= maxPage) 0 else page
    }

  @Composable
  override fun CoursePageContent(pagerScope: PagerScope, page: Int) {
    val date = beginDate?.plusWeeks(page - 1)
      ?.weekFinalDate?.plusDays(timeline.beginDayOfWeek.ordinal)
    Column {
      CourseWeekCompose(
        weekBeginDate = if (page == 0) null else date, // 整学期页不显示日期
        beginDayOfWeek = timeline.beginDayOfWeek,
      )
      super.CoursePageContent(pagerScope, page)
    }
  }
}