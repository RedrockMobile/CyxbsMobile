package com.cyxbs.pages.course.dialog

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.WindowInsetsRulers
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.utils.compose.Wrapper
import com.cyxbs.components.utils.compose.plusDsl
import com.cyxbs.components.utils.compose.rememberDerivedStateOfStructure
import com.cyxbs.components.view.ui.BottomSheetCompose
import com.cyxbs.components.view.ui.BottomSheetState
import com.cyxbs.components.view.ui.BottomSheetValueState
import com.cyxbs.components.view.ui.Window
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.drawBeginFinalTimeline
import com.cyxbs.pages.course.view.item.extension.CourseItemExtension
import com.cyxbs.pages.course.view.item.modifier.observeItemRectInWindow
import com.cyxbs.pages.course.view.overlay.OverlapResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 点击课表 item 弹起的 BottomSheetDialog
 *
 * @author 985892345
 * @date 2025/3/29
 */
interface CourseItemBottomSheetDialogExtension : CourseItemExtension {

  val itemState: CourseItemState

  @Composable
  fun CourseBottomSheetDialogContent(state: CourseItemBottomSheetDialogState)
}

val LocalCourseItemBottomSheetDialog =
  staticCompositionLocalOf<CourseItemBottomSheetDialogState> { error("未初始化") }

@Stable
@Composable
fun rememberCourseItemBottomSheetDialogState(): CourseItemBottomSheetDialogState {
  val state = remember {
    CourseItemBottomSheetDialogState()
  }
  MobileCourseBottomSheetDialog(state) // 这里注册了 Dialog
  return state
}

@Stable
class CourseItemBottomSheetDialogState {

  val dialogContents: MutableStateFlow<List<CourseItemBottomSheetDialogExtension>> =
    MutableStateFlow(emptyList())

  val bottomSheetState = BottomSheetState()

  // 当前选中的 item
  val currentPageItemFlow: MutableStateFlow<CourseItemBottomSheetDialogExtension?> =
    MutableStateFlow(null)

  // 键盘弹出时需要漏出的位置
  val imePeekLayoutInWindowBottomFlow = MutableStateFlow(0F)

  fun showDialog(extension: CourseItemBottomSheetDialogExtension) {
    clear()
    dialogContents.value = listOf(extension)
  }

  fun showDialog(overlapResult: OverlapResult?) {
    if (overlapResult == null) {
      dismissDialog()
    } else {
      clear()
      dialogContents.value = collectCoveredItems(
        rootItemState = overlapResult.itemState,
        otherOverlap = overlapResult,
        set = linkedSetOf(overlapResult.itemState)
      ).mapNotNull { it.item.extension as? CourseItemBottomSheetDialogExtension }
    }
  }

  fun dismissDialog() {
    clear()
    dialogContents.value = emptyList()
  }

  private fun clear() {
    bottomSheetState.userScrollEnabled.value = true
    currentPageItemFlow.value = null
    imePeekLayoutInWindowBottomFlow.value = 0F
  }

  // 键盘弹出时需要漏出的位置
  // 设置后键盘弹出时仅遮挡该位置下面的区域，而暴露 bottom 之前的部分
  fun setImePeekBottomInWindow(bottomInWindow: Float) {
    imePeekLayoutInWindowBottomFlow.value = bottomInWindow
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

/**
 * 移动端课表 item 点击后出现的 BottomSheetDialog
 */
@Composable
private fun MobileCourseBottomSheetDialog(
  state: CourseItemBottomSheetDialogState,
) {
  state.dialogContents.collectAsState().value.firstOrNull() ?: return
  Window(
    dismissOnBackPress = {
      state.dismissDialog()
    }
  ) {
    val height = 280.dp
    val imeOverlapHeight = remember { mutableIntStateOf(0) }
    Box(
      modifier = Modifier.imePadding(imeOverlapHeight)
    ) {
      ShowBeginFinalTime(state)
      CurrentItemShowTop(state)
      BottomSheet(state, height, imeOverlapHeight)
    }
  }
}

/**
 * @param overlapHeight 键盘最终状态与布局重叠的高度
 */
private fun Modifier.imePadding(
  overlapHeight: IntState,
  onFraction: ((fraction: Float, imeOffset: Float) -> Unit)? = null
): Modifier {
  return layout { measure, constraints ->
    val placeable = measure.measure(constraints)
    layout(placeable.width, placeable.height) {
      val ime = WindowInsetsRulers.Ime
      val animationProperties = ime.getAnimation(this)
      if (animationProperties.isVisible) {
        // 键盘可见
        val height = placeable.height.toFloat()
        val sourceBottom = animationProperties.source.bottom.current(height)
        val currentBottom = ime.current.bottom.current(height)
        if (animationProperties.isAnimating) {
          // 键盘上升或下降动画中
          val targetBottom = animationProperties.target.bottom.current(height)
          val top = minOf(sourceBottom, targetBottom)
          val bottom = maxOf(sourceBottom, targetBottom)
          val imeHeight = bottom - top
          val fraction = (bottom - currentBottom) / imeHeight
          val offset = overlapHeight.intValue - imeHeight
          onFraction?.invoke(fraction, offset)
          placeable.place(x = 0, y = (offset * fraction).roundToInt())
        } else {
          // 键盘完全展开
          val imeHeight = abs(sourceBottom - currentBottom)
          val offset = overlapHeight.intValue - imeHeight
          onFraction?.invoke(1F, offset)
          placeable.place(x = 0, y = offset.roundToInt())
        }
      } else {
        // 键盘不可见
        onFraction?.invoke(0F, 0F)
        placeable.place(x = 0, y = 0)
      }
    }
  }
}

// 如果 item 被弹窗遮挡，则将滚轴向上移动
@Composable
private fun OffsetScroll(
  state: CourseItemBottomSheetDialogState,
  layoutCoordinatesFlow: SharedFlow<LayoutCoordinates>
) {
  LaunchedEffect(Unit) {
    val scrollContext = state.dialogContents.value.first().itemState.coursePage.scrollContext
    val marginBottomState = scrollContext.timeline.marginBottom
    val marginBottomKey = "MobileCourseBottomSheetDialog#OffsetScroll"
    val initScrollValue = scrollContext.scrollState.value
    var prevItem = state.currentPageItemFlow.value
    layoutCoordinatesFlow.filterNotNull().map {
      it.positionInWindow().y
    }.combine(
      state.currentPageItemFlow.filterNotNull().map {
        it.itemState.observeItemRectInWindow().first()
          // 这里需要减去 margin 转换为初始坐标，在被重叠的 item 显示时有用
          .translate(0F, marginBottomState.getOrElse(marginBottomKey) { 0 }.toFloat())
      }
    ) { layoutOffsetInWindow, itemRectInWindow ->
      itemRectInWindow.bottom - layoutOffsetInWindow
    }.collectLatest {
      val total = it.coerceAtLeast(0F)
      val newScrollValue = initScrollValue + total
      val nowScrollValue = scrollContext.scrollState.value
      if (newScrollValue > nowScrollValue) {
        scrollContext.scrollState.scrollBy(newScrollValue - nowScrollValue)
      }
      val oldMarginBottom = marginBottomState.getOrElse(marginBottomKey) { 0 }
      val newMarginBottom = (total - (scrollContext.scrollState.value - initScrollValue))
        .roundToInt().coerceAtLeast(0)
      if (oldMarginBottom != newMarginBottom) {
        if (state.bottomSheetState.state == BottomSheetValueState.Expanded
          && prevItem != state.currentPageItemFlow.value
        ) {
          // 切换 item 时需要进行偏移
          prevItem = state.currentPageItemFlow.value
          animate(
            initialValue = oldMarginBottom,
            targetValue = newMarginBottom,
            typeConverter = Int.VectorConverter,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
          ) { value, _ ->
            marginBottomState[marginBottomKey] = value
          }
        } else {
          marginBottomState[marginBottomKey] = newMarginBottom
        }
      }
    }
  }
}

@Composable
private fun BottomSheet(
  state: CourseItemBottomSheetDialogState,
  height: Dp,
  imeOverlapHeight: MutableIntState,
) {
  val layoutCoordinatesFlow = remember {
    MutableSharedFlow<LayoutCoordinates>(
      replay = 1,
      extraBufferCapacity = 1,
      onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
  }
  val hasFocusFlow = remember { MutableStateFlow(false) }
  val outerLayoutCoordinates = remember { Wrapper<LayoutCoordinates?>(null) }
  BottomSheetCompose(
    bottomSheetState = state.bottomSheetState,
    dismissOnClickOutside = true,
    scrimColor = Color.Transparent,
    modifier = Modifier.onGloballyPositioned {
      outerLayoutCoordinates.value = it
    }
  ) {
    OffsetScroll(state, layoutCoordinatesFlow)
    Box(
      modifier = Modifier.navigationBarsPadding()
        .fillMaxWidth()
        .height(height)
        .onGloballyPositioned {
          layoutCoordinatesFlow.tryEmit(it)
        }.onFocusChanged {
          hasFocusFlow.value = it.hasFocus
        }
    ) {
      Spacer(
        modifier = Modifier.fillMaxWidth().height(36.dp).background(
          brush = Brush.verticalGradient(
            colors = listOf(Color(0x005369BC), Color(0x205369BC))
          )
        )
      )
      Box(
        modifier = Modifier.padding(top = 20.dp)
          .fillMaxSize()
          .then(bottomSheetDraggable())
          .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
          .background(LocalAppColors.current.whiteBlack)
      ) {
        CourseBottomSheetDialogContent(state)
      }
    }
  }
  LaunchedEffect(Unit) {
    try {
      state.bottomSheetState.expand()
    } catch (e: CancellationException) {
      // 在展开动画时用户可能快速点击空白区域触发 collapse()，这里就会抛出 CancellationException
    }
    state.bottomSheetState.stateFlow.first { it == BottomSheetValueState.Collapsed }
    state.dismissDialog()
  }
  LaunchedEffect(Unit) {
    hasFocusFlow.filter { it }.mapNotNull {
      outerLayoutCoordinates.value
    }.flatMapLatest { outerLayoutCoordinates ->
      // 使用 layoutCoordinatesFlow 会少一个导航栏的高度
      state.imePeekLayoutInWindowBottomFlow.map {
        outerLayoutCoordinates.positionInWindow().y + outerLayoutCoordinates.size.height - it
      }
    }.collect {
      imeOverlapHeight.intValue = it.roundToInt()
    }
  }
}

@Composable
private fun ShowBeginFinalTime(
  state: CourseItemBottomSheetDialogState,
) {
  val currentPageItemFlow = state.currentPageItemFlow
  val alphaState = rememberDerivedStateOfStructure {
    state.bottomSheetState.fraction.coerceIn(0F, 1F)
  }
  val time1 = remember {
    mutableStateOf(
      currentPageItemFlow.value?.itemState?.item?.whatTime?.beginTime
        ?: MinuteTime(0, 0)
    )
  }
  val time2 = remember {
    mutableStateOf(
      currentPageItemFlow.value?.itemState?.item?.whatTime?.finalTime
        ?: MinuteTime(0, 0)
    )
  }
  val itemRectState = remember {
    currentPageItemFlow.mapNotNull { it?.itemState }.onEach {
      time1.value = it.item.whatTime.now.value.beginTime
      time2.value = it.item.whatTime.now.value.finalTime
    }.flatMapLatest { itemState ->
      itemState.observeItemRectInWindow()
    }
  }.collectAsState(null)
  Spacer(
    modifier = Modifier.layout { measure, constraints ->
      val width = itemRectState.value?.width?.roundToInt() ?: 0
      val height = itemRectState.value?.height?.roundToInt() ?: 0
      val placeable = measure.measure(Constraints.fixed(width, height))
      layout(constraints.maxWidth, constraints.maxHeight) {
        val rect = itemRectState.value ?: return@layout
        val coordinates = coordinates ?: return@layout
        val localToWindow = coordinates.localToWindow(Offset.Zero)
        placeable.place(
          x = (rect.left - localToWindow.x).roundToInt(),
          y = (rect.top - localToWindow.y).roundToInt(),
        )
      }
    }.drawBeginFinalTimeline(
      alpha = alphaState,
      time1 = time1,
      time2 = time2,
    )
  )
}

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
      snapshotFlow { state.bottomSheetState.fraction.coerceIn(0F, 1F) }.collect {
        if (it < 0.2F) reset() else setItem()
      }
    }
  }
}

@Composable
private fun CourseBottomSheetDialogContent(
  state: CourseItemBottomSheetDialogState,
) {
  val itemDialogContents by state.dialogContents.collectAsState()
  if (itemDialogContents.isEmpty()) return
  val pagerState = rememberPagerState(
    initialPage = if (itemDialogContents.size == 1) 0 else itemDialogContents.size * 1000,
  ) {
    if (itemDialogContents.size == 1) 1 else Int.MAX_VALUE
  }
  LaunchedEffect(Unit) {
    snapshotFlow { pagerState.currentPage }.collect {
      state.currentPageItemFlow.value =
        itemDialogContents[pagerState.currentPage % itemDialogContents.size]
    }
  }
  Column(modifier = Modifier.fillMaxSize()) {
    HorizontalPager(
      state = pagerState,
      modifier = Modifier.fillMaxWidth().weight(1F),
    ) { page ->
      val itemDialogContent = if (itemDialogContents.isEmpty()) null
      else itemDialogContents[page % itemDialogContents.size]
      itemDialogContent?.CourseBottomSheetDialogContent(state)
    }
    // 底部的圆点指示器
    Spacer(modifier = Modifier.fillMaxWidth().height(24.dp).plusDsl {
      if (itemDialogContents.size > 1) {
        drawWithCache {
          val radius = 4.dp.toPx()
          val interval = 16.dp.toPx()
          val beginX = size.width / 2 - (itemDialogContents.size - 1) * interval / 2
          val beginY = size.height / 2
          val path1 = Path()
          val path2 = Path()
          onDrawBehind {
            val itemCount = itemDialogContents.size
            val currentPage = pagerState.currentPage
            val currentPageOffset = pagerState.currentPageOffsetFraction
            val absoluteOffset = currentPage + currentPageOffset
            val relativeOffset = if (absoluteOffset % itemCount > itemCount - 1) { // 当划出边界时
              (1 - (absoluteOffset - absoluteOffset.toInt())) * (itemCount - 1) // 这里可以表示从右边界到左边界(或相反)经过的值
            } else absoluteOffset % itemCount
            repeat(itemCount) {
              drawCircle(Color(0xFF888888), radius, Offset(beginX + it * interval, beginY))
            }
            val relativeOffsetInt = relativeOffset.toInt()
            val path = getWaterDropIndicator(
              path1,
              path2,
              radius,
              relativeOffset - relativeOffsetInt,
              interval
            )
            path.translate(Offset(beginX + relativeOffsetInt * interval, beginY))
            drawPath(path, Color(0xFF788EFA))
          }
        }
      }
    })
  }
}

// 基本思路是两个圆点之间的上下方有两个半径很大的圆, 小圆点就在这两个大圆之间被挤压着移动
private fun getWaterDropIndicator(
  path1: Path,
  path2: Path,
  radius: Float,
  fraction: Float, // 0.0 -> 1.0
  interval: Float,
): Path {
  path1.rewind()
  path2.rewind()
  // 中间大圆的坐标
  val outerX = interval / 2
  val outerY = interval
  val outerR = hypot(outerX, outerY) - radius
  // 绘制当前移动点的圆
  val nowX = fraction * interval
  val nowR = hypot(outerX - nowX, outerY) - outerR
  path1.addRoundRect(RoundRect(Rect(Offset(nowX, 0F), nowR), CornerRadius(nowR)))
  // 绘制跟随移动的圆
  val startMove = 0.6F
  val k = 1 / (1 - startMove)
  val b = 1 - k
  val followX = max(0F, k * fraction + b) * interval
  val followR = hypot(outerX - followX, outerY) - outerR
  path1.addRoundRect(RoundRect(Rect(Offset(followX, 0F), followR), CornerRadius(followR)))
  // 与两个圆上下端点构成的四边形相并
  path2.moveTo(nowX, nowR)
  path2.lineTo(nowX, -nowR)
  path2.lineTo(followX, -followR)
  path2.lineTo(followX, followR)
  path2.close()
  path1.op(path1, path2, PathOperation.Union)
  // 排除上下两个大圆
  path2.rewind()
  path2.addRoundRect(RoundRect(Rect(Offset(outerX, outerY), outerR), CornerRadius(outerR)))
  path2.addRoundRect(RoundRect(Rect(Offset(outerX, -outerY), outerR), CornerRadius(outerR)))
  path1.op(path1, path2, PathOperation.Difference)
  return path1
}
