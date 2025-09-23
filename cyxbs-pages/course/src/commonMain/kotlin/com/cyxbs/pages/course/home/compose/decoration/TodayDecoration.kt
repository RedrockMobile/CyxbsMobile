package com.cyxbs.pages.course.home.compose.decoration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.Today
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.compose.rememberDerivedStateOfStructure
import com.cyxbs.pages.course.view.page.CoursePageDecoration

/**
 * 显示课表今日背景
 *
 * @author 985892345
 * @date 2025/9/23
 */
@Stable
class TodayDecoration(
  val weekBeginDate: Date?,
) : CoursePageDecoration {
  @Composable
  override fun OuterCoursePage(
    scrollPaddingValues: PaddingValues,
    timelineWidth: Dp,
    content: @Composable () -> Unit
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      val todayIndex by rememberDerivedStateOfStructure(weekBeginDate) {
        weekBeginDate?.daysUntil(Today) ?: Today.dayOfWeek.ordinal
      }
      if (todayIndex in 0..6) {
        Spacer(modifier = Modifier.layout { measurable, constraints ->
          val startPadding =
            timelineWidth.roundToPx() + scrollPaddingValues.calculateStartPadding(layoutDirection)
              .roundToPx()
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
      content()
    }
  }
}