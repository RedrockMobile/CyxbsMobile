package com.cyxbs.pages.course.view.timeline.data

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.TextUnit
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import kotlinx.serialization.Serializable

/**
 * .
 *
 * @author 985892345
 * 2024/3/11 19:19
 */
@Stable
@Serializable
sealed interface CourseTimelineData {
  val optionText: String
  val fontSize: TextUnit
  val startTime: MinuteTime
  val endTime: MinuteTime
  val nowWeight: Float
  val initialWeight: Float

  @Composable
  fun ColumnScope.Content()
}

fun CourseTimelineData.getStartTimeInt(timeline: CourseTimeline): Int {
  return if (startTime >= timeline.startMinuteTime) {
    startTime.hour * 60 + startTime.minute
  } else {
    startTime.hour * 60 + startTime.minute + 24 * 60
  }
}

fun CourseTimelineData.getEndTimeInt(timeline: CourseTimeline): Int {
  return if (endTime > timeline.startMinuteTime) {
    endTime.hour * 60 + endTime.minute
  } else {
    endTime.hour * 60 + endTime.minute + 24 * 60
  }
}