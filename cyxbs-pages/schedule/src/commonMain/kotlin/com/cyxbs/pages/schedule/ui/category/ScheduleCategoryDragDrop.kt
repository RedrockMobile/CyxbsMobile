package com.cyxbs.pages.schedule.ui.category

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * 分类列表拖拽状态。
 *
 * 算法来自 AndroidX 官方 `LazyColumnDragAndDropDemo`：
 * https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation/integration-tests/foundation-demos/src/main/java/androidx/compose/foundation/demos/LazyColumnDragAndDropDemo.kt
 * 本实现只把手势监听从整个 LazyColumn 下沉到左侧拖动手柄，避免长按名称或操作按钮误触排序。
 */
@Composable
internal fun rememberScheduleCategoryDragDropState(
  lazyListState: LazyListState,
  onMove: (fromIndex: Int, toIndex: Int) -> Unit,
  onDragFinished: () -> Unit,
): ScheduleCategoryDragDropState {
  val scope = rememberCoroutineScope()
  val currentOnMove by rememberUpdatedState(onMove)
  val currentOnDragFinished by rememberUpdatedState(onDragFinished)
  val state = remember(lazyListState, scope) {
    ScheduleCategoryDragDropState(
      lazyListState = lazyListState,
      scope = scope,
      onMove = { from, to -> currentOnMove(from, to) },
      onDragFinished = { currentOnDragFinished() },
    )
  }
  LaunchedEffect(state) {
    while (true) lazyListState.scrollBy(state.scrollChannel.receive())
  }
  return state
}

/** 拖拽过程只保存可见条目的布局身份与位移，不持有分类业务数据。 */
internal class ScheduleCategoryDragDropState(
  private val lazyListState: LazyListState,
  private val scope: CoroutineScope,
  private val onMove: (Int, Int) -> Unit,
  private val onDragFinished: () -> Unit,
) {
  var draggingItemIndex by mutableStateOf<Int?>(null)
    private set

  internal val scrollChannel = Channel<Float>()
  private var draggingItemDraggedDelta by mutableFloatStateOf(0f)
  private var draggingItemInitialOffset = 0
  private var orderChanged = false

  /** 当前被拖动条目相对 LazyColumn 正常布局位置的视觉偏移。 */
  internal val draggingItemOffset: Float
    get() = draggingItemLayoutInfo?.let { item ->
      draggingItemInitialOffset + draggingItemDraggedDelta - item.offset
    } ?: 0f

  private val draggingItemLayoutInfo: LazyListItemInfo?
    get() = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggingItemIndex }

  internal var previousIndexOfDraggedItem by mutableStateOf<Int?>(null)
    private set
  internal val previousItemOffset = Animatable(0f)

  /** 手柄长按成功后，以对应 Lazy item 的当前 offset 作为拖动原点。 */
  internal fun onDragStart(index: Int) {
    lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.also { item ->
      draggingItemIndex = item.index
      draggingItemInitialOffset = item.offset
      orderChanged = false
    }
  }

  /**
   * 累加手指位移，并用被拖动条目的中点寻找目标条目。
   *
   * 中点进入另一条目的布局区间后立即换位；没有目标且越过视口边缘时，经 channel 请求 LazyColumn
   * 同步滚动，保持长列表可以连续拖动。
   */
  internal fun onDrag(offset: Offset) {
    draggingItemDraggedDelta += offset.y
    val draggingItem = draggingItemLayoutInfo ?: return
    val startOffset = draggingItem.offset + draggingItemOffset
    val endOffset = startOffset + draggingItem.size
    val middleOffset = startOffset + (endOffset - startOffset) / 2f
    val targetItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
      item.index != draggingItem.index && middleOffset.toInt() in item.offset..item.offsetEnd
    }
    if (targetItem != null) {
      val scrollToIndex = when {
        targetItem.index == lazyListState.firstVisibleItemIndex -> draggingItem.index
        draggingItem.index == lazyListState.firstVisibleItemIndex -> targetItem.index
        else -> null
      }
      if (scrollToIndex != null) {
        scope.launch {
          // 避免 LazyColumn 自动维持“第一项仍为第一项”而抵消本次换位。
          lazyListState.scrollToItem(scrollToIndex, lazyListState.firstVisibleItemScrollOffset)
          onMove(draggingItem.index, targetItem.index)
        }
      } else {
        onMove(draggingItem.index, targetItem.index)
      }
      draggingItemIndex = targetItem.index
      orderChanged = true
      return
    }
    val overscroll = when {
      draggingItemDraggedDelta > 0 ->
        (endOffset - lazyListState.layoutInfo.viewportEndOffset).coerceAtLeast(0f)
      draggingItemDraggedDelta < 0 ->
        (startOffset - lazyListState.layoutInfo.viewportStartOffset).coerceAtMost(0f)
      else -> 0f
    }
    if (overscroll != 0f) scrollChannel.trySend(overscroll)
  }

  /** 松手或取消时让拖动条目回弹；只有真实换位过才请求持久化 sortOrder。 */
  internal fun onDragInterrupted() {
    val previousIndex = draggingItemIndex
    if (previousIndex != null) {
      previousIndexOfDraggedItem = previousIndex
      val startOffset = draggingItemOffset
      scope.launch {
        previousItemOffset.snapTo(startOffset)
        previousItemOffset.animateTo(
          0f,
          spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 1f),
        )
        previousIndexOfDraggedItem = null
      }
    }
    val shouldPersist = orderChanged
    draggingItemDraggedDelta = 0f
    draggingItemIndex = null
    draggingItemInitialOffset = 0
    orderChanged = false
    if (shouldPersist) onDragFinished()
  }

  private val LazyListItemInfo.offsetEnd: Int
    get() = offset + size
}

/** 仅挂在点阵手柄上；长按手柄后才把后续 drag delta 交给列表排序状态。 */
internal fun Modifier.scheduleCategoryDragHandle(
  state: ScheduleCategoryDragDropState,
  index: Int,
  enabled: Boolean,
): Modifier = if (!enabled) this else pointerInput(state, index) {
  detectDragGesturesAfterLongPress(
    onDragStart = { state.onDragStart(index) },
    onDrag = { change, dragAmount ->
      change.consume()
      state.onDrag(dragAmount)
    },
    onDragEnd = state::onDragInterrupted,
    onDragCancel = state::onDragInterrupted,
  )
}

/** 给当前拖动项提升层级并平移，其余项使用 Lazy item 官方 placement 动画补位。 */
@Composable
internal fun LazyItemScope.ScheduleCategoryDraggableItem(
  state: ScheduleCategoryDragDropState,
  index: Int,
  content: @Composable (isDragging: Boolean) -> Unit,
) {
  val dragging = index == state.draggingItemIndex
  val modifier = when {
    dragging -> Modifier.zIndex(1f).graphicsLayer { translationY = state.draggingItemOffset }
    index == state.previousIndexOfDraggedItem ->
      Modifier.zIndex(1f).graphicsLayer { translationY = state.previousItemOffset.value }
    else -> Modifier.animateItem()
  }
  Box(modifier = modifier) { content(dragging) }
}
