package com.cyxbs.pages.sport.service

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cyxbs.pages.sport.api.ISportService
import com.cyxbs.pages.sport.ui.SportFeed as SportFeedComposable
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * [ISportService] 的具体实现，注册到 KtProvider。
 *
 * feed UI 在 commonMain，而点击卡片跳转体育打卡详情页依赖 Android Activity 路由，
 * 只能在 androidMain 完成，故在这里把跳转能力注入到 commonMain 的 [SportFeedComposable]。
 */
@ImplProvider
object SportServiceImpl : ISportService {

  @Composable
  override fun SportFeed(modifier: Modifier) {
    SportFeedComposable(
      modifier = modifier,
      onJumpDetail = { jumpSportDetail() },
    )
  }
}

internal expect fun jumpSportDetail()