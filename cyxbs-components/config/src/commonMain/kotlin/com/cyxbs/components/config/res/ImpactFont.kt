package com.cyxbs.components.config.res

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

/**
 * Impact 字体的平台获取入口，由 [ConfigRes.impactFontFamily] 转发。
 *
 * - Android / iOS 端通过 [FontFamily] 提供 Impact 字体
 * - desktop / web 端暂未提供对应字体文件，返回 `null`
 */
@Composable
internal expect fun platformImpactFontFamily(): FontFamily?

@Composable
internal expect fun platformImpactMinFontFamily(): FontFamily?