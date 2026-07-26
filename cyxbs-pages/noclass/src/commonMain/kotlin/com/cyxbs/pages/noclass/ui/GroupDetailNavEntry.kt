package com.cyxbs.pages.noclass.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_NO_CLASS_GROUP_DETAIL
import com.cyxbs.pages.noclass.api.GroupDetailNavArgument
import com.cyxbs.pages.noclass.ui.groupdetail.GroupDetailPage
import com.cyxbs.pages.noclass.viewmodel.GroupDetailViewModel

/**
 * 分组详情页导航
 * @author summer_palace2
 * @date 2026/5/3 19:34
 */
@AppNav(route = NAV_NO_CLASS_GROUP_DETAIL)
class GroupDetailNavEntry : AppNavEntry<GroupDetailNavArgument>() {

    override fun isNeedLogin(argument: GroupDetailNavArgument): Boolean {
        return false
    }

    @Composable
    override fun Content(argument: GroupDetailNavArgument) {
        viewModel(key = argument.groupId) {
            GroupDetailViewModel(groupId = argument.groupId, groupName = argument.groupName)
        }
        GroupDetailPage(argument)
    }
}