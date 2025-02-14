package com.cyxbs.components.utils.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.cyxbs.components.config.compose.theme.LocalAppDark

/**
 * .
 *
 * @author 985892345
 * @date 2024/1/23 15:12
 */

fun Int.color(): Color {
  return Color(this)
}

fun Long.color(): Color {
  return Color(this)
}

@Composable
fun Int.dark(darkColor: Int): Color {
  return if (!LocalAppDark.current) Color(this) else Color(darkColor)
}

@Composable
fun Int.dark(darkColor: Color): Color {
  return if (!LocalAppDark.current) Color(this) else darkColor
}

@Composable
fun Long.dark(darkColor: Long): Color {
  return if (!LocalAppDark.current) Color(this) else Color(darkColor)
}

@Composable
fun Long.dark(darkColor: Color): Color {
  return if (!LocalAppDark.current) Color(this) else darkColor
}

@Composable
fun Color.dark(darkColor: Int): Color {
  return if (!LocalAppDark.current) this else Color(darkColor)
}