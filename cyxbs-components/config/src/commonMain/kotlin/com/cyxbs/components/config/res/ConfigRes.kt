package com.cyxbs.components.config.res

import androidx.compose.ui.text.font.FontFamily
import cyxbsmobile.cyxbs_components.config.generated.resources.Res
import cyxbsmobile.cyxbs_components.config.generated.resources.config_ic_circle_add
import cyxbsmobile.cyxbs_components.config.generated.resources.config_ic_calendar_sync
import cyxbsmobile.cyxbs_components.config.generated.resources.config_ic_change_date
import cyxbsmobile.cyxbs_components.config.generated.resources.config_ic_compose_app_logo
import cyxbsmobile.cyxbs_components.config.generated.resources.config_ic_compose_back
import cyxbsmobile.cyxbs_components.config.generated.resources.config_ic_compose_place_holder
import cyxbsmobile.cyxbs_components.config.generated.resources.config_ic_default_avatar
import cyxbsmobile.cyxbs_components.config.generated.resources.config_ic_delete
import cyxbsmobile.cyxbs_components.config.generated.resources.config_ic_pin
import cyxbsmobile.cyxbs_components.config.generated.resources.config_ic_restore
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

    /** Figma 待办详情「同步到课表」按钮使用的通用日历图标。 */
    fun configIcCalendarSync(): DrawableResource = Res.drawable.config_ic_calendar_sync

    /** Figma 待办详情日期卡片右上角的通用“更改日期”图标。 */
    fun configIcChangeDate(): DrawableResource = Res.drawable.config_ic_change_date

    /** 通用置顶图标，由 Figma 原始路径转换为全平台可用的 Vector Drawable。 */
    fun configIcPin(): DrawableResource = Res.drawable.config_ic_pin

    /** 通用删除图标，由 Figma 原始路径转换为全平台可用的 Vector Drawable。 */
    fun configIcDelete(): DrawableResource = Res.drawable.config_ic_delete

    /** 通用恢复图标，由 Figma 原始路径转换为全平台可用的 Vector Drawable。 */
    fun configIcRestore(): DrawableResource = Res.drawable.config_ic_restore

    /**
     * Impact 字体（用于电费、课时分数等数字强调样式）。
     *
     * - Android 端返回基于 `res/font/impact_min.ttf` 的 [FontFamily]
     * - 其他平台暂未提供对应字体文件，返回 `null`；调用方根据是否为 null
     *   决定要不要给 Text 设置 `fontFamily`，否则会回退到平台默认字体
     */
    fun impactFontFamily(): FontFamily? = platformImpactFontFamily()
}
