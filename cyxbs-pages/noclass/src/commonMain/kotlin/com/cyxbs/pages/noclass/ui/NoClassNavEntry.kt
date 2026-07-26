package com.cyxbs.pages.noclass.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_NO_CLASS
import com.cyxbs.pages.noclass.api.NoClassNavArgument
import com.cyxbs.pages.noclass.ui.noclass.NoClassPage
import com.cyxbs.pages.noclass.viewmodel.NoClassViewModel

/**
 * description ： 没课约的导航入口
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2026/5/1 17:10
 */
@AppNav(route = NAV_NO_CLASS)
class NoClassNavEntry : AppNavEntry<NoClassNavArgument>() {

    override fun isNeedLogin(argument: NoClassNavArgument): Boolean {
        return false
    }

    @Composable
    override fun Content(argument: NoClassNavArgument) {
        viewModel { NoClassViewModel() }
        NoClassPage(argument)
    }
}