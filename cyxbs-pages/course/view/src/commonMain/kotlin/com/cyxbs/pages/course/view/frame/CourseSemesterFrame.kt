package com.cyxbs.pages.course.view.frame

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import com.cyxbs.pages.course.view.page.CoursePageCompose
import com.cyxbs.pages.course.view.week.CourseWeekCompose
import kotlinx.datetime.DayOfWeek

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
    get() = super.initialPage + 1

  @Composable
  override fun PagerScope.CoursePageContent(page: Int) {
    val scrollState = rememberScrollState()
    val date = beginDate?.plusWeeks(page - 1)
    Column {
      CourseWeekCompose(
        weekBeginDate = if (page == 0) null else date,
        beginDayOfWeek = beginDate?.dayOfWeek ?: DayOfWeek.MONDAY,
      )
      CoursePageCompose(
        timeline = timeline,
        beginDayOfWeek = beginDate?.dayOfWeek ?: DayOfWeek.MONDAY,
        verticalScrollState = scrollState,
        items = {
          getDayItems(page, it)
        }
      )
    }
  }
}