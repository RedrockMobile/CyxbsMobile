package com.cyxbs.components.config.res

import androidx.compose.ui.text.font.FontFamily

/**
 * Impact 字体的平台获取入口，由 [ConfigRes.impactFontFamily] 转发。
 *
 * - Android 端通过原生 `R.font.impact_min` 提供 [FontFamily]
 * - 其它平台暂未提供对应字体文件，返回 `null`
 */
internal expect fun platformImpactFontFamily(): FontFamily?