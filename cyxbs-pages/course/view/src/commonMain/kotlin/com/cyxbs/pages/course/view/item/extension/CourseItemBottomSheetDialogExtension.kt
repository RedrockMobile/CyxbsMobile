package com.cyxbs.pages.course.view.item.extension

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.view.ui.bottomsheet.BottomSheetAnchor
import com.cyxbs.components.view.ui.bottomsheet.BottomSheetMotionState
import com.cyxbs.pages.course.view.dialog.CourseBottomSheetContent
import com.cyxbs.pages.course.view.dialog.CourseBottomSheetDialog
import com.cyxbs.pages.course.view.dialog.CourseBottomSheetDialogState
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.modifier.BeginFinalTimeShowModifier
import com.cyxbs.pages.course.view.item.modifier.observeItemRectOnScreen
import com.cyxbs.pages.course.view.overlay.OverlapResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 点击课表 item 弹起的 BottomSheetDialog
 *
 * @author 985892345
 * @date 2025/3/29
 */
interface CourseItemBottomSheetDialogExtension : CourseItemExtension, CourseBottomSheetContent {

  val itemState: CourseItemState
}

val LocalCourseItemBottomSheetDialog =
  staticCompositionLocalOf<CourseItemBottomSheetDialogState> { error("未初始化") }

@Stable
@Composable
fun rememberCourseItemBottomSheetDialogState(): CourseItemBottomSheetDialogState {
  val state = remember {
    CourseItemBottomSheetDialogState()
  }
  CourseBottomSheetDialog(
    state = state,
    windowEffects = {
      ShowBeginFinalTime(state)
      CurrentItemShowTop(state)
      OffsetScroll(state)
    },
  )
  return state
}

/**
 * 课表 Item 入口对通用详情宿主的适配状态。
 *
 * 本类只负责把 [OverlapResult] 转换为按课表层级排序的详情内容；Window、BottomSheet 与 Pager 均由
 * [com.cyxbs.pages.course.view.dialog.CourseBottomSheetDialogState] 提供，独立入口不需要依赖本类。
 */
@Stable
class CourseItemBottomSheetDialogState :
  CourseBottomSheetDialogState<CourseItemBottomSheetDialogExtension>() {

  /** 当前选中的课表 Item 详情。 */
  internal val currentPageItemFlow: MutableStateFlow<CourseItemBottomSheetDialogExtension?>
    get() = currentPageContentFlow

  fun showDialog(extension: CourseItemBottomSheetDialogExtension) {
    showDialog(listOf(extension))
  }

  fun showDialog(overlapResult: OverlapResult?) {
    if (overlapResult == null) {
      dismissDialog()
    } else {
      showDialog(
        collectCoveredItems(
          rootItemState = overlapResult.itemState,
          otherOverlap = overlapResult,
          set = linkedSetOf(overlapResult.itemState),
        ).mapNotNull { it.item.extensions.get(CourseItemBottomSheetDialogExtension::class) },
      )
    }
  }

  private fun collectCoveredItems(
    rootItemState: CourseItemState,
    otherOverlap: OverlapResult,
    set: MutableSet<CourseItemState>,
  ): Set<CourseItemState> {
    otherOverlap.coveredItemList.fastForEach {
      val itemState = it.result.itemState
      val itemWhatTimeFixed = itemState.item.whatTime.now.value
      val rootWhatTimeFixed = rootItemState.item.whatTime.now.value
      if (itemWhatTimeFixed.beginTime < rootWhatTimeFixed.finalTime
        && itemWhatTimeFixed.finalTime > rootWhatTimeFixed.beginTime
      ) {
        set.add(itemState)
      }
      collectCoveredItems(rootItemState, it.result, set)
    }
    return set
  }
}

// 如果 item 被弹窗遮挡，则将滚轴向上移动
@Composable
@OptIn(ExperimentalCoroutinesApi::class)
private fun OffsetScroll(
  state: CourseItemBottomSheetDialogState,
) {
  val minItemTopSpacingPx = with(LocalDensity.current) { 20.dp.toPx() }
  val marginBottomKey = "MobileCourseBottomSheetDialog#OffsetScroll"
  val scrollContext = remember {
    state.dialogContents.value.first().itemState.coursePageFlow.value?.scrollContext
  }
  if (scrollContext == null) return // 如果是下一周的课程的话，则会有未初始化的情况
  val marginBottomState = remember {
    scrollContext.timeline.marginBottom
  }
  LaunchedEffect(state, minItemTopSpacingPx) {
    val initScrollValue = scrollContext.scrollState.value
    var settledBaseScrollValue = initScrollValue
    var hasSettledBaseScroll = false

    /**
     * 返回未施加弹窗避让时的课表滚动位置。
     *
     * 首次打开期间固定为点击前的位置；BottomSheet 首次展开后会根据 Item 的当前位置重新确定一次，
     * 之后切换 Item、拖拽和关闭均使用该固定值，避免滚动基准随布局反复变化。
     */
    fun baseScrollValue(): Int = settledBaseScrollValue.coerceIn(0, scrollContext.scrollState.maxValue)

    /** 返回 OffsetScroll 已经施加到课表上的完整偏移。 */
    fun currentOffset(): Float {
      val scrollOffset = scrollContext.scrollState.value - baseScrollValue()
      val marginOffset = marginBottomState.getOrElse(marginBottomKey) { 0 }
      return (scrollOffset + marginOffset).toFloat().coerceAtLeast(0F)
    }

    /**
     * 计算 Item 为避让 BottomSheet 所需的完整偏移。
     *
     * [itemRectOnScreen] 是已经撤销本逻辑自身偏移后的坐标。通常按 Item 底部与弹窗顶部的重叠量
     * 上移；若 Item 很高，则最多只移动到课表可视区顶部下方 20dp，避免标题和点击区域被推出课表。
     */
    fun calculateTargetOffset(
      itemRectOnScreen: Rect,
      layoutTopOnScreen: Float,
    ): Float {
      val overlapOffset = (itemRectOnScreen.bottom - layoutTopOnScreen).coerceAtLeast(0F)
      val outerCoordinates = scrollContext.outerCoordinates
      if (outerCoordinates == null || !outerCoordinates.isAttached) return overlapOffset
      val minItemTop = outerCoordinates.positionOnScreen().y + minItemTopSpacingPx
      val maxOffset = (itemRectOnScreen.top - minItemTop).coerceAtLeast(0F)
      return minOf(overlapOffset, maxOffset)
    }

    /**
     * 读取一次当前 Item 坐标并计算目标偏移。
     *
     * 坐标中会先撤销 OffsetScroll 自身已经施加的滚动和底部补偿，防止布局变化后以上一次偏移为
     * 新基准继续累加。调用方可传入 BottomSheet 当前帧的顶部坐标，使高度动画与 Item 位移同步。
     */
    suspend fun CourseItemBottomSheetDialogExtension.calculateTargetOffsetOnce(
      layoutTopOnScreen: Float,
    ): Float {
      val itemRectOnScreen = itemState.observeItemRectOnScreen(forceCalculate = true).first()
        .translate(
          translateX = 0F,
          translateY = currentOffset(),
        )
      return calculateTargetOffset(itemRectOnScreen, layoutTopOnScreen)
    }

    /**
     * BottomSheet 首次展开后确定关闭弹窗要恢复到的滚动位置。
     *
     * 如果 Item 在点击前的滚动位置本就可见，则继续沿用原位置；仅当最终 Item 底部超出课表视口时，
     * 才把基准向下移动到刚好可见。这样中午时间轴不会因为 `maxValue == 0` 被误判为需要追随底部，
     * 夜间时间轴关闭弹窗后也不会重新把 Item 留在屏幕外。
     */
    suspend fun trySettleBaselineAfterOpening() {
      if (hasSettledBaseScroll) return
      hasSettledBaseScroll = true
      val outerCoordinates = scrollContext.outerCoordinates ?: return
      if (!outerCoordinates.isAttached) return
      val currentItem = state.currentPageItemFlow.value ?: return
      if (scrollContext.scrollState.value == scrollContext.scrollState.maxValue) {
        // 如果展开后滚轴已经是最大值，则收回时也显示在最大值，因为晚上的 item 通过后面的 requiredScroll 的计算会少一些距离
        settledBaseScrollValue = scrollContext.scrollState.maxValue
        return
      }
      val currentRect = currentItem.itemState.observeItemRectOnScreen(forceCalculate = true).first()
      // 撤销当前弹窗避让，得到 Item 位于旧基准滚动位置时的最终布局坐标。
      val itemBottomWithoutAvoidance = currentRect.bottom + currentOffset()
      val viewportBottom = outerCoordinates.positionOnScreen().y + outerCoordinates.size.height
      val requiredScroll = (itemBottomWithoutAvoidance - viewportBottom)
        .coerceAtLeast(0F).roundToInt()
      settledBaseScrollValue = (settledBaseScrollValue + requiredScroll)
        .coerceIn(0, scrollContext.scrollState.maxValue)
    }

    /**
     * 立即应用完整偏移，优先使用课表本身可滚动的空间，不足的部分再交给底部补偿。
     *
     * 弹窗与时间轴本身已在逐帧动画时调用该方法，避免在外层再叠加一层动画。
     */
    suspend fun applyOffset(targetOffset: Float) {
      val target = targetOffset.coerceAtLeast(0F)
      val targetScrollValue = (baseScrollValue() + target.roundToInt())
        .coerceIn(0, scrollContext.scrollState.maxValue)
      scrollContext.scrollState.scrollBy(
        (targetScrollValue - scrollContext.scrollState.value).toFloat()
      )
      marginBottomState[marginBottomKey] = (
        target - (scrollContext.scrollState.value - baseScrollValue())
        ).roundToInt().coerceAtLeast(0)
    }

    /**
     * 将当前完整偏移动画到新 Item 的目标偏移。
     *
     * 滚动值和底部补偿必须共享同一动画进度，否则可滚动部分会先跳变，视觉上就像切换动画消失。
     */
    suspend fun animateOffset(targetOffset: Float) {
      val target = targetOffset.coerceAtLeast(0F)
      val initial = currentOffset()
      if (initial == target) return
      scrollContext.scrollState.scroll {
        val scrollScope = this
        animate(
          initialValue = initial,
          targetValue = target,
          animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        ) { value, _ ->
          val targetScrollValue = (baseScrollValue() + value.roundToInt())
            .coerceIn(0, scrollContext.scrollState.maxValue)
          scrollScope.scrollBy(
            (targetScrollValue - scrollContext.scrollState.value).toFloat()
          )
          marginBottomState[marginBottomKey] = (
            value - (scrollContext.scrollState.value - baseScrollValue())
            ).roundToInt().coerceAtLeast(0)
        }
      }
    }

    /** 按当前运动方向同步课程 Item 避让，改向时由 collectLatest 取消上一方向的观察。 */
    suspend fun observeMotion(closing: Boolean) {
      if (closing) {
        // 关闭时不再读取 Item；仅按照 BottomSheet 收起进度还原已经施加的偏移。
        val startFraction = state.bottomSheetState.expansionFraction.coerceAtLeast(0F)
        val startOffset = currentOffset()
        if (startFraction == 0F) {
          applyOffset(0F)
        } else {
          snapshotFlow { state.bottomSheetState.expansionFraction }.collect { fraction ->
            applyOffset(startOffset * (fraction / startFraction).coerceIn(0F, 1F))
          }
        }
      } else {
        // 打开期间同时跟随 BottomSheet 与时间轴动画，动画结束后立即停止观察。
    state.layoutTopOnScreenFlow.combine(
          state.currentPageItemFlow.filterNotNull()
            .flatMapLatest { extension ->
              extension.itemState.observeItemRectOnScreen(forceCalculate = true)
                .map { rect ->
                  // 排除本逻辑自身施加的滚动与 margin，防止坐标反馈造成上下闪动；
                  // 时间轴展开改变的真实布局坐标仍会保留。
                  rect.translate(
                    translateX = 0F,
                    translateY = (
                      marginBottomState.getOrElse(marginBottomKey) { 0 } +
                          scrollContext.scrollState.value - baseScrollValue()
                      ).toFloat(),
                  )
                }
            }
        ) { layoutTopOnScreen, itemRectOnScreen ->
          calculateTargetOffset(itemRectOnScreen, layoutTopOnScreen)
        }.collect { applyOffset(it) }
      }
    }

    state.bottomSheetState.motionStateFlow.collectLatest { motionState ->
      when (motionState) {
        is BottomSheetMotionState.Dragging -> {
          observeMotion(closing = motionState.originAnchor == BottomSheetAnchor.Expanded)
        }
        is BottomSheetMotionState.Settling -> {
          // 改向时以新目标为准，而不是继续沿用整条运动链的初始锚点。
          observeMotion(closing = motionState.targetAnchor != BottomSheetAnchor.Expanded)
        }
        is BottomSheetMotionState.Idle -> when (motionState.anchor) {
          BottomSheetAnchor.Expanded -> {
            trySettleBaselineAfterOpening()
            var currentItem = state.currentPageItemFlow.value
            // 弹窗内容高度变化时直接逐帧同步偏移；只有 Pager 切换 Item 时才使用独立动画。
            // combine 会在切换动画期间合并最新的弹窗坐标，动画完成后再按最终高度立即校准。
        state.layoutTopOnScreenFlow.combine(
              state.currentPageItemFlow.filterNotNull()
            ) { layoutTopOnScreen, item ->
              layoutTopOnScreen to item
            }.collect { (layoutTopOnScreen, item) ->
              val targetOffset = item.calculateTargetOffsetOnce(layoutTopOnScreen)
              if (item === currentItem) {
                applyOffset(targetOffset)
              } else {
                currentItem = item
                animateOffset(targetOffset)
              }
            }
          }
          BottomSheetAnchor.Collapsed,
          BottomSheetAnchor.Hidden -> {
            if (currentOffset() != 0F) {
              applyOffset(0F)
            }
          }
        }
      }
    }
  }
  DisposableEffect(Unit) {
    onDispose {
      // 在弹窗消失时强制重置 marginBottom
      marginBottomState[marginBottomKey] = 0
    }
  }
}

// 显示 item 开始结束时间
@Composable
private fun ShowBeginFinalTime(
  state: CourseItemBottomSheetDialogState,
) {
  LaunchedEffect(Unit) {
    var unlockRunnable: Runnable? = null
    var isLockWhenBegin: Boolean? = null
    state.currentPageItemFlow.filterNotNull().map {
      it.itemState
    }.onCompletion {
      unlockRunnable?.run()
    }.collectLatest { itemState ->
      unlockRunnable?.run()
      if (isLockWhenBegin == null) {
        isLockWhenBegin = BeginFinalTimeShowModifier.showLock.get(itemState).isLocked() // 如果为 true 则说明已经可见
      }
      unlockRunnable = BeginFinalTimeShowModifier.showLock.get(itemState).lock().let {
        Runnable {
          // 包裹一层用于还原 alphaState
          it.run()
          BeginFinalTimeShowModifier.alphaState.get(itemState).floatValue = 1F
        }
      }
      if (!isLockWhenBegin) {
        // 如果最开始已经锁定，说明已经在展示开始结束时间了，那就不主动关联上透明度变化
        BeginFinalTimeShowModifier.alphaState.get(itemState).floatValue = 0F
        snapshotFlow { state.bottomSheetState.expansionFraction.coerceIn(0F, 1F) }.collect {
          BeginFinalTimeShowModifier.alphaState.get(itemState).floatValue = it
        }
      }
    }
  }
}

// 点击后的 item 置顶全显示
@Composable
private fun CurrentItemShowTop(
  state: CourseItemBottomSheetDialogState,
) {
  LaunchedEffect(Unit) {
    var lastItem: CourseItemState? = null
    val showAllInterceptor = CourseItemState.ShowRangeTransformer { _, overlap ->
      // item 被遮挡的区域都显示出来
      val whatTimeFixed = overlap.itemState.item.whatTime.now.value
      val beginTime = whatTimeFixed.beginTime
      val finalTime = whatTimeFixed.finalTime
      listOf(MinuteTimePair(beginTime, finalTime))
    }

    fun reset() {
      lastItem?.zIndexState?.floatValue--
      lastItem?.removeShowRangeTransformer(showAllInterceptor)
      lastItem = null
    }

    fun setItem() {
      if (lastItem != null) return
      val item = state.currentPageItemFlow.value?.itemState
      lastItem = item
      item?.zIndexState?.floatValue++ // 置顶展示
      item?.addShowRangeTransformer(showAllInterceptor)
    }
    launch {
      state.currentPageItemFlow.onCompletion {
        reset() // 协程作用域被取消时调用，此时 Compose 组件被移除
      }.collect {
        reset()
        setItem()
      }
    }
    launch {
      // 因为底部弹窗关闭时存在动画，导致需要一定时间才会触发 onCompletion 的 reset
      // 所以单独监听滚动距离来检测是否需要 reset
      // todo 后续想办法修下这个弹窗关闭动画过长的问题
      snapshotFlow { state.bottomSheetState.expansionFraction.coerceIn(0F, 1F) }.collect {
        if (it < 0.2F) reset() else setItem()
      }
    }
  }
}
