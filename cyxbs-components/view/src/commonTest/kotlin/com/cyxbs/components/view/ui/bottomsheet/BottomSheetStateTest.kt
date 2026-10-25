package com.cyxbs.components.view.ui.bottomsheet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BottomSheetStateTest {

  /**
   * 验证中途改向时保留稳定语义起点，并拒绝旧动画在新动画之后提交过期终点。
   */
  @Test
  fun retargetKeepsOriginAndRejectsStaleCompletion() {
    val state = BottomSheetState()
    val openingId = state.beginSettling(
      targetAnchor = BottomSheetAnchor.Expanded,
      targetHeightPx = 100F,
      initialVelocity = -500F,
      source = BottomSheetSettleSource.Programmatic,
    )

    state.showHeight.floatValue = 48F
    val closingId = state.beginSettling(
      targetAnchor = BottomSheetAnchor.Collapsed,
      targetHeightPx = 20F,
      initialVelocity = 220F,
      source = BottomSheetSettleSource.Programmatic,
    )

    val closing = assertIs<BottomSheetMotionState.Settling>(state.motionState)
    assertEquals(BottomSheetAnchor.Collapsed, closing.originAnchor)
    assertEquals(BottomSheetAnchor.Expanded, closing.previousTargetAnchor)
    assertEquals(BottomSheetAnchor.Collapsed, closing.targetAnchor)
    assertEquals(48F, closing.startHeightPx)
    assertEquals(220F, closing.initialVelocityPxPerSecond)

    state.completeSettling(openingId, BottomSheetAnchor.Expanded)
    assertEquals(closing, state.motionState)
    assertEquals(BottomSheetAnchor.Collapsed, state.settledAnchor)

    state.completeSettling(closingId, BottomSheetAnchor.Collapsed)
    assertEquals(
      BottomSheetMotionState.Idle(BottomSheetAnchor.Collapsed),
      state.motionState,
    )
  }
}
