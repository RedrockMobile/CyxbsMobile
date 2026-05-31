package com.cyxbs.pages.ufield.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.cyxbs.components.base.ui.BaseFragment
import com.cyxbs.components.config.compose.theme.AppTheme
import com.cyxbs.components.config.route.FAIRGROUND_ENTRY
import com.cyxbs.components.config.route.QA_ENTRY
import com.cyxbs.components.config.route.UFIELD_MAIN_ENTRY
import com.cyxbs.components.config.service.startActivity
import com.cyxbs.pages.ufield.fairground.FairgroundNavPlatform
import com.cyxbs.pages.ufield.fairground.FairgroundPage
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * 邮乐园入口页 Fragment（作为 Home 的 tab 加载）。
 *
 * 页面已迁移为 commonMain 的 [com.cyxbs.pages.ufield.fairground.FairgroundNavEntry]，
 * 这里仅作为 [ComposeView] 宿主复用同一份 Compose 内容 [FairgroundPage]，内容完全还原。
 *
 * author : 苟云东
 * email : 2191288460@qq.com
 * date : 2023/8/26 14:59
 */
@ImplProvider(clazz = Fragment::class, name = FAIRGROUND_ENTRY)
class FairgroundPageFragment : BaseFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    FairgroundPage()
                }
            }
        }
    }
}

@ImplProvider
object FairgroundNavPlatformImpl : FairgroundNavPlatform {
    override fun jumpQaEntry() {
        startActivity(QA_ENTRY)
    }

    override fun jumpUfieldMainEntry() {
        startActivity(UFIELD_MAIN_ENTRY)
    }
}