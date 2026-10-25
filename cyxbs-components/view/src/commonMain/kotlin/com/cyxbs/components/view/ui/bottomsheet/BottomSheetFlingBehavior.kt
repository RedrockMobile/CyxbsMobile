package com.cyxbs.components.view.ui.bottomsheet

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import kotlin.math.abs

/** 使用单段弹簧完成 Sheet 松手后的锚点吸附。 */
internal class BottomSheetFlingBehavior(
  private val bottomSheetState: BottomSheetState,
) : TargetedFlingBehavior {

  /** 一次吸附的目标与动画结束后剩余速度。 */
  data class Result(
    val targetAnchor: BottomSheetAnchor,
    val remainingVelocity: Float,
  )

  /** 锚点选择结果，避免通过动画结束后的浮点高度反推语义目标。 */
  private data class Target(
    val anchor: BottomSheetAnchor,
    val heightPx: Float,
  )

  /** Spring 与布局边界之间不足该距离时视为抵达目标，单位为 px。 */
  private val settlingThresholdPx = 1F

  /**
   * 将松手速度投影到未来位置时使用的时间窗口，单位为秒。
   *
   * 投影只决定最终吸附锚点，不会先播放一段衰减动画。
   */
  private val velocityProjectionSeconds = 0.2F

  /**
   * `initialVelocity` 一方面用于预测用户意图并选择 Hide、Collapsed 或 Expanded，另一方面作为
   * Spring 的初速度直接延续手势。这里不播放 decay 动画，避免速度曲线在 decay 与 snap 的交界处
   * 发生肉眼可见的阶段切换。
   */
  override suspend fun ScrollScope.performFling(
    initialVelocity: Float,
    onRemainingDistanceUpdated: (Float) -> Unit,
  ): Float = settle(
    initialVelocity = initialVelocity,
    source = BottomSheetSettleSource.DragRelease,
    onRemainingDistanceUpdated = onRemainingDistanceUpdated,
  ).remainingVelocity

  /**
   * 从当前真实位置执行一次吸附，并将选择出的语义锚点返回给拖动或嵌套滚动调用方。
   *
   * @param initialVelocity 滚动坐标系中的初速度，正值表示 Sheet 向下移动。
   * @param source 本次吸附来自直接拖动释放还是内部列表剩余惯性。
   */
  suspend fun ScrollScope.settle(
    initialVelocity: Float,
    source: BottomSheetSettleSource,
    onRemainingDistanceUpdated: ((Float) -> Unit)? = null,
  ): Result {
    bottomSheetState.animationVelocity = initialVelocity
    val now = bottomSheetState.showHeight.floatValue
    val target = calculateTarget(initialVelocity)
    val transitionId = bottomSheetState.beginSettling(
      targetAnchor = target.anchor,
      targetHeightPx = target.heightPx,
      initialVelocity = initialVelocity,
      source = source,
    )
    // ScrollableState 的正方向会减小 showHeight，因此目标滚动量需要使用 now - targetHeight。
    val targetScrollOffset = now - target.heightPx
    onRemainingDistanceUpdated?.invoke(targetScrollOffset)
    if (abs(targetScrollOffset) < settlingThresholdPx) {
      scrollBy(targetScrollOffset)
      onRemainingDistanceUpdated?.invoke(0F)
      bottomSheetState.animationVelocity = 0F
      bottomSheetState.completeSettling(transitionId, target.anchor)
      return Result(target.anchor, 0F)
    }

    var consumedOffset = 0F
    var remainingVelocity = initialVelocity
    AnimationState(
      initialValue = 0F,
      initialVelocity = initialVelocity,
    ).animateTo(
      targetValue = targetScrollOffset,
      animationSpec = bottomSheetState.animationSpecFor(target.heightPx),
    ) {
      val requestedDelta = value - consumedOffset
      val consumedDelta = scrollBy(requestedDelta)
      consumedOffset += consumedDelta
      remainingVelocity = velocity
      bottomSheetState.animationVelocity = velocity
      onRemainingDistanceUpdated?.invoke(targetScrollOffset - consumedOffset)

      // 内容高度或 Insets 在动画途中变化时，布局边界可能拒绝部分位移；此时立即结束并把剩余速度
      // 交还嵌套滚动链，避免弹簧持续尝试越过已经失效的目标。
      if (abs(requestedDelta - consumedDelta) >= settlingThresholdPx) {
        cancelAnimation()
      }
    }
    val result = if (abs(targetScrollOffset - consumedOffset) < settlingThresholdPx) {
      0F
    } else {
      remainingVelocity
    }
    // 被外部动画取消时不会执行到这里，最后一帧速度会保留给接管动画。
    bottomSheetState.animationVelocity = 0F
    bottomSheetState.completeSettling(transitionId, target.anchor)
    return Result(target.anchor, result)
  }

  /** 根据当前位置和释放速度投影选择最终锚点，不实际播放投影过程。 */
  private fun calculateTarget(initialVelocity: Float): Target {
    val peekHeight = bottomSheetState.peekHeightUpdater.peekHeightPx
    val max = bottomSheetState.showMaxHeight.floatValue
    val now = bottomSheetState.showHeight.floatValue
    if (max <= 0F) return Target(BottomSheetAnchor.Hidden, 0F)

    // 正速度表示向下拖动，因此从 showHeight 中减去速度投影距离。
    val projectedHeight = (
      now - initialVelocity * velocityProjectionSeconds
    ).coerceIn(0F, max)
    if (!bottomSheetState.hideable) {
      return if (projectedHeight <= (peekHeight + max) / 2F) {
        Target(BottomSheetAnchor.Collapsed, peekHeight)
      } else {
        Target(BottomSheetAnchor.Expanded, max)
      }
    }

    return when {
      projectedHeight <= peekHeight / 2F -> Target(BottomSheetAnchor.Hidden, 0F)
      projectedHeight <= (peekHeight + max) / 2F -> {
        Target(BottomSheetAnchor.Collapsed, peekHeight)
      }
      else -> Target(BottomSheetAnchor.Expanded, max)
    }
  }
}
