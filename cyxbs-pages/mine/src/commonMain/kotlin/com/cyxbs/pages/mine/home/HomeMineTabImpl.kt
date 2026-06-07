package com.cyxbs.pages.mine.home

import androidx.compose.runtime.Composable
import com.cyxbs.pages.home.api.IHomeMineTab
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * 「我的」tab 在主页内的内容供给方实现
 *
 * 通过 @ImplProvider 注册到 KtProvider，home 模块以 `IHomeMineTab::class.implOrNull()`
 * 取到本实现，从而在 HorizontalPager 中嵌入 [MinePage]，无需直接依赖 mine 模块。
 */
@ImplProvider
object HomeMineTabImpl : IHomeMineTab {

  @Composable
  override fun Content() {
    MinePage()
  }
}
