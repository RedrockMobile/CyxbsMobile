package com.cyxbs.components.config.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

/** Desktop 使用当前操作系统的默认字体栈。 */
@Composable
internal actual fun getFontFamily(): FontFamily = FontFamily.Default
