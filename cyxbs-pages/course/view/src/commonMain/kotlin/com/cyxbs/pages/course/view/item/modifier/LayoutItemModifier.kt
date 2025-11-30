package com.cyxbs.pages.course.view.item.modifier

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
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
  // 垂直位置与高度
  val weightAnimatable = remember {
    Animatable(
      initialValue = calculateWeight(itemState, timeline),
      typeConverter = Offset.VectorConverter,
    )
  }
  LaunchedEffect(timeline.beginDayOfWeek) {
    val whatTime = itemState.item.whatTime
    if (whatTime is CourseItemWhatTime.Changeable) {
      whatTime.observe().collectLatest {
        supervisorScope {
          launch { indexAnimatable.animateTo(calculateIndex(itemState, timeline).toFloat()) }
          launch { weightAnimatable.animateTo(calculateWeight(itemState, timeline)) }
        }
      }
    }
  }
  return Modifier.layout { measurable, constraints ->
    val width = constraints.maxWidth / 7
    val height =
      (constraints.maxHeight * (weightAnimatable.value.y - weightAnimatable.value.x)).roundToInt()
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(placeable.width, placeable.height) {
      placeable.placeRelative(
        x = (indexAnimatable.value * placeable.width).roundToInt(),
        y = (weightAnimatable.value.x * constraints.maxHeight).roundToInt()
      )
    }
  }
}

private fun calculateIndex(itemState: CourseItemState, timeline: CourseTimeline): Int {
  Snapshot.withoutReadObservation {
    val itemDayOfWeekOrdinal = itemState.item.whatTime.now.dayOfWeek.ordinal
    val beginDayOfWeekOrdinal = timeline.beginDayOfWeek.ordinal
    return (itemDayOfWeekOrdinal + 7 - beginDayOfWeekOrdinal) % 7
  }
}

private fun calculateWeight(itemState: CourseItemState, timeline: CourseTimeline): Offset {
  Snapshot.withoutReadObservation {
    return timeline.calculateBeginFinalWeight(
      itemState.item.whatTime.now.beginTime,
      itemState.item.whatTime.now.finalTime
    )
  }
}