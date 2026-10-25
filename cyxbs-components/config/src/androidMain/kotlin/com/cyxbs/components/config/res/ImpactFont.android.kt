package com.cyxbs.components.config.res

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.cyxbs.components.config.R

@Composable
internal actual fun platformImpactFontFamily(): FontFamily? =
  FontFamily(Font(R.font.impact))

@Composable
internal actual fun platformImpactMinFontFamily(): FontFamily? =
  FontFamily(Font(R.font.impact_min))