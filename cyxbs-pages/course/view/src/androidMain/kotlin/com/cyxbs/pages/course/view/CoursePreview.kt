package com.cyxbs.pages.course.view

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.cyxbs.components.config.compose.theme.AppTheme
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.TodayNoEffect
import com.cyxbs.pages.course.view.data.CourseDataProvider
import com.cyxbs.pages.course.view.data.CourseDataProviderGroup
import com.cyxbs.pages.course.view.frame.CourseBottomSheetFrame
import com.cyxbs.pages.course.view.item.CourseItem

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
    CoursePreviewFrame.Content()
  }
}

private object CoursePreviewFrame : CourseBottomSheetFrame() {

  override val beginDate: Date = TodayNoEffect.weekBeginDate
  override val providerGroup: CourseDataProviderGroup = CourseDataProviderGroup(
    CoursePreviewDataProvider,
  )
}

private object CoursePreviewDataProvider : CourseDataProvider() {
  override fun compare(a: CourseItem, b: CourseItem): Int {
    return a.beginTime.compareTo(b.beginTime)
  }
}