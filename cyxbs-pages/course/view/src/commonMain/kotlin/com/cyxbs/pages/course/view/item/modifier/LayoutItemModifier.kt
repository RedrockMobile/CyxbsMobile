package com.cyxbs.pages.course.view.item.modifier

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.toSize
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.page.LocalCoursePage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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

  // 是否启动时间信息改变后的动画，默认不开启
  val enableAnim = CourseItemState.ValueKey { false }

  @Composable
  override fun createModifier(): Modifier {
    val itemState = itemState
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
      initialValue = calculateIndex(itemState).toFloat(),
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
        val newIndex = calculateIndex(itemState).toFloat()
        if (newIndex != indexAnimatable.value) {
          launch {
            if (LayoutItemModifier.enableAnim.get(itemState)) {
              indexAnimatable.animateTo(newIndex)
            } else {
              indexAnimatable.snapTo(newIndex)
            }
          }
        }
        if (it.beginTime.minuteOfDay != beginTimeAnimatable.value) {
          launch {
            if (LayoutItemModifier.enableAnim.get(itemState)) {
              beginTimeAnimatable.animateTo(it.beginTime.minuteOfDay)
            } else {
              beginTimeAnimatable.snapTo(it.beginTime.minuteOfDay)
            }
          }
        }
        if (it.finalTime.minuteOfDay != finalTimeAnimatable.value) {
          launch {
            if (LayoutItemModifier.enableAnim.get(itemState)) {
              finalTimeAnimatable.animateTo(it.finalTime.minuteOfDay)
            } else {
              finalTimeAnimatable.snapTo(it.finalTime.minuteOfDay)
            }
          }
        }
      }
    }
  }
  return Modifier.layout { measurable, constraints ->
    val beginWeightRatio = timeline.calculateWeightRatio(MinuteTime.new(beginTimeAnimatable.value))
    val finalWeightRatio = timeline.calculateWeightRatio(MinuteTime.new(finalTimeAnimatable.value))
    val width = constraints.maxWidth / 7
    val height = (constraints.maxHeight * (finalWeightRatio - beginWeightRatio)).roundToInt().coerceAtLeast(1)
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(width, height) {
      placeable.placeRelative(
        x = (indexAnimatable.value * constraints.maxWidth / 7 + (width - placeable.width) / 2F).roundToInt(),
        y = (beginWeightRatio * constraints.maxHeight + (height - placeable.height) / 2F).roundToInt(),
        zIndex = itemState.zIndexState.floatValue,
      )
    }
  }
}

private fun calculateIndex(itemState: CourseItemState,): Int {
  val itemDayOfWeekOrdinal = itemState.item.whatTime.now.value.dayOfWeek.ordinal
  val beginDayOfWeekOrdinal = itemState.coursePage.timeline.beginDayOfWeek.ordinal
  return (itemDayOfWeekOrdinal + 7 - beginDayOfWeekOrdinal) % 7
}

/**
 * 获取 item 在屏幕中的坐标
 * 会跟随 item 的位置移动而实时改变
 * @param forceCalculate 是否强制实时计算 item 的坐标位置，一般用于当前 item 还未完全变成对应时间的情况
 */
fun CourseItemState.observeItemRectInWindow(forceCalculate: Boolean = false): Flow<Rect> {
  return layoutCoordinatesFlow.flatMapLatest { itemCoordinates ->
    if (itemCoordinates != null && itemCoordinates.isAttached && !forceCalculate) {
      flowOf(Rect(itemCoordinates.positionInWindow(), itemCoordinates.size.toSize()))
    } else {
      // 此时 item 可能已经不可见，比如被上方重叠的 item 遮挡完了
      // 使用 coursePage.layoutCoordinatesFlow 进行计算
      coursePageFlow.filterNotNull()
        .flatMapLatest { it.layoutCoordinatesFlow }
        .filter { it.isAttached } // 需要确保 isAttached，防止 page 已经不可见
        .map { pageCoordinates ->
          // 手动计算 item 的位置，跟 courseItemLayout 计算逻辑保持一致
          val beginWeightRatio = coursePage.timeline.calculateWeightRatio(item.whatTime.beginTime)
          val finalWeightRatio = coursePage.timeline.calculateWeightRatio(item.whatTime.finalTime)
          val width = pageCoordinates.size.width / 7
          val height =
            (pageCoordinates.size.height * (finalWeightRatio - beginWeightRatio)).roundToInt()
          val x = calculateIndex(this) * pageCoordinates.size.width / 7F
          val y = beginWeightRatio * pageCoordinates.size.height
          val offsetInWindow = pageCoordinates.positionInWindow()
          Rect(
            left = x + offsetInWindow.x,
            top = y + offsetInWindow.y,
            right = x + width + offsetInWindow.x,
            bottom = y + height + offsetInWindow.y,
          )
        }
    }
  }
}