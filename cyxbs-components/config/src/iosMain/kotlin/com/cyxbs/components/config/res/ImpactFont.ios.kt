package com.cyxbs.components.config.res

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import cyxbsmobile.cyxbs_components.config.generated.resources.Res
import cyxbsmobile.cyxbs_components.config.generated.resources.impact
import cyxbsmobile.cyxbs_components.config.generated.resources.impact_min
import org.jetbrains.compose.resources.Font

@Composable
internal actual fun platformImpactFontFamily(): FontFamily? =
  FontFamily(Font(Res.font.impact))

@Composable
internal actual fun platformImpactMinFontFamily(): FontFamily? =
  FontFamily(Font(Res.font.impact_min))