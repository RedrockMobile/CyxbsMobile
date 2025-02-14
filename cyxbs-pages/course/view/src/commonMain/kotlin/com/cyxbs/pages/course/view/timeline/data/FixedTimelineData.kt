package com.cyxbs.pages.course.view.timeline.data

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.serializable.ColorSerializable
import com.cyxbs.components.config.serializable.TextUnitSerializable
import com.cyxbs.components.config.time.MinuteTime
import kotlinx.serialization.Serializable

/**
 * .
 *
 * @author 985892345
 * 2024/3/11 19:19
 */
@Serializable
data class FixedTimelineData(
  val text: String,
  override val optionText: String,
  override val startTime: MinuteTime,
  override val endTime: MinuteTime,
  val weight: Float = 1F,
  @Serializable(TextUnitSerializable::class)
  override val fontSize: TextUnit = 12.sp,
  @Serializable(ColorSerializable::class)
  override val color: Color = Color.Unspecified,
  override val hasTomorrow: Boolean,
) : CourseTimelineData {
  override val nowWeight: Float
    get() = weight
  override val initialWeight: Float
    get() = weight

  override fun copyData(): CourseTimelineData {
    return copy()
  }

  @Composable
  override fun ColumnScope.Content() {
    Layout(
      modifier = Modifier.weight(nowWeight).fillMaxWidth(),
      content = {
        Text(
          text = text,
          textAlign = TextAlign.Center,
          fontSize = fontSize,
          color = color,
          overflow = TextOverflow.Visible
        )
      },
      measurePolicy = TimelineMeasurePolicy,
    )
  }

  companion object {
    private val TimelineMeasurePolicy = MeasurePolicy { measurables, constraints ->
      val placeable = measurables[0].measure(
        constraints.copy(
          minWidth = 0,
          minHeight = 0,
          maxHeight = Constraints.Infinity
        )
      )
      layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.placeRelative(
          x = (constraints.maxWidth - placeable.width) / 2,
          y = (constraints.maxHeight - placeable.height) / 2
        )
      }
    }
  }
}