package com.cyxbs.components.config.compose.theme

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 是否是黑夜模式
 *
 * @author 985892345
 * @date 2025/1/4
 */
val LocalAppDark: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }