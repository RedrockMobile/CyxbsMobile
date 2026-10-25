package com.cyxbs.components.config.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import cyxbsmobile.cyxbs_components.config.generated.resources.Res
import cyxbsmobile.cyxbs_components.config.generated.resources.SourceHanSansCN_Bold
import cyxbsmobile.cyxbs_components.config.generated.resources.SourceHanSansCN_ExtraLight
import cyxbsmobile.cyxbs_components.config.generated.resources.SourceHanSansCN_Heavy
import cyxbsmobile.cyxbs_components.config.generated.resources.SourceHanSansCN_Light
import cyxbsmobile.cyxbs_components.config.generated.resources.SourceHanSansCN_Medium
import cyxbsmobile.cyxbs_components.config.generated.resources.SourceHanSansCN_Normal
import cyxbsmobile.cyxbs_components.config.generated.resources.SourceHanSansCN_Regular
import org.jetbrains.compose.resources.Font

/**
 * Web 使用随应用打包的思源黑体，避免不同浏览器和操作系统选择不同的系统 fallback 字体。
 *
 * 字体来源：https://github.com/adobe-fonts/source-han-sans/tree/release
 * 压缩方式：https://moyuscript.github.io/MoyuScript/2022/10/26/font-compress/
 */
@Composable
internal actual fun getFontFamily(): FontFamily = FontFamily(
  Font(Res.font.SourceHanSansCN_ExtraLight, FontWeight.ExtraLight),
  Font(Res.font.SourceHanSansCN_Light, FontWeight.Light),
  Font(Res.font.SourceHanSansCN_Normal, FontWeight.Normal),
  Font(Res.font.SourceHanSansCN_Regular, FontWeight.Medium),
  Font(Res.font.SourceHanSansCN_Medium, FontWeight.SemiBold),
  Font(Res.font.SourceHanSansCN_Bold, FontWeight.Bold),
  Font(Res.font.SourceHanSansCN_Heavy, FontWeight.ExtraBold),
)
