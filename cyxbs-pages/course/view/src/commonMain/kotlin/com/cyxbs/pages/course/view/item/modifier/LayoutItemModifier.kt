package com.cyxbs.pages.course.view.item.modifier

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.page.LocalCoursePage
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlin.math.roundToInt

/**
 * .
 *
 * @author 985892345
 * @date 2025/11/16
 */
object LayoutItemModifier : CourseItemModifier {
  @Composable
  override fun createModifier(): Modifier {
    return courseItemLayout(itemState)
  }
}

@Composable
private fun courseItemLayout(itemState: CourseItemState): Modifier {
  val coursePageContext = LocalCoursePage.current
  val timeline = coursePageContext.timeline
  // 水平位置
  val indexAnimatable = remember {
    Animatable(
      initialValue = calculateIndex(itemState, timeline).toFloat(),
    )
  }
  val beginTimeAnimatable = remember {
    Animatable(
      initialValue = itemState.item.whatTime.now.value.beginTime.minuteOfDay,
      typeConverter = Int.VectorConverter,
    )
  }
  val finalTimeAnimatable = remember {
    Animatable(
      initialValue = itemState.item.whatTime.now.value.finalTime.minuteOfDay,
      typeConverter = Int.VectorConverter,
    )
  }
  LaunchedEffect(timeline.beginDayOfWeek) {
    itemState.item.whatTime.now.collectLatest {
      supervisorScope {
        val newIndex = calculateIndex(itemState, timeline).toFloat()
        if (newIndex != indexAnimatable.value) {
          launch { indexAnimatable.animateTo(newIndex) }
        }
        if (it.beginTime.minuteOfDay != beginTimeAnimatable.value) {
          launch { beginTimeAnimatable.animateTo(it.beginTime.minuteOfDay) }
        }
        if (it.finalTime.minuteOfDay != finalTimeAnimatable.value) {
          launch { finalTimeAnimatable.animateTo(it.finalTime.minuteOfDay) }
        }
      }
    }
  }
  return Modifier.layout { measurable, constraints ->
    val weightOffset = timeline.calculateBeginFinalWeight(
      beginTime = MinuteTime.new(beginTimeAnimatable.value),
      finalTime = MinuteTime.new(finalTimeAnimatable.value)
    )
    val width = constraints.maxWidth / 7
    val height = (constraints.maxHeight * (weightOffset.y - weightOffset.x)).roundToInt()
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(width, height) {
      placeable.placeRelative(
        x = (indexAnimatable.value * constraints.maxWidth / 7 + (width - placeable.width) / 2F).roundToInt(),
        y = (weightOffset.x * constraints.maxHeight + (height - placeable.height) / 2F).roundToInt(),
        zIndex = itemState.zIndexState.floatValue,
      )
    }
  }
}

private fun calculateIndex(itemState: CourseItemState, timeline: CourseTimeline): Int {
  val itemDayOfWeekOrdinal = itemState.item.whatTime.now.value.dayOfWeek.ordinal
  val beginDayOfWeekOrdinal = timeline.beginDayOfWeek.ordinal
  return (itemDayOfWeekOrdinal + 7 - beginDayOfWeekOrdinal) % 7
}