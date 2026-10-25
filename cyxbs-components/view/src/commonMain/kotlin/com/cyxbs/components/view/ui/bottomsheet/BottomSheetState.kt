package com.cyxbs.components.view.ui.bottomsheet

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.cyxbs.components.utils.compose.derivedStateOfStructure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlin.math.abs

/**
 * BottomSheet 已稳定到达的锚点。
 *
 * 锚点只描述真实完成的位置，不包含拖动、Fling 或程序化动画等运动阶段。
 */
enum class BottomSheetAnchor {
  Hidden,
  Collapsed,
  Expanded,
}

/** 用户直接改变 BottomSheet 高度时的输入来源。 */
enum class BottomSheetDragSource {
  DragHandle,
  NestedScroll,
}

/** BottomSheet 选定锚点并开始吸附时的触发来源。 */
enum class BottomSheetSettleSource {
  Programmatic,
  DragRelease,
  NestedFling,
}

/**
 * BottomSheet 当前的运动状态。
 */
sealed interface BottomSheetMotionState {

  /**
   * [originAnchor] 始终是本轮运动开始前上次到达的锚点。
   * 动画中途改向时，新分段会从当时的实际高度和速度继续，但不会伪造一个尚未到达的起始锚点。
   */
  val originAnchor: BottomSheetAnchor

  /** BottomSheet 当前静止在 [anchor]。 */
  data class Idle(
    val anchor: BottomSheetAnchor,
  ) : BottomSheetMotionState {
    override val originAnchor: BottomSheetAnchor = anchor
  }

  /**
   * 用户正在直接拖动 BottomSheet。
   *
   * @param originAnchor 上次到达的锚点
   * @param startHeightPx 本段拖动开始时的真实显示高度，单位为 px。
   * @param interruptedTargetAnchor 拖动是否打断了一个尚未完成的吸附目标。
   * @param source 手势来自 Sheet 拖拽区还是内部嵌套滚动。
   */
  data class Dragging(
    override val originAnchor: BottomSheetAnchor,
    val startHeightPx: Float,
    val interruptedTargetAnchor: BottomSheetAnchor?,
    val source: BottomSheetDragSource,
  ) : BottomSheetMotionState

  /**
   * BottomSheet 已选定目标，正在执行单段弹簧吸附。
   *
   * @param originAnchor 上次到达的锚点
   * @param startHeightPx 本段动画开始时的真实高度，可能与 [originAnchor] 不同，因为会有滚动中途切换的情况
   * @param transitionId 本段运动的唯一编号，用于阻止被取消的旧协程覆盖新状态。
   * @param previousTargetAnchor 改向前的目标；从静止或拖动释放开始时可以为 null。
   * @param targetAnchor 当前目标锚点。
   * @param targetHeightPx 当前目标高度，单位为 px。
   * @param initialVelocityPxPerSecond 本段弹簧接管时的真实初速度，正值表示 Sheet 向下移动。
   * @param source 本段吸附的触发来源。
   */
  data class Settling(
    val transitionId: Long,
    override val originAnchor: BottomSheetAnchor,
    val startHeightPx: Float,
    val previousTargetAnchor: BottomSheetAnchor?,
    val targetAnchor: BottomSheetAnchor,
    val targetHeightPx: Float,
    val initialVelocityPxPerSecond: Float,
    val source: BottomSheetSettleSource,
  ) : BottomSheetMotionState
}

/**
 * BottomSheet 的状态与动作入口，负责统一管理锚点、拖动、嵌套滚动和吸附动画。
 *
 * ## 核心方法：
 * ```
 * // 展开
 * state.expandAsync()
 * state.expandSuspend()
 *
 * // 折叠
 * state.collapseAsync()
 * state.collapseSuspend()
 *
 * // 隐藏
 * state.hideAsync()
 * state.hideSuspend()
 *
 * // 判断是否正处于展开/折叠/隐藏
 * state.isSettledAt(BottomSheetAnchor.Expanded)
 * state.isSettledAt(BottomSheetAnchor.Collapsed)
 * state.isSettledAt(BottomSheetAnchor.Hidden)
 *
 * // 判断是否正在移动；Dragging 为用户拖动，Settling 为吸附动画
 * state.motionState !is BottomSheetMotionState.Idle
 * state.motionState is BottomSheetMotionState.Dragging
 * state.motionState is BottomSheetMotionState.Settling
 *
 * // 挂起直到真正静止在指定锚点
 * state.awaitSettledAnchor(BottomSheetAnchor.Expanded)
 *
 * // 获取位置或当前吸附动画进度
 * state.expansionFraction // Collapsed 为 0，Expanded 为 1，Hidden 小于 0
 * state.transitionProgress // Settling 时为 0..1，其他状态为 null
 * ```
 *
 * @param onDismissRequest 遮罩、返回或下拉关闭的统一回调，默认折叠。
 * @param hideable 是否允许手势选择 Hidden 锚点；不限制显式调用 hide。
 * @param requestDismissOnDrag 手势选中关闭锚点时是否进入 [onDismissRequest]。
 */
@Stable
class BottomSheetState(
  var onDismissRequest: suspend BottomSheetState.() -> Unit = { collapseSuspend() },
  val hideable: Boolean = false,
  val requestDismissOnDrag: Boolean = true,
) {

  internal val showHeight = mutableFloatStateOf(0F)
  internal val showMaxHeight = mutableFloatStateOf(0F)

  /**
   * 当前吸附动画在 ScrollableState 坐标系中的速度，正值表示 Sheet 向下移动。
   *
   * 动画正常结束时清零；被新运动取消时保留最后一帧速度，使改向后的弹簧可以连续起步。
   */
  internal var animationVelocity = 0F

  /**
   * 折叠高度计算器，对外可读取包含父级剩余导航栏后的最终高度。
   *
   * 高度配置仍由 [BottomSheetCompose] 统一更新，调用方不应绕过组件修改计算结果。
   */
  val peekHeightUpdater = BottomSheetPeekHeightUpdater(this)

  /**
   * 最近一次真正到达的锚点流，只在稳定锚点发生变化时发送新值。
   * 等待指定锚点完成请使用 [awaitSettledAnchor]。
   */
  val settledAnchorFlow: StateFlow<BottomSheetAnchor>
    field = MutableStateFlow(BottomSheetAnchor.Collapsed)

  /** 最近一次真正到达的锚点；确认当前仍静止于该位置应使用 [isSettledAt]。 */
  var settledAnchor: BottomSheetAnchor by mutableStateOf(settledAnchorFlow.value)
    private set

  private val motionStateFlowInternal = MutableStateFlow<BottomSheetMotionState>(
    BottomSheetMotionState.Idle(BottomSheetAnchor.Collapsed)
  )

  /**
   * 当前完整运动状态流；响应中途改向时建议使用 `collectLatest` 取消旧方向处理。
   */
  val motionStateFlow: StateFlow<BottomSheetMotionState> get() = motionStateFlowInternal

  /** 当前运动状态的 Compose 可观察快照，适合在组合或派生状态中直接读取。 */
  var motionState: BottomSheetMotionState by mutableStateOf(motionStateFlowInternal.value)
    private set

  /** 当前是否已经静止在 [targetAnchor]，不会把正在离开该锚点的动画误判为已稳定。 */
  fun isSettledAt(targetAnchor: BottomSheetAnchor): Boolean {
    val motion = motionState
    return motion is BottomSheetMotionState.Idle && motion.anchor == targetAnchor
  }

  /** 命令通道承载 expand/collapse/hide 的一次性触发语义。 */
  internal val commandFlow = MutableStateFlow<BottomSheetAnchor?>(null)

  /**
   * 用户是否可以拖动 Sheet，或通过内部列表的嵌套滚动改变 Sheet 高度。
   * 该开关不会阻止 [expandAsync] 等程序化动作，也不会主动取消已经开始的动画。
   */
  val userScrollEnabled = mutableStateOf(true)

  /**
   * 当前位于折叠和展开锚点之间的空间比例；小于 0 表示已进入折叠到隐藏的区间。
   *
   * 该值只描述屏幕位置，不表示某一段动画完成了多少。
   */
  val expansionFraction by derivedStateOfStructure {
    val showHeight = showHeight.floatValue
    val showMaxHeight = showMaxHeight.floatValue
    val peekHeight = peekHeightUpdater.peekHeightPx
    if (showMaxHeight == 0F) 0F else (showHeight - peekHeight) / (showMaxHeight - peekHeight)
  }

  /**
   * 当前吸附分段从 [BottomSheetMotionState.Settling.startHeightPx] 到目标的完成比例。
   *
   * 拖动没有确定终点，因此仅在 Settling 阶段返回数值；静止或拖动时返回 null。
   */
  val transitionProgress: Float? by derivedStateOfStructure {
    val settling = motionState as? BottomSheetMotionState.Settling
      ?: return@derivedStateOfStructure null
    val distance = settling.targetHeightPx - settling.startHeightPx
    if (distance == 0F) {
      1F
    } else {
      ((showHeight.floatValue - settling.startHeightPx) / distance).coerceIn(0F, 1F)
    }
  }

  /**
   * 当前是否已经明确向关闭锚点运动。
   *
   * 可用于在视觉动画结束前提前撤销全屏背景的点击拦截。
   */
  val isClosing: Boolean
    get() = when (val motion = motionState) {
      is BottomSheetMotionState.Settling -> motion.targetAnchor != BottomSheetAnchor.Expanded
      is BottomSheetMotionState.Dragging,
      is BottomSheetMotionState.Idle -> false
    }

  internal val scrollableState = ScrollableState {
    val max = showMaxHeight.floatValue
    val now = showHeight.floatValue
    val new = (now - it).coerceIn(0F, max)
    showHeight.floatValue = new
    now - new
  }

  /** BottomSheet 统一使用的吸附弹簧。 */
  internal val bottomSheetSpring = spring<Float>(
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = 1F,
  )

  /** 完全移出屏幕时缩短弹簧尾段，使外层 Window 能更早释放触摸。 */
  private val closeBottomSheetSpring = spring<Float>(
    dampingRatio = bottomSheetSpring.dampingRatio,
    stiffness = bottomSheetSpring.stiffness,
    visibilityThreshold = 10F,
  )

  /**
   * 根据目标高度选择动画：完全退出屏幕时缩短尾段，保留常驻内容的折叠与展开使用标准曲线。
   */
  internal fun animationSpecFor(targetHeightPx: Float): SpringSpec<Float> {
    return if (targetHeightPx == 0F) closeBottomSheetSpring else bottomSheetSpring
  }

  private var nextTransitionId = 0L

  /**
   * 异步请求展开并立即返回，由已安装的 [BottomSheetCompose] 执行动画。
   * 调用方需要在展开完成后继续业务时，应改用 [expandSuspend]。
   */
  fun expandAsync() {
    commandFlow.value = BottomSheetAnchor.Expanded
  }

  /**
   * 挂起直到展开完成或者被后续运动取消。
   *
   * @throws CancellationException 被另一次折叠、隐藏或用户运动取消时抛出。
   */
  suspend fun expandSuspend() {
    if (isSettledAt(BottomSheetAnchor.Expanded)) {
      return
    }
    // iOS CMP Dialog 可能先执行展开、再触发 onSizeChanged，必须等到目标高度有效后才能建模动画。
    if (showMaxHeight.floatValue <= 0F) {
      snapshotFlow { showMaxHeight.floatValue }.first { it > 0F }
    }
    settleTo(
      targetAnchor = BottomSheetAnchor.Expanded,
      targetHeightPx = showMaxHeight.floatValue,
      source = BottomSheetSettleSource.Programmatic,
    )
  }

  /**
   * 异步请求折叠到有效 peekHeight 并立即返回，由已安装的 [BottomSheetCompose] 执行动画。
   * 需要等待完成时使用 [collapseSuspend] 或 [awaitSettledAnchor]。
   */
  fun collapseAsync() {
    commandFlow.value = BottomSheetAnchor.Collapsed
  }

  /**
   * 挂起直到折叠完成或者被后续运动取消。
   *
   * @throws CancellationException 被另一次展开、隐藏或用户运动取消时抛出。
   */
  suspend fun collapseSuspend() {
    if (isSettledAt(BottomSheetAnchor.Collapsed)) {
      return
    }
    settleTo(
      targetAnchor = BottomSheetAnchor.Collapsed,
      targetHeightPx = peekHeightUpdater.peekHeightPx,
      source = BottomSheetSettleSource.Programmatic,
    )
  }

  /**
   * 异步请求移动到屏幕外并立即返回，由已安装的 [BottomSheetCompose] 执行动画。
   * [hideable] 只限制手势锚点选择，不限制调用方显式执行该方法。
   */
  fun hideAsync() {
    commandFlow.value = BottomSheetAnchor.Hidden
  }

  /**
   * 挂起直到 Sheet 真正静止在 [targetAnchor]。
   *
   * 不能只等待 [settledAnchorFlow] 的值：从某个锚点离开后又返回同一锚点时，最近稳定锚点在
   * 运动期间本来就没有变化，单独判断它会在动画完成前提前返回。
   */
  suspend fun awaitSettledAnchor(targetAnchor: BottomSheetAnchor) {
    motionStateFlow.first { motion ->
      motion is BottomSheetMotionState.Idle && motion.anchor == targetAnchor
    }
  }

  /**
   * 挂起直到完全隐藏或者被后续运动取消。
   *
   * @throws CancellationException 被另一次展开、折叠或用户运动取消时抛出。
   */
  suspend fun hideSuspend() {
    if (isSettledAt(BottomSheetAnchor.Hidden)) {
      return
    }
    settleTo(
      targetAnchor = BottomSheetAnchor.Hidden,
      targetHeightPx = 0F,
      source = BottomSheetSettleSource.Programmatic,
    )
  }

  /**
   * 标记用户开始直接拖动，并使尚未结束的运动分段失效。
   *
   * @param source 拖动来自 Sheet 手柄还是内部嵌套滚动。
   */
  internal fun beginDrag(source: BottomSheetDragSource) {
    val current = motionState
    if (current is BottomSheetMotionState.Dragging && current.source == source) return
    nextTransitionId++
    animationVelocity = 0F
    updateMotionState(
      BottomSheetMotionState.Dragging(
        originAnchor = settledAnchor,
        startHeightPx = showHeight.floatValue,
        interruptedTargetAnchor = when (current) {
          is BottomSheetMotionState.Settling -> current.targetAnchor
          is BottomSheetMotionState.Dragging -> current.interruptedTargetAnchor
          is BottomSheetMotionState.Idle -> null
        },
        source = source,
      )
    )
  }

  /**
   * 建立一段吸附运动，并返回唯一编号。
   *
   * 改向时保留最近稳定锚点，同时以当前真实高度作为新分段起点；旧协程只能持有旧编号，无法再
   * 把状态错误落成旧目标。
   */
  internal fun beginSettling(
    targetAnchor: BottomSheetAnchor,
    targetHeightPx: Float,
    initialVelocity: Float,
    source: BottomSheetSettleSource,
  ): Long {
    val previousTargetAnchor = when (val current = motionState) {
      is BottomSheetMotionState.Settling -> current.targetAnchor
      is BottomSheetMotionState.Dragging -> current.interruptedTargetAnchor
      is BottomSheetMotionState.Idle -> null
    }
    val transitionId = ++nextTransitionId
    updateMotionState(
      BottomSheetMotionState.Settling(
        transitionId = transitionId,
        originAnchor = settledAnchor,
        startHeightPx = showHeight.floatValue,
        previousTargetAnchor = previousTargetAnchor,
        targetAnchor = targetAnchor,
        targetHeightPx = targetHeightPx,
        initialVelocityPxPerSecond = initialVelocity,
        source = source,
      )
    )
    return transitionId
  }

  /** 仅允许仍是当前分段的动画提交最终锚点。 */
  internal fun completeSettling(transitionId: Long, targetAnchor: BottomSheetAnchor) {
    val current = motionState as? BottomSheetMotionState.Settling ?: return
    if (current.transitionId != transitionId || current.targetAnchor != targetAnchor) return
    settledAnchor = targetAnchor
    settledAnchorFlow.value = targetAnchor
    updateMotionState(BottomSheetMotionState.Idle(targetAnchor))
  }

  /**
   * 用户恰好把 Sheet 拖到锚点且无需播放吸附动画时，提交该稳定锚点。
   *
   * 仅 Dragging 可以通过此入口结束，避免嵌套滚动在边界处留下一个永不结束的拖动态。
   */
  internal fun completeDrag(targetAnchor: BottomSheetAnchor) {
    if (motionState !is BottomSheetMotionState.Dragging) return
    nextTransitionId++
    animationVelocity = 0F
    settledAnchor = targetAnchor
    settledAnchorFlow.value = targetAnchor
    updateMotionState(BottomSheetMotionState.Idle(targetAnchor))
  }

  /**
   * 使用当前弹簧移动到指定锚点，并继承被接管动画的最后速度。
   */
  private suspend fun settleTo(
    targetAnchor: BottomSheetAnchor,
    targetHeightPx: Float,
    source: BottomSheetSettleSource,
  ) {
    val transitionId = beginSettling(
      targetAnchor = targetAnchor,
      targetHeightPx = targetHeightPx,
      initialVelocity = animationVelocity,
      source = source,
    )
    scrollableState.scroll {
      // 取得互斥权后，旧动画已经写入最后一帧速度；用真实接管值刷新当前分段元数据。
      val current = motionState as? BottomSheetMotionState.Settling
      if (current?.transitionId == transitionId) {
        updateMotionState(
          current.copy(
            startHeightPx = showHeight.floatValue,
            initialVelocityPxPerSecond = animationVelocity,
          )
        )
      }
      val targetScrollOffset = showHeight.floatValue - targetHeightPx
      if (abs(targetScrollOffset) < 1F) {
        scrollBy(targetScrollOffset)
        animationVelocity = 0F
        completeSettling(transitionId, targetAnchor)
        return@scroll
      }

      var consumedOffset = 0F
      AnimationState(
        initialValue = 0F,
        initialVelocity = animationVelocity,
      ).animateTo(
        targetValue = targetScrollOffset,
        animationSpec = animationSpecFor(targetHeightPx),
      ) {
        val requestedDelta = value - consumedOffset
        val consumedDelta = scrollBy(requestedDelta)
        consumedOffset += consumedDelta
        animationVelocity = velocity
        if (abs(requestedDelta - consumedDelta) >= 1F) {
          cancelAnimation()
        }
      }
      // 外部取消会直接抛出 CancellationException，不会清零或提交旧目标。
      animationVelocity = 0F
      completeSettling(transitionId, targetAnchor)
    }
  }

  /** 在有效折叠高度改变时同步当前显示高度。 */
  internal fun onPeekHeightChanged(value: Float) {
    when {
      motionState is BottomSheetMotionState.Idle &&
        settledAnchor == BottomSheetAnchor.Collapsed -> showHeight.floatValue = value
      motionState is BottomSheetMotionState.Idle &&
        settledAnchor == BottomSheetAnchor.Hidden -> Unit
      showHeight.floatValue < value -> showHeight.floatValue = value
    }
  }

  private fun updateMotionState(value: BottomSheetMotionState) {
    motionState = value
    motionStateFlowInternal.value = value
  }
}
