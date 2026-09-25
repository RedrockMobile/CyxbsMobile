package com.cyxbs.pages.course.view.dialog

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.LocalImePaddingTargetState
import com.cyxbs.components.utils.compose.imePaddingWithTarget
import com.cyxbs.components.utils.compose.plusDsl
import com.cyxbs.components.utils.compose.rememberImePaddingTargetState
import com.cyxbs.components.view.ui.Window
import com.cyxbs.components.view.ui.bottomsheet.BottomSheetAnchor
import com.cyxbs.components.view.ui.bottomsheet.BottomSheetCompose
import com.cyxbs.components.view.ui.bottomsheet.BottomSheetState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.hypot
import kotlin.math.max

/** 通用课程详情内容，不持有课表 Item、重叠关系或课表滚动状态。 */
interface CourseBottomSheetContent {

  /** 用于跨列表刷新复用当前内容状态；同一弹窗内必须唯一。 */
  val contentKey: Any
    get() = this

  /** 在统一 BottomSheet 宿主中绘制业务内容。 */
  @Composable
  fun CourseBottomSheetDialogContent(scope: CourseBottomSheetScope)
}

/**
 * 业务内容可使用的最小宿主能力。
 *
 * 该接口刻意不暴露课表 Item，因而同一份课程或日程内容既能放在课表内，也能放在 Widget Activity 中。
 */
interface CourseBottomSheetScope {

  /** 请求宿主播放收起动画并在动画结束后释放内容。 */
  fun dismissDialogAnimated()

  /** 编辑开始后锁定当前内容，禁止切换到同一重叠区域的其他条目。 */
  fun lockCurrentPage()

  /** 注册或清除业务关闭拦截；返回 false 时宿主会回弹到展开态。 */
  fun updateDismissRequestGate(gate: (suspend () -> Boolean)?)

  /** 更新绘制在宿主 Window 最上层的业务弹层。 */
  fun updateWindowOverlayContent(content: (@Composable () -> Unit)?)
}

private val DefaultCourseBottomSheetHeight = 280.dp

/**
 * 与具体入口无关的课程详情 BottomSheet 状态。
 *
 * [onDismissed] 仅在内容完成收起并被清空后触发，Activity 宿主可据此结束自身；替换内容不会触发。
 */
@Stable
open class CourseBottomSheetDialogState<T : CourseBottomSheetContent>(
  private val onDismissed: () -> Unit = {},
) : CourseBottomSheetScope {

  val dialogContents: MutableStateFlow<List<T>> = MutableStateFlow(emptyList())

  private var dismissRequestGate: (suspend () -> Boolean)? = null
  private val windowOverlayContent = mutableStateOf<(@Composable () -> Unit)?>(null)

  val bottomSheetState = BottomSheetState(
    onDismissRequest = {
      // 业务内容可挂起关闭并展示确认；继续编辑后才回弹，放弃后完成收起。
      if (dismissRequestGate?.invoke() != false) collapseSuspend() else expandSuspend()
    },
  )

  /** 当前 Pager 正在展示的内容。 */
  val currentPageContentFlow: MutableStateFlow<T?> = MutableStateFlow(null)

  /** BottomSheet 顶部在屏幕中的纵坐标，由布局更新，课表等入口可按需订阅。 */
  internal val layoutTopOnScreenFlow = MutableSharedFlow<Float>(
    replay = 1,
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )

  // 编辑当前内容时锁定 Pager；保留原列表，避免切换布局分支后重建并丢失表单状态。
  internal val currentPageLockedFlow = MutableStateFlow(false)

  /**
   * 替换并显示一组详情内容。
   *
   * 空列表等价于直接关闭；调用方应按希望展示的 Pager 顺序传入已经去重的内容。
   */
  fun showDialog(contents: List<T>) {
    if (contents.isEmpty()) {
      dismissDialog()
      return
    }
    clearTransientState()
    dialogContents.value = contents
  }

  /** 立即释放内容；正常交互应优先调用 [dismissDialogAnimated]。 */
  fun dismissDialog() {
    val hadContent = dialogContents.value.isNotEmpty()
    clearTransientState()
    dialogContents.value = emptyList()
    if (hadContent) onDismissed()
  }

  override fun dismissDialogAnimated() {
    bottomSheetState.collapseAsync()
  }

  override fun lockCurrentPage() {
    if (currentPageContentFlow.value != null) currentPageLockedFlow.value = true
  }

  override fun updateDismissRequestGate(gate: (suspend () -> Boolean)?) {
    dismissRequestGate = gate
  }

  override fun updateWindowOverlayContent(content: (@Composable () -> Unit)?) {
    windowOverlayContent.value = content
  }

  /** 在宿主 Window 根布局末尾绘制当前业务弹层。 */
  @Composable
  internal fun WindowOverlayContent() {
    windowOverlayContent.value?.invoke()
  }

  /** 清理只属于上一轮内容的状态，但不触发 Activity 等外层宿主退出。 */
  protected fun clearTransientState() {
    dismissRequestGate = null
    windowOverlayContent.value = null
    bottomSheetState.userScrollEnabled.value = true
    currentPageContentFlow.value = null
    currentPageLockedFlow.value = false
  }
}

/** 创建独立入口使用的通用详情状态，并始终调用最新的 [onDismissed]。 */
@Composable
fun rememberCourseBottomSheetDialogState(
  onDismissed: () -> Unit,
): CourseBottomSheetDialogState<CourseBottomSheetContent> {
  val latestOnDismissed by rememberUpdatedState(onDismissed)
  return remember {
    CourseBottomSheetDialogState { latestOnDismissed() }
  }
}

/**
 * 绘制统一的课程详情 Window、BottomSheet、重叠分页器和业务浮层。
 *
 * [windowEffects] 是课表入口的可选适配点；BottomSheet 坐标联动由 [CourseBottomSheetDialogState]
 * 自身处理，独立 Activity 使用默认状态即可完全脱离课表滚轴、Item 置顶和起止时间动画。
 */
@Composable
fun <T : CourseBottomSheetContent> CourseBottomSheetDialog(
  state: CourseBottomSheetDialogState<T>,
  windowEffects: @Composable () -> Unit = {},
) {
  state.dialogContents.collectAsState().value.firstOrNull() ?: return
  Window(
    // 返回键交给 BottomSheetState，与蒙层点击共用业务关闭拦截。
    dismissOnBackPress = null,
  ) {
    val imePaddingTargetState = rememberImePaddingTargetState()
    CompositionLocalProvider(LocalImePaddingTargetState provides imePaddingTargetState) {
      // 只上移到目标编辑区域完整可见，弹窗其余部分仍允许被键盘覆盖。
      Box(modifier = Modifier.imePaddingWithTarget(imePaddingTargetState)) {
        windowEffects()
        BottomSheet(state)
        // 业务确认层必须位于可拖动 BottomSheet 之外，收起后仍能立即显示。
        state.WindowOverlayContent()
      }
    }
  }
}

/** 绘制通用 BottomSheet 外观，并把顶部屏幕坐标交给课表入口做 Item 避让。 */
@Composable
private fun <T : CourseBottomSheetContent> BottomSheet(
  state: CourseBottomSheetDialogState<T>,
) {
  val bottomSheetBackgroundColor = LocalAppColors.current.whiteBlack
  BottomSheetCompose(
    bottomSheetState = state.bottomSheetState,
    dismissOnClickOutside = true,
    scrimColor = Color.Transparent,
    navigationBarContent = { Spacer(Modifier.fillMaxSize().background(bottomSheetBackgroundColor)) },
  ) {
    val currentPageLocked by state.currentPageLockedFlow.collectAsState()
    val shadowHeightPx = with(LocalDensity.current) { 36.dp.toPx() }
    val shadowBrush = remember(shadowHeightPx) {
      // 阴影高度固定，仅在 density 变化时重建 Brush，内容尺寸动画不会产生重复分配。
      Brush.verticalGradient(
        colors = listOf(Color(0x005369BC), Color(0x205369BC)),
        endY = shadowHeightPx,
      )
    }
    Box(
      modifier = Modifier
        // 背景必须画在尺寸动画外层；子项的 matchParentSize 会先跳到目标高度，收缩期间会透出课表。
        .drawBehind {
          val top = 20.dp.toPx().coerceAtMost(size.height)
          val fillHeight = size.height - top
          val radius = minOf(16.dp.toPx(), fillHeight / 2F)
          val shadowHeight = shadowHeightPx.coerceAtMost(size.height)
          drawRect(
            brush = shadowBrush,
            size = Size(size.width, shadowHeight),
          )
          drawRoundRect(
            color = bottomSheetBackgroundColor,
            topLeft = Offset(0F, top),
            size = Size(size.width, fillHeight),
            cornerRadius = CornerRadius(radius),
          )
          // 只保留顶部圆角；导航栏区域由 navigationBarContent 使用相同颜色补齐。
          if (fillHeight > radius) {
            drawRect(
              color = bottomSheetBackgroundColor,
              topLeft = Offset(0F, top + radius),
              size = Size(size.width, fillHeight - radius),
            )
          }
        }
        .fillMaxWidth()
        .then(
          if (currentPageLocked) Modifier.heightIn(min = DefaultCourseBottomSheetHeight)
          else Modifier.height(DefaultCourseBottomSheetHeight)
        )
        .animateContentSize()
        .then(bottomSheetDraggable())
        .onGloballyPositioned {
          state.layoutTopOnScreenFlow.tryEmit(it.positionOnScreen().y)
        },
    ) {
      Box(
        modifier = Modifier.padding(top = 20.dp)
          .fillMaxWidth()
          .then(if (currentPageLocked) Modifier else Modifier.fillMaxSize())
          .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
      ) {
        CourseBottomSheetDialogContent(state)
      }
    }
  }
  LaunchedEffect(Unit) {
    try {
      state.bottomSheetState.expandSuspend()
    } catch (_: CancellationException) {
      // 展开动画中快速点击空白区域会取消 expandSuspend，随后仍需等待收起完成。
    }
    state.bottomSheetState.awaitSettledAnchor(BottomSheetAnchor.Collapsed)
    state.dismissDialog()
  }
}

/** 绘制单条内容或重叠内容 Pager，并在编辑锁定时保留当前 Composable 状态。 */
@Composable
private fun <T : CourseBottomSheetContent> CourseBottomSheetDialogContent(
  state: CourseBottomSheetDialogState<T>,
) {
  val dialogContents by state.dialogContents.collectAsState()
  val currentPageLocked by state.currentPageLockedFlow.collectAsState()
  val currentPageContent by state.currentPageContentFlow.collectAsState()
  if (dialogContents.isEmpty()) return
  val movableContents = remember(state) {
    mutableMapOf<Pair<Int, Any>, @Composable () -> Unit>()
  }
  fun movableContent(page: Int, content: T): @Composable () -> Unit =
    movableContents.getOrPut(page to content.contentKey) {
      movableContentOf { content.CourseBottomSheetDialogContent(state) }
    }
  val pagerState = rememberPagerState(
    initialPage = if (dialogContents.size == 1) 0 else dialogContents.size * 1000,
  ) {
    if (dialogContents.size == 1) 1 else Int.MAX_VALUE
  }
  LaunchedEffect(dialogContents) {
    snapshotFlow { pagerState.currentPage }.collect { page ->
      state.currentPageContentFlow.value = dialogContents[page % dialogContents.size]
    }
  }
  Column(
    modifier = if (currentPageLocked) Modifier.fillMaxWidth() else Modifier.fillMaxSize(),
  ) {
    if (currentPageLocked) {
      val content = currentPageContent ?: dialogContents.first()
      val contentPage = if (dialogContents.size == 1) 0 else pagerState.currentPage
      movableContent(contentPage, content).invoke()
    } else if (dialogContents.size > 1) {
      HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxWidth().weight(1F),
        verticalAlignment = Alignment.Top,
      ) { page ->
        val content = dialogContents.getOrNull(page % dialogContents.size)
        content?.let { movableContent(page, it).invoke() }
      }
    } else {
      val content = dialogContents.firstOrNull()
      if (content != null) {
        key(content.contentKey) {
          movableContent(0, content).invoke()
        }
      }
    }
    Spacer(
      modifier = Modifier.fillMaxWidth()
        .height(if (currentPageLocked) 0.dp else 24.dp)
        .plusDsl {
          if (dialogContents.size > 1 && !currentPageLocked) {
            drawWithCache {
              val radius = 4.dp.toPx()
              val interval = 16.dp.toPx()
              val beginX = size.width / 2 - (dialogContents.size - 1) * interval / 2
              val beginY = size.height / 2
              val path1 = Path()
              val path2 = Path()
              onDrawBehind {
                val itemCount = dialogContents.size
                val currentPage = pagerState.currentPage
                val currentPageOffset = pagerState.currentPageOffsetFraction
                val absoluteOffset = currentPage + currentPageOffset
                val relativeOffset = if (absoluteOffset % itemCount > itemCount - 1) {
                  (1 - (absoluteOffset - absoluteOffset.toInt())) * (itemCount - 1)
                } else {
                  absoluteOffset % itemCount
                }
                repeat(itemCount) {
                  drawCircle(Color(0xFF888888), radius, Offset(beginX + it * interval, beginY))
                }
                val relativeOffsetInt = relativeOffset.toInt()
                val path = getWaterDropIndicator(
                  path1 = path1,
                  path2 = path2,
                  radius = radius,
                  fraction = relativeOffset - relativeOffsetInt,
                  interval = interval,
                )
                path.translate(Offset(beginX + relativeOffsetInt * interval, beginY))
                drawPath(path, Color(0xFF788EFA))
              }
            }
          }
        },
    )
  }
}

/** 生成重叠页底部的水滴形分页指示器路径。 */
private fun getWaterDropIndicator(
  path1: Path,
  path2: Path,
  radius: Float,
  fraction: Float,
  interval: Float,
): Path {
  path1.rewind()
  path2.rewind()
  val outerX = interval / 2
  val outerY = interval
  val outerR = hypot(outerX, outerY) - radius
  val nowX = fraction * interval
  val nowR = hypot(outerX - nowX, outerY) - outerR
  path1.addRoundRect(RoundRect(Rect(Offset(nowX, 0F), nowR), CornerRadius(nowR)))
  val startMove = 0.6F
  val k = 1 / (1 - startMove)
  val b = 1 - k
  val followX = max(0F, k * fraction + b) * interval
  val followR = hypot(outerX - followX, outerY) - outerR
  path1.addRoundRect(RoundRect(Rect(Offset(followX, 0F), followR), CornerRadius(followR)))
  path2.moveTo(nowX, nowR)
  path2.lineTo(nowX, -nowR)
  path2.lineTo(followX, -followR)
  path2.lineTo(followX, followR)
  path2.close()
  path1.op(path1, path2, PathOperation.Union)
  path2.rewind()
  path2.addRoundRect(RoundRect(Rect(Offset(outerX, outerY), outerR), CornerRadius(outerR)))
  path2.addRoundRect(RoundRect(Rect(Offset(outerX, -outerY), outerR), CornerRadius(outerR)))
  path1.op(path1, path2, PathOperation.Difference)
  return path1
}
