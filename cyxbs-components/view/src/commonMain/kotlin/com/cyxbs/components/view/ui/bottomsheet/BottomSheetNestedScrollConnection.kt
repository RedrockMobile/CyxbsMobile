package com.cyxbs.components.view.ui.bottomsheet

import androidx.compose.foundation.MutatePriority
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * 协调内部滚动容器与 BottomSheet 的位移和剩余惯性，并在手势结束后选择最终吸附锚点。
 */
internal class BottomSheetNestedScrollConnection(
  private val bottomSheetState: BottomSheetState,
  private val flingBehavior: BottomSheetFlingBehavior,
) : NestedScrollConnection {

  override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
    if (!bottomSheetState.userScrollEnabled.value) return super.onPreScroll(available, source)
    val min = bottomSheetState.peekHeightUpdater.peekHeightPx
    val max = bottomSheetState.showMaxHeight.floatValue
    val old = bottomSheetState.showHeight.floatValue
    // 先消耗手指向上的滑动
    if (available.y < 0 && old < max && source == NestedScrollSource.UserInput) {
      bottomSheetState.beginDrag(BottomSheetDragSource.NestedScroll)
      val new = (old - available.y).coerceIn(min, max)
      val diff = old - new
      bottomSheetState.scrollableState.dispatchRawDelta(diff)
      return Offset(x = 0F, y = diff)
    }
    return super.onPreScroll(available, source)
  }

  override fun onPostScroll(
    consumed: Offset,
    available: Offset,
    source: NestedScrollSource
  ): Offset {
    if (!bottomSheetState.userScrollEnabled.value) return super.onPostScroll(consumed, available, source)
    val min = bottomSheetState.peekHeightUpdater.peekHeightPx
    val max = bottomSheetState.showMaxHeight.floatValue
    val old = bottomSheetState.showHeight.floatValue
    // 只接管用户手指产生的剩余位移；列表的 SideEffect 惯性应在 onPostFling 以剩余速度一次性交接，
    // 否则 Sheet 会先跟随列表 decay 移动，再切换到 Spring，形成两段式速度变化。
    if (available.y > 0 && old > min && source == NestedScrollSource.UserInput) {
      bottomSheetState.beginDrag(BottomSheetDragSource.NestedScroll)
      val new = (old - available.y).coerceIn(min, max)
      val diff = old - new
      bottomSheetState.scrollableState.dispatchRawDelta(diff)
      return Offset(x = 0F, y = diff)
    }
    return super.onPostScroll(consumed, available, source)
  }

  override suspend fun onPreFling(available: Velocity): Velocity {
    if (!bottomSheetState.userScrollEnabled.value) return super.onPreFling(available)
    val min = bottomSheetState.peekHeightUpdater.peekHeightPx
    val max = bottomSheetState.showMaxHeight.floatValue
    val current = bottomSheetState.showHeight.floatValue
    val isBetweenAnchors = current > min && current < max
    val shouldExpandFromCollapsed = current <= min && available.y < 0F
    if (!isBetweenAnchors && !shouldExpandFromCollapsed) {
      when {
        current >= max -> bottomSheetState.completeDrag(BottomSheetAnchor.Expanded)
        current <= min -> bottomSheetState.completeDrag(BottomSheetAnchor.Collapsed)
      }
      return Velocity.Zero
    }

    // Sheet 已经离开锚点时，应在子列表启动 decay 前取得完整手势速度；折叠态的向上 fling 也应
    // 优先展开 Sheet。完全展开态则先交给列表；列表到顶后的剩余惯性由 onPostFling 截断，避免
    // 同一次 fling 继续折叠 Sheet。
    val remainingVelocity = settleWithVelocity(available.y)
    return Velocity(x = 0F, y = available.y - remainingVelocity)
  }

  override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
    if (!bottomSheetState.userScrollEnabled.value) return super.onPostFling(consumed, available)
    val min = bottomSheetState.peekHeightUpdater.peekHeightPx
    val max = bottomSheetState.showMaxHeight.floatValue
    val current = bottomSheetState.showHeight.floatValue

    // Sheet 完全展开时，onPostFling 收到的是子列表 fling 后未消费的剩余速度。这里刻意截断它，
    // 使列表的惯性最多滚到顶部，不会在同一次 fling 中继续带动 Sheet 折叠；用户需要继续按住拖动，
    // 或重新向下拖动，才会通过 onPostScroll 让 Sheet 离开展开锚点。返回 available 表示这部分速度
    // 已被当前嵌套滚动节点消费，避免它继续传给更外层容器。
    if (current == max) return available.copy(x = 0F)

    val canMoveDown = available.y > 0F && current > min
    val canMoveUp = available.y < 0F && current < max
    if (!canMoveDown && !canMoveUp) return Velocity.Zero

    // 子列表已经优先处理其 fling，这里只用它无法消费的剩余速度驱动 Sheet。
    val remainingVelocity = settleWithVelocity(available.y)
    return Velocity(x = 0F, y = available.y - remainingVelocity)
  }

  /**
   * 使用嵌套滚动交付的速度吸附 Sheet，并在离开滚动互斥区后同步最终业务状态。
   *
   * @param initialVelocity 嵌套滚动坐标系中的初速度，正值向下、负值向上。
   * @return Sheet 动画结束后未消费、可继续交给祖先节点的速度。
   */
  private suspend fun settleWithVelocity(initialVelocity: Float): Float {
    var result = BottomSheetFlingBehavior.Result(
      targetAnchor = BottomSheetAnchor.Expanded,
      remainingVelocity = initialVelocity,
    )
    bottomSheetState.scrollableState.scroll(scrollPriority = MutatePriority.UserInput) {
      with(flingBehavior) {
        result = settle(
          initialVelocity = initialVelocity,
          source = BottomSheetSettleSource.NestedFling,
        )
      }
    }
    if (result.targetAnchor != BottomSheetAnchor.Expanded && bottomSheetState.requestDismissOnDrag) {
      bottomSheetState.onDismissRequest.invoke(bottomSheetState)
    }
    return result.remainingVelocity
  }
}
