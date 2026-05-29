package com.cyxbs.pages.noclass.ui.groupdetail

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.navigation.DestinationParcel
import com.cyxbs.components.config.navigation.MainNavDestination
import com.cyxbs.components.config.navigation.NAV_GROUP_DETAIL
import com.cyxbs.pages.noclass.api.GroupDetailArgument
import com.cyxbs.pages.noclass.viewmodel.GroupDetailViewModel
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * 分组详情页导航
 * @author summer_palace2
 * @date 2026/5/3 19:34
 */
@ImplProvider(clazz = MainNavDestination::class, name = NAV_GROUP_DETAIL)
class GroupDetailNavDestination : MainNavDestination<GroupDetailArgument>(GroupDetailArgument::class) {
    override val needLogin: Boolean = false

    @Composable
    override fun DestinationContent(parcel: DestinationParcel<GroupDetailArgument>) {
        val arg = parcel.argument
        viewModel(key = arg.groupId) {
            GroupDetailViewModel(groupId = arg.groupId, groupName = arg.groupName)
        }
        GroupDetailPage(arg)
    }
}
