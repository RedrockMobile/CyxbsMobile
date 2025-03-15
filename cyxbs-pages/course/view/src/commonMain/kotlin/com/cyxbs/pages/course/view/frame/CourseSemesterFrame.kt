package com.cyxbs.pages.course.view.frame

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import com.cyxbs.components.config.time.Date
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
      super.CoursePageContent(pagerScope, page)
    }
  }
}