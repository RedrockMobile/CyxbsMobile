package com.cyxbs.pages.discover.pages.discover

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import com.cyxbs.components.base.ui.BaseFragment
import com.cyxbs.components.config.compose.theme.AppTheme
import com.cyxbs.components.config.route.DISCOVER_ENTRY
import com.cyxbs.pages.discover.home.DiscoverPage
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * 发现入口页 Fragment（作为 Home 的 tab 加载）。
 *
 * 页面已迁移为 commonMain 的 [com.cyxbs.pages.discover.home.DiscoverNavEntry]，
 * 这里仅作为 [ComposeView] 宿主复用同一份 Compose 内容 [DiscoverPage]，
 * 同时把老的 Feed Fragment（Sport / Todo / Electricity）以 [AndroidView] 形式注入回页面。
 *
 * 老的 XML / Adapter / ViewModel 暂不删除，待后续单独清理。
 */
@ImplProvider(clazz = Fragment::class, name = DISCOVER_ENTRY)
class DiscoverHomeFragment : BaseFragment() {

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        AppTheme {
          DiscoverPage()
        }
      }
    }
  }
}


