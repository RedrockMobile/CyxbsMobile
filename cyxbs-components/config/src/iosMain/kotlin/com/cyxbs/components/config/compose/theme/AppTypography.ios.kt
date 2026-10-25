package com.cyxbs.components.config.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

/** iOS 使用系统默认字体栈，并保留系统自身的中英文 fallback 规则。 */
@Composable
internal actual fun getFontFamily(): FontFamily = FontFamily.Default
