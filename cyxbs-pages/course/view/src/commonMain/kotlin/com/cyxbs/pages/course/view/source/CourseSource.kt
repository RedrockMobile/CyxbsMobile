package com.cyxbs.pages.course.view.source

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.roundToInt

/**
 * .
 *
 * @author 985892345
 * @date 2025/1/28
 */
@Stable
abstract class CourseSource {

  abstract fun getItems(date: Date, timeline: CourseTimeline): ImmutableList<CourseSourceItem>

  /**
   * 只关心高度，宽度固定为一天的宽度
   */
  @Composable
  open fun ItemContent(
    item: CourseSourceItem,
    timeline: CourseTimeline,
    beginTime: MinuteTime,
    finalTime: MinuteTime,
  ) {
    item.Content(modifier = Modifier.layout { measurable, constraints ->
      val weight = timeline.calculateBeginFinalWeight(beginTime, finalTime)
      val height = (weight.y - weight.x) * constraints.maxHeight
      val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, height.roundToInt()))
      layout(placeable.width, placeable.height) {
        placeable.placeRelative(0, (weight.x * constraints.maxHeight).roundToInt())
      }
    })
  }
}

@Stable
interface CourseSourceItem {
  /**
   * 只绘制 item 内容，不关心宽高
   */
  @Composable
  fun Content(modifier: Modifier)
}