package com.cyxbs.pages.course.view.timeline.data

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.pages.course.api.CourseUtils
import com.cyxbs.pages.course.view.timeline.DefaultTimelineTextColor
import com.cyxbs.pages.course.view.timeline.DefaultTimelineTextDarkColor
import kotlinx.serialization.Serializable

/**
 * .
 *
 * @author 985892345
 * 2024/3/14 16:59
 */
@Serializable
data class LessonTimelineData(
  val lesson: Int,
) : CourseTimelineData {

  override val optionText: String = "第${lesson}节"

  override val startTime: MinuteTime = CourseUtils.getStartMinuteTime(lesson)

  override val endTime: MinuteTime = CourseUtils.getEndMinuteTime(lesson)

  override val fontSize: TextUnit
    get() = 12.sp

  override val nowWeight: Float
    get() = 1F
  override val initialWeight: Float
    get() = 1F

  @Composable
  override fun ColumnScope.Content() {
    Layout(
      modifier = Modifier.weight(nowWeight).fillMaxWidth(),
      content = {
        Text(
          text = lesson.toString(),
          textAlign = TextAlign.Center,
          fontSize = fontSize,
          color = DefaultTimelineTextColor.dark(DefaultTimelineTextDarkColor),
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