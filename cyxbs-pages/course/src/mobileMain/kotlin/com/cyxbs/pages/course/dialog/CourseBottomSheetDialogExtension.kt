package com.cyxbs.pages.course.dialog

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.MinuteTime
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 点击课表 item 弹起的 BottomSheetDialog
 *
 * @author 985892345
 * @date 2025/3/29
 */
interface CourseBottomSheetDialogExtension : CourseItemExtension {

  val itemState: CourseItemState

  @Composable
  fun CourseBottomSheetDialogContent()
}

@Stable
@Composable
fun rememberCourseBottomSheetDialogState(): CourseBottomSheetDialogState {
  val state = remember {
    CourseBottomSheetDialogState()
  }
  MobileCourseBottomSheetDialog(state) // 这里注册了 Dialog
  return state
}

@Stable
class CourseBottomSheetDialogState {

  val dialogContents: MutableStateFlow<List<CourseBottomSheetDialogExtension>> =
    MutableStateFlow(emptyList())

  val bottomSheetState = BottomSheetState()

  val currentPageItemFlow: MutableStateFlow<CourseBottomSheetDialogExtension?> = MutableStateFlow(null)

  fun showDialog(extension: CourseBottomSheetDialogExtension) {
    dialogContents.value = listOf(extension)
  }

  fun showDialog(overlapResult: OverlapResult?) {
    if (overlapResult == null) {
      dialogContents.value = emptyList()
    } else {
      dialogContents.value = collectCoveredItems(
        rootItemState = overlapResult.itemState,
        otherOverlap = overlapResult,
        set = linkedSetOf(overlapResult.itemState)
      ).mapNotNull { it.item.extension as? CourseBottomSheetDialogExtension }
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

/**
 * 移动端课表 item 点击后出现的 BottomSheetDialog
 */
@Composable
private fun MobileCourseBottomSheetDialog(
  state: CourseBottomSheetDialogState,
) {
  state.dialogContents.collectAsState().value.firstOrNull() ?: return
  Window(
    dismissOnBackPress = {
      state.dialogContents.value = emptyList()
    }
  ) {
    val height = 280.dp
    Box {
      OffsetScroll(state, height)
      ShowBeginFinalTime(state)
      BottomSheet(state, height)
    }
  }
}

// 如果 item 被弹窗遮挡，则将滚轴向上移动
@Composable
private fun BoxScope.OffsetScroll(
  state: CourseBottomSheetDialogState,
  height: Dp,
) {
  val layoutCoordinatesFlow = remember { MutableStateFlow<LayoutCoordinates?>(null) }
  Spacer( // 模拟底部弹窗展开时的显示位置，如果 item 被弹窗遮挡，则将滚轴向上移动
    modifier = Modifier.align(Alignment.BottomStart)
      .navigationBarsPadding()
      .fillMaxWidth()
      .height(height)
      .onGloballyPositioned {
        layoutCoordinatesFlow.value = it
      }
  )
  LaunchedEffect(Unit) {
    val marginBottomState = state.dialogContents.value.first().itemState.coursePage.scrollContext.marginBottom
    combine(
      layoutCoordinatesFlow.filterNotNull(),
      state.currentPageItemFlow.filterNotNull()
        .map { it.itemState.observeItemRectInWindow().first() },
    ) { layoutCoordinates, itemRectInWindow ->
      val layoutOffsetInWindow = layoutCoordinates.positionInWindow()
      val itemBottomInWindow = itemRectInWindow.bottom
      if (itemBottomInWindow < 0) -1F else {
        // 如果 itemBottomInWindow < 0，则说明 item 已经在窗口外，不需要移动
        itemBottomInWindow - layoutOffsetInWindow.y
      }
    }.filter { it > 0 }.map {
      it + marginBottomState.intValue
    }.collectLatest { newMargin ->
      if (state.currentPageItemFlow.value == state.dialogContents.value.firstOrNull()) {
        // 首个 item 打开时监听 bottomSheetState.fraction
        snapshotFlow { state.bottomSheetState.fraction.coerceIn(0F, 1F) }.collect {
          marginBottomState.intValue = (it * newMargin).roundToInt()
        }
      } else {
        // 切换了 item 则单独用动画移动
        supervisorScope {
          val animateJob = launch {
            animate(
              initialValue = marginBottomState.intValue.toFloat(),
              targetValue = newMargin,
              animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ) { value, _ ->
              marginBottomState.intValue = value.roundToInt()
            }
          }
          val lastFraction = state.bottomSheetState.fraction
          snapshotFlow { state.bottomSheetState.fraction }.first { it < lastFraction } // 直到第一次向下移动，所以开始 Collapsed
          animateJob.cancel() // 如果在切换 item 动画中立马触发关闭，就需要取消动画
          snapshotFlow { state.bottomSheetState.fraction.coerceIn(0F, 1F) }.collect {
            marginBottomState.intValue = (it * newMargin).roundToInt()
          }
        }
      }
    }
  }
}

@Composable
private fun BottomSheet(
  state: CourseBottomSheetDialogState,
  height: Dp,
) {
  BottomSheetCompose(
    bottomSheetState = state.bottomSheetState,
    dismissOnClickOutside = true,
    scrimColor = Color.Transparent,
  ) {
    Box(
      modifier = Modifier.navigationBarsPadding()
        .fillMaxWidth()
        .height(height)
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
        CourseBottomSheetDialogContent(state.dialogContents.value, state.currentPageItemFlow)
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
    state.dialogContents.value = emptyList()
  }
}

@Composable
private fun ShowBeginFinalTime(
  state: CourseBottomSheetDialogState,
) {
  val currentPageItemFlow = state.currentPageItemFlow
  val alphaState = rememberDerivedStateOfStructure {
    state.bottomSheetState.fraction.coerceIn(0F, 1F)
  }
  val time1 = remember {
    mutableStateOf(currentPageItemFlow.value?.itemState?.item?.whatTime?.beginTime ?: MinuteTime(0, 0))
  }
  val time2 = remember {
    mutableStateOf(currentPageItemFlow.value?.itemState?.item?.whatTime?.finalTime ?: MinuteTime(0, 0))
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
private fun CourseBottomSheetDialogContent(
  itemDialogContents: List<CourseBottomSheetDialogExtension>,
  currentPageItemFlow: MutableStateFlow<CourseBottomSheetDialogExtension?>,
) {
  val pagerState = rememberPagerState(
    initialPage = if (itemDialogContents.size == 1) 0 else itemDialogContents.size * 1000,
  ) {
    if (itemDialogContents.size == 1) 1 else Int.MAX_VALUE
  }
  LaunchedEffect(Unit) {
    snapshotFlow { pagerState.currentPage }.collect {
      currentPageItemFlow.value = itemDialogContents[pagerState.currentPage % itemDialogContents.size]
    }
  }
  Column(modifier = Modifier.fillMaxSize()) {
    HorizontalPager(
      state = pagerState,
      modifier = Modifier.fillMaxWidth().weight(1F),
    ) {
      itemDialogContents[it % itemDialogContents.size].CourseBottomSheetDialogContent()
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
