package com.cyxbs.pages.noclass.ui

import androidx.compose.runtime.Composable
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_NO_CLASS_BATCH_ADD
import com.cyxbs.pages.noclass.api.BatchAddNavArgument
import com.cyxbs.pages.noclass.ui.noclass.NoclassBatchAddPage

/**
 * description ： 没课约-批量添加页导航入口
 * author : whj
 * date : 2026/10/25
 */
@AppNav(route = NAV_NO_CLASS_BATCH_ADD)
class BatchAddNavEntry : AppNavEntry<BatchAddNavArgument>() {

    override fun isNeedLogin(argument: BatchAddNavArgument): Boolean {
        return false
    }

    @Composable
    override fun Content(argument: BatchAddNavArgument) {
        NoclassBatchAddPage()
    }
}
