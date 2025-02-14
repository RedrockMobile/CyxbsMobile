package com.cyxbs.pages.course.view.timeline.data

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
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
  val color: Color
  val startTime: MinuteTime
  val endTime: MinuteTime
  val nowWeight: Float
  val initialWeight: Float

  /**
   * 时间轴存在越过24点的情况，添加该变量用于表示是否存在明天的时间段
   */
  val hasTomorrow: Boolean

  // 开始时间的分钟数，如果存在明天的时间段，则值会大于 24 * 60
  val startTimeInt: Int
    get() = if (!hasTomorrow || startTime > endTime) {
      startTime.hour * 60 + startTime.minute
    } else {
      startTime.hour * 60 + startTime.minute + 24 * 60
    }

  val endTimeInt: Int
    get() = if (!hasTomorrow) {
      endTime.hour * 60 + endTime.minute
    } else {
      endTime.hour * 60 + endTime.minute + 24 * 60
    }

  fun copyData(): CourseTimelineData

  @Composable
  fun ColumnScope.Content()
}