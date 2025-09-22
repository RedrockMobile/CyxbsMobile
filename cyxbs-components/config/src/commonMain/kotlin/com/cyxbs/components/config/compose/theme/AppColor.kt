package com.cyxbs.components.config.compose.theme

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 应用级别最常用的颜色
 *
 * @author 985892345
 * @date 2024/1/28 11:09
 */

val LocalAppColors: ProvidableCompositionLocal<AppColor> = staticCompositionLocalOf {
  error("未配置 AppTheme")
}

open class AppColor(
  val tvDefault: Color = Color(0x8A000000),   // TextView 的默认颜色
  val tvLv1: Color = Color(0xFF112C54),       // 1号字体颜色，常用于页面顶部标题栏
  val tvLv2: Color = Color(0xFF112C57),       // 2号字体颜色，常用于页面普通字体
  val tvLv3: Color = Color(0xFF15315B),
  val tvLv4: Color = Color(0xFF2A4E84),
  val bottomBg: Color = Color(0xFFF2F3F8),    // 底部背景色，常用于页面最底部的颜色
  val middleBg: Color = Color(0xFFF9FAFD),    // 中间背景色，常用于页面浮层
  val topBg: Color = Color.White,                   // 顶部背景色，常用于 dialog
  val whiteBlack: Color = Color.White,
  val positive: Color = Color(0xFF4A44E4),    // 确定按钮颜色
  val negative: Color = Color(0xFFC3D4EE),    // 取消按钮颜色
) {
  companion object : AppColor()
}

object AppDarkColor : AppColor(
  tvDefault = Color(0xB3FFFFFF),
  tvLv1 = Color.White,
  tvLv2 = Color.White,
  tvLv3 = Color.White,
  tvLv4 = Color.White,
  bottomBg = Color.Black,
  middleBg = Color(0xFF1D1D1D),
  topBg = Color(0xFF2D2D2D),
  whiteBlack = Color.Black,
  positive = Color(0xFF4A44E4),
  negative = Color(0xCC5A5A5A),
)