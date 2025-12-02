package com.cyxbs.pages.map.widget

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cyxbs.components.utils.extensions.logg
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sign

/**
 * @Desc : Banner
 * @Author : zzx
 * @Date : 2025/12/1 14:59
 */

@Composable
fun rememberBannerPagerState(
  pageCount: Int,
  isScrollInfinite: Boolean = false,
  initialPage: Int = 0
): PagerState {
  val count = if (isScrollInfinite) Int.MAX_VALUE else pageCount
  return rememberPagerState(
    initialPage = count / 2 - (count / 2) % pageCount + initialPage,
    pageCount = { count }
  )
}

@Composable
fun BannerCompose(
  pageCount: Int,
  pagerState: PagerState,
  modifier: Modifier = Modifier,
  isAutoScroll: Boolean = false,
  scrollTime: Long = 3000,
  scrollDuration: Int = 2000,
  contentPadding: PaddingValues = PaddingValues(0.dp),
  pageSpacing: Dp = 0.dp,
  beyondViewportPageCount: Int = 3,
  pageContent: @Composable PagerScope.(page: Int, virtualPage: Int) -> Unit
) {
  HorizontalPager(
    state = pagerState,
    modifier = modifier,
    contentPadding = contentPadding,
    pageSpacing = pageSpacing,
    beyondViewportPageCount = beyondViewportPageCount
  ) { index ->
    // 这里同时返回了list中对应的index以及在无限大中的index
    // 这个index可用于在计算滑动动画时与pagerState.currentPage的差值
    pageContent(index % pageCount, index)
  }
  // 这里不能用currentPage，否则走一半就会跳转
  LaunchedEffect(pagerState.settledPage, isAutoScroll) {
    if (isAutoScroll) {
      delay(scrollTime)
      // 这里判断以下不开启无限循环&&count==2,1的特殊情况
      val nextPage = if (pagerState.settledPage + 1 < pagerState.pageCount) {
        pagerState.settledPage + 1
      } else {
        if (pagerState.settledPage - 1 < 0) pagerState.settledPage
        else pagerState.settledPage - 1
      }
      pagerState.animateScrollToPage(
        page = nextPage,
        animationSpec = tween(
          durationMillis = scrollDuration,
          easing = FastOutSlowInEasing
        )
      )
    }
  }
}

const val MIN_SCALE = 0.85f
const val MAX_SCALE = 1f

fun Modifier.bannerTransition(pagerState: PagerState, page: Int) =
  graphicsLayer {
    val pageOffset = pagerState.currentPageOffsetFraction + (pagerState.currentPage - page)
    val scale = MIN_SCALE + (1f - abs(pageOffset)).coerceIn(0f, 1f) * (MAX_SCALE - MIN_SCALE)
    val reduceWidth = size.width * (1 - scale) / 2F
    scaleX = scale
    scaleY = scale
    // 修正因为放缩导致的偏移量
    translationX = sign(pageOffset) * reduceWidth
  }

@Composable
fun BannerIndicatorCompose(
  pagerState: PagerState,
  count: Int,
  modifier: Modifier = Modifier,
  radius: Dp = 3.dp,
  selectedWidth: Dp = 20.dp,
  space: Dp = 20.dp,
  shadow: Dp = 2.dp,
  indicatorColor: Color = Color.White,
  shadowColor: Color = Color(0X44000000)
) {
  val diameter = radius * 2
  val totalWidth = selectedWidth + diameter * (count - 1) + space * (count - 1)
  /*
  current selectedWidth
  (selectedWidth - diameter) * (1 - fraction) + diameter

  selectedWidth - (selectedWidth - diameter) * (1 - fraction)
   */
  Row(
    modifier = modifier.width(totalWidth),
    horizontalArrangement = Arrangement.spacedBy(space),
    verticalAlignment = Alignment.CenterVertically
  ) {
    repeat(count) { index ->
      val currentPage = pagerState.currentPage % count
      val nextPage =
        (currentPage + 1 * sign(pagerState.currentPageOffsetFraction).toInt() + count) % count
      logg("$currentPage $nextPage ${pagerState.currentPageOffsetFraction}")
      val width = when (index) {
        currentPage -> (selectedWidth - diameter) * (1 - abs(pagerState.currentPageOffsetFraction)) + diameter
        nextPage -> selectedWidth - (selectedWidth - diameter) * (1 - abs(pagerState.currentPageOffsetFraction))
        else -> diameter
      }
      Box(
        modifier = Modifier
          .width(width)
          .height(radius * 2)
          .shadow(
            elevation = shadow,
            shape = CircleShape,
            clip = false,
            spotColor = shadowColor
          )
          .background(
            color = indicatorColor,
            shape = CircleShape
          )
      )
    }
  }
}