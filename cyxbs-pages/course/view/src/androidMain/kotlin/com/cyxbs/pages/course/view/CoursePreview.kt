package com.cyxbs.pages.course.view

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.cyxbs.components.config.compose.theme.AppTheme
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.TodayNoEffect
import com.cyxbs.pages.course.view.frame.CourseBottomSheetFrame
import com.cyxbs.pages.course.view.item.CourseItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.DayOfWeek

/**
 * .
 *
 * @author 985892345
 * @date 2025/2/14
 */
@Preview(showBackground = true)
@Composable
fun PreviewCourseWeekCompose() {
  AppTheme {
    CoursePreviewFrame.CourseCompose()
  }
}

private object CoursePreviewFrame : CourseBottomSheetFrame() {

  override val beginDate: Date = TodayNoEffect.weekBeginDate

  override fun getDayItems(week: Int, dayOfWeek: DayOfWeek): ImmutableList<CourseItem> {
    return persistentListOf()
  }
}