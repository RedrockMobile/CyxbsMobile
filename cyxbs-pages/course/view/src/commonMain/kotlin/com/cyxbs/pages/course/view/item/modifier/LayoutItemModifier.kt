package com.cyxbs.pages.course.view.item.modifier

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

  /** 所有课表 Item 共用的最小视觉高度，避免时间轴折叠后内容与点击区域完全消失。 */
  val DefaultMinimumVisualHeight = 20.dp

  // 是否启动时间信息改变后的动画，默认开启
  val animLock = CourseItemState.ValueKey { Lock() }

  /**
   * Item 的最小视觉高度，默认对所有课表 Item 生效。
   *
   * 它以业务区间中心向上下两侧扩展绘制区域，不修改
   * [com.cyxbs.pages.course.view.item.CourseItemWhatTime] 的业务时间范围。零时长 Item 与被时间轴
   * 压缩的时间段因此能保留一致的可读、可点击区域；特殊 Item 仍可按需覆盖该值。
   */
  val minimumVisualHeight = CourseItemState.ValueKey<Dp> { DefaultMinimumVisualHeight }

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
 * 获取 Item 在屏幕中的坐标，并在 Item 布局位置变化时持续更新。
 *
 * @param forceCalculate 为 true 时不依赖 Item 自身是否存在可用坐标，而是依据课表页面坐标、时间范围和
 * 时间轴权重计算完整 Item 的位置。时间轴展开动画会逐帧改变权重，因此这里使用 [snapshotFlow] 继续
 * 发出新的矩形；调用方若会同时滚动课表，需要剔除自身滚动造成的屏幕坐标变化，避免形成反馈循环。
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun CourseItemState.observeItemRectOnScreen(forceCalculate: Boolean = false): Flow<Rect> {
  return layoutCoordinatesFlow.flatMapLatest { itemCoordinates ->
    if (itemCoordinates != null && itemCoordinates.isAttached && !forceCalculate) {
      flowOf(Rect(itemCoordinates.positionOnScreen(), itemCoordinates.size.toSize()))
    } else {
      // Item 可能已被上层重叠项完全遮挡，因此使用 CoursePage 坐标计算其完整业务区间。
      coursePageFlow.filterNotNull()
        .flatMapLatest { it.layoutCoordinatesFlow }
        .filter { it.isAttached }
        .flatMapLatest { pageCoordinates ->
          snapshotFlow {
            // 与 courseItemLayout 保持相同的时间轴权重换算；权重变化会驱动新的坐标结果。
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
}
