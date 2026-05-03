package com.cyxbs.pages.noclass.ui.NoClass

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.navigation.DestinationParcel
import com.cyxbs.components.config.navigation.MainNavDestination
import com.cyxbs.components.config.navigation.NAV_NO_CLASS
import com.cyxbs.pages.noclass.api.NoClassArgument
import com.cyxbs.pages.noclass.viewmodel.NoClassViewModel
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * description ： TODO:没课约的导航地址
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2026/5/1 17:10
 */

@ImplProvider(clazz = MainNavDestination::class, name = NAV_NO_CLASS)
class NoClassNavDestination : MainNavDestination<NoClassArgument>(NoClassArgument::class) {
    override val needLogin: Boolean = false

    @Composable
    override fun DestinationContent(parcel: DestinationParcel<NoClassArgument>) {
        viewModel { NoClassViewModel() }
        NoClassPage()
    }
}