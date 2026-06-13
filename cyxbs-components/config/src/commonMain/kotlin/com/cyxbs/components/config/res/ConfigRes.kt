package com.cyxbs.components.config.res

import androidx.compose.ui.text.font.FontFamily
import cyxbsmobile.cyxbs_components.config.generated.resources.Res
import cyxbsmobile.cyxbs_components.config.generated.resources.config_ic_circle_add
import cyxbsmobile.cyxbs_components.config.generated.resources.config_ic_compose_app_logo
import cyxbsmobile.cyxbs_components.config.generated.resources.config_ic_compose_back
import cyxbsmobile.cyxbs_components.config.generated.resources.config_ic_compose_place_holder
import cyxbsmobile.cyxbs_components.config.generated.resources.config_ic_default_avatar
import org.jetbrains.compose.resources.DrawableResource

/**
 * @Desc : 对外暴露的公共资源（图片 / 字体）
 * @Author : zzx
 * @Date : 2025/10/29 13:21
 */

object ConfigRes {
    fun configIcAppLogo() : DrawableResource = Res.drawable.config_ic_compose_app_logo
    fun configIcBack() : DrawableResource = Res.drawable.config_ic_compose_back
    fun configIcPlaceHolder() : DrawableResource = Res.drawable.config_ic_compose_place_holder
    fun configIcDefaultAvatar(): DrawableResource = Res.drawable.config_ic_default_avatar
    fun configIcCircleAdd(): DrawableResource = Res.drawable.config_ic_circle_add

    /**
     * Impact 字体（用于电费、课时分数等数字强调样式）。
     *
     * - Android 端返回基于 `res/font/impact_min.ttf` 的 [FontFamily]
     * - 其他平台暂未提供对应字体文件，返回 `null`；调用方根据是否为 null
     *   决定要不要给 Text 设置 `fontFamily`，否则会回退到平台默认字体
     */
    fun impactFontFamily(): FontFamily? = platformImpactFontFamily()
}