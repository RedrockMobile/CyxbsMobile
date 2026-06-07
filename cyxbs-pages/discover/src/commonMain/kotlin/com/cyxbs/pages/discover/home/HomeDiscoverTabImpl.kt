package com.cyxbs.pages.discover.home

import androidx.compose.runtime.Composable
import com.cyxbs.pages.home.api.IHomeDiscoverTab
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * 「发现」tab 在主页内的内容供给方实现
 *
 * 通过 @ImplProvider 注册到 KtProvider，home 模块以 `IHomeDiscoverTab::class.implOrNull()`
 * 取到本实现，从而在 HorizontalPager 中嵌入 [DiscoverPage]，无需直接依赖 discover 模块。
 */
@ImplProvider
object HomeDiscoverTabImpl : IHomeDiscoverTab {

  @Composable
  override fun Content() {
    DiscoverPage()
  }
}
