package com.cyxbs.pages.course.view.timeline.data

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.TextUnit
import com.cyxbs.components.config.time.MinuteTime
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
  val initialWeight: Float // 需要是一个不变的固定值

  @Composable
  fun ColumnScope.Content()
}
