package com.cyxbs.pages.course.view.item.touch

import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.zIndex
import com.cyxbs.components.utils.compose.getValue
import com.cyxbs.components.utils.compose.rememberDerivedStateOfStructure
import com.cyxbs.components.utils.compose.rememberUpdatedWrapper
import com.cyxbs.components.utils.compose.rememberWrapper
import com.cyxbs.components.utils.compose.setValue
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.overlay.CourseItemRange
import com.cyxbs.pages.course.view.overlay.mergeOverlapRange
import com.cyxbs.pages.course.view.page.LocalCoursePageContext
import kotlinx.coroutines.launch

/**
 * 长按移动 item
 *
 * @author 985892345
 * @date 2025/5/4
 */

sealed interface LongPressMoveState {
  data object Idle : LongPressMoveState
  data object Touching : LongPressMoveState
  data object Animating : LongPressMoveState
}

@Stable
abstract class LongPressMoveController {

  var state by mutableStateOf<LongPressMoveState>(LongPressMoveState.Idle)
    protected set

  open fun enable(transition: MutableState<Offset>): Boolean {
    return transition.value == Offset.Zero
  }

  open fun onStartLongPress(pointer: PointerInputChange) {
    state = LongPressMoveState.Touching
  }

  open fun onMoveLongPress(
    transition: MutableState<Offset>,
    changeTransition: Offset,
    isTouch: Boolean, // true 则是手指触摸带来的移动，false 则说明是坐标系移动带来的移动
    leftTopScreenPosition: Offset,
    size: IntSize,
  ) {
    transition.value = changeTransition
  }

  open suspend fun onUpLongPress(transition: MutableState<Offset>) {
    state = LongPressMoveState.Animating
    try {
      animate(
        typeConverter = Offset.VectorConverter,
        initialValue = transition.value,
        targetValue = Offset.Zero,
      ) { value, _ ->
        transition.value = value
      }
    } finally {
      transition.value = Offset.Zero
      state = LongPressMoveState.Idle
    }
  }

  open suspend fun onCancelLongPress(transition: MutableState<Offset>) {
    state = LongPressMoveState.Animating
    try {
      animate(
        typeConverter = Offset.VectorConverter,
        initialValue = transition.value,
        targetValue = Offset.Zero,
      ) { value, _ ->
        transition.value = value
      }
    } finally {
      transition.value = Offset.Zero
      state = LongPressMoveState.Idle
    }
  }
}

// 长按移动 item
@Stable
@Composable
fun Modifier.longPressMove(
  controller: LongPressMoveController,
): Modifier {
  val controllerWrapper by rememberUpdatedWrapper(controller)
  val coroutineScope = rememberCoroutineScope()
  var originLayoutCoordinates by rememberWrapper<LayoutCoordinates?>(null)
  var transitionLayoutCoordinate by rememberWrapper<LayoutCoordinates?>(null)
  var screenPosition by remember { mutableStateOf(Offset.Zero) }
  val transition = remember { mutableStateOf(Offset.Zero) }
  val isMoving by rememberDerivedStateOfStructure { controllerWrapper.state !is LongPressMoveState.Idle }
  return zIndex(
    if (isMoving) 1F else 0F
  ).onGloballyPositioned {
    // 得到 translation 变换前的坐标系
    originLayoutCoordinates = it
    if (controllerWrapper.state is LongPressMoveState.Touching) {
      // 当相对屏幕的位置发生改变时，则需要重新计算 transition（比如时间轴展开、滚轴上下滑动）
      val changeTransition = it.screenToLocal(screenPosition)
      controllerWrapper.onMoveLongPress(transition, changeTransition, false, screenPosition, it.size)
    }
  }.graphicsLayer {
    translationX = transition.value.x
    translationY = transition.value.y
  }.onGloballyPositioned {
    // 这里得到的是经过 translation 变换后的坐标系
    // 后续 pointerInput 中的位置都是基于该坐标系的计算
    transitionLayoutCoordinate = it
  }.pointerInput(Unit) {
    // pointerInput 需要在 graphicsLayer 之后，否则下面的 item 无法处理事件
    awaitEachGesture {
      val down = awaitFirstDown(
        requireUnconsumed = false,
        // 使用 Initial 会导致 awaitLongPressOrCancellation 中的 Main 拿到的 pointer 是已经被 consume 的
        // 所以这里也设置成 Main，让 awaitLongPressOrCancellation 里面拿到的是下一次 Main 的
        pass = PointerEventPass.Main,
      )
      if (!controllerWrapper.enable(transition)) return@awaitEachGesture
      val longPressPointer = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
      while (true) {
        val pointer = try {
          awaitPointerEvent(pass = PointerEventPass.Initial).changes.fastFirstOrNull { it.id == longPressPointer.id }
        } catch (e: Exception) {
          // 可能会存在 CancellationException
          coroutineScope.launch {
            controllerWrapper.onCancelLongPress(transition)
          }
          throw e
        }
        if (pointer == null || pointer.isConsumed) {
          coroutineScope.launch {
            controllerWrapper.onCancelLongPress(transition)
          }
          break
        }
        if (pointer.changedToUp()) {
          coroutineScope.launch {
            controllerWrapper.onUpLongPress(transition)
          }
          break
        }
        pointer.consume()
        if (controllerWrapper.state == LongPressMoveState.Idle) {
          controllerWrapper.onStartLongPress(pointer)
        }
        // 记录相对于屏幕的位置
        transitionLayoutCoordinate?.let { transitionLc ->
          screenPosition = transitionLc.localToScreen(pointer.position) - longPressPointer.position
          originLayoutCoordinates?.let { originLc ->
            // 根据 translation 变换前的坐标系算出 transition 偏移量
            val changeTransition = originLc.screenToLocal(screenPosition)
            controllerWrapper.onMoveLongPress(transition, changeTransition, true, screenPosition, size)
          }
        }
      }
    }
  }
}


// 长按移动 item 控制器的默认实现
class LongPressMoveControllerImpl(
  val itemState: CourseItemState,
  val edgeScroll: EdgeScroll,
  val coursePageContext: LocalCoursePageContext,
) : LongPressMoveController() {

  private var edgePosition: EdgeScroll.EdgePosition? = null

  // 自身 showRange 转换器
  private val selfShowRangeTransformer = CourseItemState.ShowRangeTransformer { _, overlap ->
    // 被长按的 item 即使被覆盖也完整展示
    listOf(CourseItemRange(overlap.item.beginTime, overlap.item.finalTime))
  }

  // 处理被覆盖 item 的 overlap 更新触发器
  private val overlapChangeTriggerForAllCovered =
    CourseItemState.CoveredItemShowRangeTransformerTrigger(coursePageContext) { show, overlap ->
      // 解除被覆盖 item 的隐藏区域
      val coveredRange = overlap.coveredRangeList
        .filter { it.itemOverlap.item === itemState.item }
        .map { it.range }
      (show + coveredRange).mergeOverlapRange()
    }

  // 处理被全覆盖 item 的 overlap 更新触发器
  private val overlapChangeTriggerForEmptyCovered =
    CourseItemState.CoveredItemShowRangeTransformerTrigger(coursePageContext) { show, overlap ->
      // 单独处理被全覆盖这类 item 的展示
      show.ifEmpty {
        val coveredRange = overlap.coveredRangeList
          .filter { it.itemOverlap.item === itemState.item }
          .map { it.range }
        (show + coveredRange).mergeOverlapRange()
      }
    }

  override fun onStartLongPress(pointer: PointerInputChange) {
    super.onStartLongPress(pointer)
    edgePosition = edgeScroll.add()
    itemState.addShowRangeTransformer(selfShowRangeTransformer)
    itemState.addOverlapChangeTrigger(overlapChangeTriggerForAllCovered)
  }

  override fun onMoveLongPress(
    transition: MutableState<Offset>,
    changeTransition: Offset,
    isTouch: Boolean,
    leftTopScreenPosition: Offset,
    size: IntSize,
  ) {
    super.onMoveLongPress(transition, changeTransition, isTouch, leftTopScreenPosition, size)
    if (isTouch) {
      edgePosition?.update(leftTopScreenPosition, size)
    }
  }

  override suspend fun onUpLongPress(transition: MutableState<Offset>) {
    edgePosition?.remove()
    // 在动画之前移除 overlapChangeTriggerForAllCovered 是为了让被部分覆盖的 item 能提前触发 text 位置还原动画
    // 但是这样会导致被全部覆盖的这类 item 也在动画前提前结束了，现象就是直接消失，体验起来很奇怪
    // 所以移除 overlapChangeTriggerForAllCovered 后再添加 overlapChangeTriggerForEmptyCovered 用于专门处理被全部覆盖的这类 item
    itemState.removeOverlapChangeTrigger(overlapChangeTriggerForAllCovered)
    itemState.addOverlapChangeTrigger(overlapChangeTriggerForEmptyCovered)
    super.onUpLongPress(transition)
    itemState.removeShowRangeTransformer(selfShowRangeTransformer)
    itemState.removeOverlapChangeTrigger(overlapChangeTriggerForEmptyCovered)
  }

  override suspend fun onCancelLongPress(transition: MutableState<Offset>) {
    edgePosition?.remove()
    itemState.removeOverlapChangeTrigger(overlapChangeTriggerForAllCovered)
    itemState.addOverlapChangeTrigger(overlapChangeTriggerForEmptyCovered)
    super.onCancelLongPress(transition)
    itemState.removeShowRangeTransformer(selfShowRangeTransformer)
    itemState.removeOverlapChangeTrigger(overlapChangeTriggerForEmptyCovered)
  }
}