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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.zIndex
import com.cyxbs.components.utils.compose.getValue
import com.cyxbs.components.utils.compose.rememberDerivedStateOfStructure
import com.cyxbs.components.utils.compose.rememberUpdatedWrapper
import com.cyxbs.components.utils.compose.rememberWrapper
import com.cyxbs.components.utils.compose.setValue
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.IMovableItemModel
import com.cyxbs.pages.course.view.overlay.CourseItemRange
import com.cyxbs.pages.course.view.overlay.mergeOverlapRange
import com.cyxbs.pages.course.view.page.LocalCoursePageContext
import com.cyxbs.pages.course.view.timeline.data.MutableTimelineData
import kotlinx.coroutines.launch
import kotlin.math.min

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

  // 用于暴露给被覆盖的 item 进行设置
  var zIndex by mutableFloatStateOf(0F)
    protected set

  var state by mutableStateOf<LongPressMoveState>(LongPressMoveState.Idle)
    private set

  open fun enable(transition: MutableState<Offset>): Boolean {
    return transition.value == Offset.Zero
  }

  open fun onStartLongPress(pointer: PointerInputChange) {
  }

  open fun onMoveLongPress(
    transition: MutableState<Offset>,
    changeTransition: Offset,
    isTouch: Boolean, // true 则是手指触摸带来的移动，false 则说明是坐标系移动带来的移动
    screenLeftTop: Offset,
    size: IntSize,
  ) {
    transition.value = changeTransition
  }

  open fun onEndLongPress(
    upOrCancel: Boolean, // true：手指抬起；false：事件被取消
    transition: MutableState<Offset>,
    screenLeftTop: Offset,
    size: IntSize,
  ) {}

  /**
   * 执行最后移动 item 的动画
   *
   * 注意：单独分离出来是因为 suspend 函数不一定会成功执行，所以有些状态的取消需要写在 [onEndLongPress]
   */
  open suspend fun animateMove(
    transition: MutableState<Offset>,
    screenLeftTop: Offset,
    size: IntSize,
  ) {
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
    }
  }

  open fun updateState(state: LongPressMoveState) {
    this.state = state
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
    if (isMoving) 1F else controllerWrapper.zIndex
  ).onGloballyPositioned {
    // 得到 translation 变换前的坐标系
    originLayoutCoordinates = it
    if (controllerWrapper.state is LongPressMoveState.Touching) {
      // 当相对屏幕的位置发生改变时，则需要重新计算 transition（比如时间轴展开、滚轴上下滑动）
      val changeTransition = it.screenToLocal(screenPosition)
      controllerWrapper.onMoveLongPress(
        transition,
        changeTransition,
        false,
        screenPosition,
        it.size
      )
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
        // DOWN 事件会被点击消耗，但后续的 MOVE 事件是不会被点击消耗的（后续事件中点击会在 Final 流程中发现事件已被消耗就会取消点击）
        requireUnconsumed = false,
        // 使用 Initial 会导致 awaitLongPressOrCancellation 中的 Main 拿到的 pointer 是已经被 consume 的（因为 DOWN 事件被点击消耗了）
        // 所以这里也设置成 Main，让 awaitLongPressOrCancellation 里面拿到的是下一次 Main 的
        pass = PointerEventPass.Main,
      )
      if (!controllerWrapper.enable(transition)) return@awaitEachGesture
      val longPressPointer = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
      while (true) {
        val pointer = awaitPointerEvent().changes.fastFirstOrNull { it.id == longPressPointer.id }
        if (pointer == null || pointer.isConsumed) {
          // 如果当前 pointerInput 被卸载了，则会发送一个抬起且被消耗的事件
          // 但注意此时 coroutineScope 也即将被取消（虽然 isActive 为 true），launch 是无法执行的
          if (controllerWrapper.state is LongPressMoveState.Touching) {
            controllerWrapper.onEndLongPress(false, transition, screenPosition, size)
            controllerWrapper.updateState(LongPressMoveState.Animating)
            coroutineScope.launch {
              controllerWrapper.animateMove(transition, screenPosition, size)
            }.invokeOnCompletion {
              // 如果 coroutineScope 在执行前被取消，则不会执行 launch 的内容，但仍会执行 invokeOnCompletion
              controllerWrapper.updateState(LongPressMoveState.Idle)
            }
          }
          break
        }
        if (pointer.changedToUp()) {
          if (controllerWrapper.state is LongPressMoveState.Touching) {
            controllerWrapper.onEndLongPress(true, transition, screenPosition, size)
            controllerWrapper.updateState(LongPressMoveState.Animating)
            coroutineScope.launch {
              controllerWrapper.animateMove(transition, screenPosition, size)
            }.invokeOnCompletion {
              controllerWrapper.updateState(LongPressMoveState.Idle)
            }
          }
          break
        }
        pointer.consume()
        if (controllerWrapper.state == LongPressMoveState.Idle) {
          controllerWrapper.updateState(LongPressMoveState.Touching)
          controllerWrapper.onStartLongPress(pointer)
        }
        // 记录相对于屏幕的位置
        transitionLayoutCoordinate?.let { transitionLc ->
          screenPosition = transitionLc.localToScreen(pointer.position) - longPressPointer.position
          originLayoutCoordinates?.let { originLc ->
            // 根据 translation 变换前的坐标系算出 transition 偏移量
            val changeTransition = originLc.screenToLocal(screenPosition)
            controllerWrapper.onMoveLongPress(
              transition,
              changeTransition,
              true,
              screenPosition,
              size
            )
          }
        }
      }
    }
  }
}


// 长按移动 item 控制器的默认实现
// 耦合 CourseItemState
class LongPressMoveControllerImpl(
  val itemState: CourseItemState,
  val edgeScroll: EdgeScroll,
  val pageContext: LocalCoursePageContext,
) : LongPressMoveController() {

  companion object : CourseItemState.DataStore<LongPressMoveControllerImpl>

  init {
    itemState.setData(LongPressMoveControllerImpl, this)
  }

  private var edgePosition: EdgeScroll.EdgePosition? = null

  // 自身 showRange 转换器（包含自身完全不显示的情况）
  private val selfShowRangeTransformerForAll = CourseItemState.ShowRangeTransformer { _, overlap ->
    // 被长按的 item 即使被覆盖也完整展示
    listOf(CourseItemRange(overlap.item.beginTime, overlap.item.finalTime))
  }

  // 自身 showRange 转换器（自在自身完全不显示时处理）
  private val selfShowRangeTransformerForEmpty =
    CourseItemState.ShowRangeTransformer { show, overlap ->
      show.ifEmpty { listOf(CourseItemRange(overlap.item.beginTime, overlap.item.finalTime)) }
    }

  // 处理被覆盖 item 的 overlap 更新触发器
  private val overlapChangeTriggerForAllCovered =
    CourseItemState.CoveredItemShowRangeTransformerTrigger(pageContext) { show, overlap ->
      // 解除被覆盖 item 的隐藏区域
      val coveredRange = overlap.coveredRangeList
        .filter { it.itemOverlap.item === itemState.item }
        .map { it.range }
      (show + coveredRange).mergeOverlapRange()
    }

  // 处理被全覆盖 item 的 overlap 更新触发器
  private val overlapChangeTriggerForEmptyCovered =
    CourseItemState.CoveredItemShowRangeTransformerTrigger(pageContext) { show, overlap ->
      // 单独处理被全覆盖这类 item 的展示
      show.ifEmpty {
        val coveredRange = overlap.coveredRangeList
          .filter { it.itemOverlap.item === itemState.item }
          .map { it.range }
        (show + coveredRange).mergeOverlapRange()
      }
    }

  override fun enable(transition: MutableState<Offset>): Boolean {
    return transition.value == Offset.Zero && itemState.item is IMovableItemModel
  }

  override fun onStartLongPress(pointer: PointerInputChange) {
    super.onStartLongPress(pointer)
    edgePosition = edgeScroll.add()
    itemState.addShowRangeTransformer(selfShowRangeTransformerForAll)
    itemState.addOverlapChangeTrigger(overlapChangeTriggerForAllCovered)
  }

  override fun onMoveLongPress(
    transition: MutableState<Offset>,
    changeTransition: Offset,
    isTouch: Boolean,
    screenLeftTop: Offset,
    size: IntSize,
  ) {
    super.onMoveLongPress(transition, changeTransition, isTouch, screenLeftTop, size)
    if (isTouch) {
      edgePosition?.update(screenLeftTop.y, size.height)
    }
    // 移动过程中判断是否需要展开时间轴折叠部分
    tryExpandTimeline(screenLeftTop, size)
  }

  private val clickLock = mutableListOf<MutableTimelineData.ClickLock>()

  // 移动过程中判断是否需要展开时间轴折叠部分
  private fun tryExpandTimeline(screenLeftTop: Offset, size: IntSize) {
    val item = itemState.item as IMovableItemModel
    if (!item.enableExpandTimelineWhenMove()) return
    val scrollContext = pageContext.scrollContext.value ?: return
    itemState.timeline.data.asSequence()
      .filterIsInstance<MutableTimelineData>()
      .filter { it.state.value == MutableTimelineData.State.Collapse }
      .mapNotNull { time ->
        scrollContext.timelineCoordinatesMap[time]?.let { coor ->
          coor.localToScreen(Offset.Zero).let {
            val threshold = min(20, size.height / 2 - 10).coerceAtLeast(0)
            if (time == itemState.timeline.data.first()) {
              // 如果是开始的折叠时间，则需要超过一定距离才能触发展开
              screenLeftTop.y + threshold * 6 < it.y
            } else if (time == itemState.timeline.data.last()) {
              // 如果是结束的折叠时间，则也需要超过一定距离才能触发展开
              screenLeftTop.y + size.height - threshold * 6 > it.y + coor.size.height
            } else {
              // 中间的折叠时间存在相交区域即可展开
              it.y < (screenLeftTop.y + size.height - threshold) && (it.y + coor.size.height) > screenLeftTop.y + threshold
            }
          }
        }?.let { if (it) time else null }
      }.forEach {
        it.click()
        clickLock.add(it.lockClick()) // 展开后就给点击上锁，直到结束解锁后才允许点击
      }
  }

  override fun onEndLongPress(
    upOrCancel: Boolean,
    transition: MutableState<Offset>,
    screenLeftTop: Offset,
    size: IntSize
  ) {
    super.onEndLongPress(upOrCancel, transition, screenLeftTop, size)
    edgePosition?.remove()
    itemState.removeOverlapChangeTrigger(overlapChangeTriggerForAllCovered)
    itemState.removeShowRangeTransformer(selfShowRangeTransformerForAll)
    clickLock.fastForEach { it.unlock() }
    clickLock.clear()
  }

  override suspend fun animateMove(
    transition: MutableState<Offset>,
    screenLeftTop: Offset,
    size: IntSize
  ) {
    val item = itemState.item as IMovableItemModel
    val destinationOffset = item.getMoveDestinationOffset(
      itemState = itemState,
      pageContext = pageContext,
      transition = transition,
      screenTopLeft = screenLeftTop,
      size = size,
    )
    var topControllers = emptySet<LongPressMoveControllerImpl>()
    if (destinationOffset == Offset.Zero) {
      // 在 onEndLongPress 中移除了 overlapChangeTriggerForAllCovered 是为了让被部分覆盖的 item 能提前触发 text 位置还原动画
      // 但是这样会导致被全部覆盖的这类 item 也在动画前提前结束了，现象就是直接消失，体验起来很奇怪
      // 所以移除 overlapChangeTriggerForAllCovered 后再添加 overlapChangeTriggerForEmptyCovered 用于专门处理被全部覆盖的这类 item
      itemState.addOverlapChangeTrigger(overlapChangeTriggerForEmptyCovered)
      // 移除 selfShowRangeTransformerForAll 后再添加 selfShowRangeTransformerForEmpty 与上面同理
      // 前者提前移除是为了能提前触发 text 位置还原动画，后者再添加是为了防止被全部覆盖的这类 item 直接消失
      itemState.addShowRangeTransformer(selfShowRangeTransformerForEmpty)
      // 递归拿到所有上层 item
      fun collectTopItems(
        set: MutableSet<LongPressMoveControllerImpl>,
        itemState: CourseItemState
      ): MutableSet<LongPressMoveControllerImpl> {
        itemState.overlap.coveredRangeList.mapNotNull { cover ->
          pageContext.findItemState(cover.itemOverlap.item)
            ?.getData(LongPressMoveControllerImpl)
        }.fastForEach {
          set.add(it)
          collectTopItems(set, it.itemState)
        }
        return set
      }
      topControllers = collectTopItems(mutableSetOf(), itemState).onEach {
        // 在回到原位置时自身的 zIndex 是为 1 的，会遮挡上方的 item，所以需要设置上方的 item zIndex 来避免这种情况
        it.zIndex = 1F
      }
    }
    try {
      // 由 IMovableItemModel 实现最后动画的移动
      item.animateMove(itemState, pageContext, transition, destinationOffset)
    } finally {
      transition.value = destinationOffset
      if (destinationOffset == Offset.Zero) {
        itemState.removeOverlapChangeTrigger(overlapChangeTriggerForEmptyCovered)
        itemState.removeShowRangeTransformer(selfShowRangeTransformerForEmpty)
        topControllers.forEach { it.zIndex = 0F }
      }
    }
  }
}