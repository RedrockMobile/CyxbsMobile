package com.cyxbs.pages.mine.about.mine.ui

import androidx.compose.runtime.Composable
import com.cyxbs.components.config.navigation.DestinationParcel
import com.cyxbs.components.config.navigation.MainDestination
import com.cyxbs.components.config.navigation.NAV_ABOUT_ENTRY
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.serialization.Serializable

/**
 * @Desc : 关于我们页面
 * @Author : zzx
 * @Date : 2025/10/27 15:42
 */

// 关于我们的路由参数
@Serializable
object AboutArgument

@ImplProvider(clazz = MainDestination::class, name = NAV_ABOUT_ENTRY)
class AboutDestination : MainDestination<AboutArgument>(AboutArgument::class) {
    @Composable
    override fun DestinationContent(parcel: DestinationParcel<AboutArgument>) {

    }
}