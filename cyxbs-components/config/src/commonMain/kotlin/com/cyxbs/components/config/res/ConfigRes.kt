package com.cyxbs.components.config.res

import cyxbsmobile.cyxbs_components.config.generated.resources.Res
import cyxbsmobile.cyxbs_components.config.generated.resources.config_ic_compose_app_logo
import cyxbsmobile.cyxbs_components.config.generated.resources.config_ic_compose_back
import cyxbsmobile.cyxbs_components.config.generated.resources.config_ic_compose_place_holder
import org.jetbrains.compose.resources.DrawableResource

/**
 * @Desc : 对外暴露的公共图片
 * @Author : zzx
 * @Date : 2025/10/29 13:21
 */

object ConfigRes {
    fun configIcAppLogo() : DrawableResource = Res.drawable.config_ic_compose_app_logo
    fun configIcBack() : DrawableResource = Res.drawable.config_ic_compose_back
    fun configIcPlaceHolder() : DrawableResource = Res.drawable.config_ic_compose_place_holder
}