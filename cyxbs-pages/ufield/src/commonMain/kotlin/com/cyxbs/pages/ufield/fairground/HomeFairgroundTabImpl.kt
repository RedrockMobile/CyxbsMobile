package com.cyxbs.pages.ufield.fairground

import androidx.compose.runtime.Composable
import com.cyxbs.pages.home.api.IHomeFairgroundTab
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * 「邮乐园」tab 在主页内的内容供给方实现
 *
 * 通过 @ImplProvider 注册到 KtProvider，home 模块以 `IHomeFairgroundTab::class.implOrNull()`
 * 取到本实现，从而在 HorizontalPager 中嵌入 [FairgroundPage]，无需直接依赖 ufield 模块。
 */
@ImplProvider
object HomeFairgroundTabImpl : IHomeFairgroundTab {

  @Composable
  override fun Content() {
    FairgroundPage()
  }
}
