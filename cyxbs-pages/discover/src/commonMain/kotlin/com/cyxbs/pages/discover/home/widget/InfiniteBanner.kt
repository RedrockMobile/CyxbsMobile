package com.cyxbs.pages.discover.home.widget

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

/**
 * Banner 公共配置（对齐原 mSlideShow.setAutoSlideTime(1200, 6000) 等设置）。
 */
object BannerConfig {
  /** 翻页动画时长（对应原 setAutoSlideTime 的第一个参数 1200ms） */
  const val FlipDurationMillis: Int = 1200

  /** 自动翻页停留时长（对应原 setAutoSlideTime 的第二个参数 6000ms） */
  const val AutoFlipIntervalMillis: Long = 6000L

  /** 进入页面时的淡入动画时长（对应 mSlideShow.animate().alpha(1F).duration = 600） */
  const val EnterFadeMillis: Int = 600

  /** 还原 ScaleInTransformer 的最小缩放比 */
  const val MinScale: Float = 0.85f
}

private const val VIRTUAL_PAGE_COUNT = 100_000

/**
 * 无限循环 Banner 状态。把 [PagerState.pageCount] 设为一个很大的常量
 * （[VIRTUAL_PAGE_COUNT]）并对真实下标取模来模拟「循环」，规避了
 * [HorizontalPager] 自身没有循环参数的限制。
 */
@Composable
fun rememberInfiniteBannerState(itemCount: Int): InfiniteBannerState {
  val initialPage = if (itemCount <= 0) {
    0
  } else {
    // 取一个 itemCount 整数倍的中位数作为初始 virtual page，
    // 保证 realPage = initialPage % itemCount = 0
    (VIRTUAL_PAGE_COUNT / 2).let { mid -> mid - mid % itemCount }
  }
  val pagerState = rememberPagerState(
    initialPage = initialPage,
    pageCount = { if (itemCount <= 0) 0 else VIRTUAL_PAGE_COUNT },
  )
  return remember(pagerState, itemCount) { InfiniteBannerState(pagerState, itemCount) }
}

@Stable
class InfiniteBannerState internal constructor(
  val pagerState: PagerState,
  val itemCount: Int,
) {
  fun realPage(virtualPage: Int): Int =
    if (itemCount <= 0) 0 else (virtualPage % itemCount + itemCount) % itemCount
}

/**
 * 无限循环 Banner。content 提供给业务渲染实际的一页内容。
 *
 * 翻页动画使用 [BannerConfig.FlipDurationMillis] 时长 + [LinearOutSlowInEasing]
 * （Compose 端最接近 Android `DecelerateInterpolator` 的减速曲线）。
 *
 * 每个 page 通过 [scaleInGraphicsLayer] 还原原 ScaleInTransformer 的「左右两侧缩小」效果。
 */
@Composable
fun InfiniteBanner(
  itemCount: Int,
  modifier: Modifier = Modifier,
  state: InfiniteBannerState = rememberInfiniteBannerState(itemCount),
  autoFlipIntervalMillis: Long = BannerConfig.AutoFlipIntervalMillis,
  content: @Composable (realPage: Int) -> Unit,
) {
  // 自动翻页：每 autoFlipIntervalMillis 推进一页；用户触摸打断 animateScrollToPage 时
  // animateScrollToPage 会抛 CancellationException —— 这里吞掉它，下一轮 delay 后继续，
  // 不会因为用户摸过一次就再也不滚了。LaunchedEffect 本身的取消通过 isActive 退出。
  LaunchedEffect(state, itemCount, autoFlipIntervalMillis) {
    if (itemCount <= 1) return@LaunchedEffect
    while (coroutineContext.isActive) {
      delay(autoFlipIntervalMillis)
      // 用户正在拖动时跳过这一轮，下一轮再尝试
      if (state.pagerState.isScrollInProgress) continue
      try {
        state.pagerState.animateScrollToPage(
          page = state.pagerState.currentPage + 1,
          animationSpec = tween(
            durationMillis = BannerConfig.FlipDurationMillis,
            easing = LinearOutSlowInEasing,
          ),
        )
      } catch (_: CancellationException) {
        // 触摸打断时，animateScrollToPage 会抛 CancellationException。
        // 这里不能直接吞掉真正的协程取消，否则 LaunchedEffect 无法退出 ——
        // 用 ensureActive 把「父 scope 被取消」的情况重新抛出。
        if (!coroutineContext.isActive) throw kotlin.coroutines.cancellation.CancellationException()
      }
    }
  }
  HorizontalPager(
    state = state.pagerState,
    modifier = modifier,
  ) { virtualPage ->
    val realPage = state.realPage(virtualPage)
    Box(
      modifier = Modifier
        .fillMaxSize()
        .scaleInGraphicsLayer(state.pagerState, virtualPage),
    ) {
      content(realPage)
    }
  }
}

/**
 * 还原原 ScaleInTransformer：当 page 离中心位置 1 个 page 时缩放到 [BannerConfig.MinScale]。
 */
private fun Modifier.scaleInGraphicsLayer(pagerState: PagerState, page: Int): Modifier =
  this.graphicsLayer {
    val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
    val factor = 1f - abs(pageOffset).coerceIn(0f, 1f)
    val scale = lerp(BannerConfig.MinScale, 1f, factor)
    scaleX = scale
    scaleY = scale
  }
