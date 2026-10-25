package com.cyxbs.components.config.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

/** Android 使用系统默认字体栈，并由系统负责为中文字符选择 fallback 字体。 */
@Composable
internal actual fun getFontFamily(): FontFamily = FontFamily.Default
