package com.cyxbs.pages.course.view.item.modifier

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.utils.compose.derivedStateOfStructure
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.page.LocalCoursePage
import kotlinx.coroutines.Runnable
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

  // 是否启动时间信息改变后的动画，默认开启
  val animLock = CourseItemState.ValueKey { Lock() }

  /**
   * item 的最小视觉高度，默认不限制。
   *
   * 它以业务区间中心向上下两侧扩展绘制区域，不修改
   * [com.cyxbs.pages.course.view.item.CourseItemWhatTime] 的业务时间范围；适用于截止时间点等需要
   * 容纳一行文字、但不应伪造成长时间段的装饰项。
   */
  val minimumVisualHeight = CourseItemState.ValueKey<Dp> { 0.dp }

  /**
   * item 的固定视觉优先级，最终会与 [CourseItemState.zIndexState] 的临时层级相加。
   *
   * 默认值为 0，不改变现有课程与事务；需要始终浮在普通时段之上的时间点可设置更高值，且不会覆盖
   * 长按、弹窗等交互对临时 zIndex 的增减。
   */
  val visualPriority = CourseItemState.ValueKey { 0F }

  @Composable
  override fun createModifier(): Modifier {
    val itemState = itemState
    return courseItemLayout(itemState)
  }

  class Lock {
    private val count = mutableIntStateOf(0)

    private val isLocked = derivedStateOfStructure { count.intValue > 0 }

    fun lock(): Runnable {
      count.intValue++
      var isUnlock = false
      return Runnable {
        if (isUnlock) return@Runnable
        isUnlock = true
        count.intValue--
      }
    }

    fun isLocked(): Boolean {
      return isLocked.value
    }
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
            if (!LayoutItemModifier.animLock.get(itemState).isLocked()) {
              indexAnimatable.animateTo(newIndex)
            } else {
              indexAnimatable.snapTo(newIndex)
            }
          }
        }
        if (it.beginTime.minuteOfDay != beginTimeAnimatable.value) {
          launch {
            if (!LayoutItemModifier.animLock.get(itemState).isLocked()) {
              beginTimeAnimatable.animateTo(it.beginTime.minuteOfDay)
            } else {
              beginTimeAnimatable.snapTo(it.beginTime.minuteOfDay)
            }
          }
        }
        if (it.finalTime.minuteOfDay != finalTimeAnimatable.value) {
          launch {
            if (!LayoutItemModifier.animLock.get(itemState).isLocked()) {
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
    val naturalHeight =
      (constraints.maxHeight * (finalWeightRatio - beginWeightRatio)).roundToInt().coerceAtLeast(0)
    val height = maxOf(naturalHeight, LayoutItemModifier.minimumVisualHeight.get(itemState).roundToPx())
      .coerceAtLeast(1)
      .coerceAtMost(constraints.maxHeight)
    val placeable = measurable.measure(Constraints.fixed(width, height))
    val naturalY = (beginWeightRatio * constraints.maxHeight).roundToInt()
    val extraVisualHeight = height - naturalHeight
    val y = (naturalY - extraVisualHeight / 2)
      .coerceIn(0, (constraints.maxHeight - height).coerceAtLeast(0))
    layout(width, height) {
      placeable.placeRelative(
        x = (indexAnimatable.value * constraints.maxWidth / 7 + (width - placeable.width) / 2F).roundToInt(),
        y = y,
        zIndex = itemState.zIndexState.floatValue + LayoutItemModifier.visualPriority.get(itemState),
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
fun CourseItemState.observeItemRectOnScreen(forceCalculate: Boolean = false): Flow<Rect> {
  return layoutCoordinatesFlow.flatMapLatest { itemCoordinates ->
    if (itemCoordinates != null && itemCoordinates.isAttached && !forceCalculate) {
      flowOf(Rect(itemCoordinates.positionOnScreen(), itemCoordinates.size.toSize()))
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
          val offsetOnScreen = pageCoordinates.positionOnScreen()
          Rect(
            left = x + offsetOnScreen.x,
            top = y + offsetOnScreen.y,
            right = x + width + offsetOnScreen.x,
            bottom = y + height + offsetOnScreen.y,
          )
        }
    }
  }
}
