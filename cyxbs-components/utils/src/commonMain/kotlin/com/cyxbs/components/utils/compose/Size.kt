package com.cyxbs.components.utils.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * .
 *
 * @author 985892345
 * @date 2024/12/28
 */

val Int.px2dpCompose: Dp
  @Composable
  get() = LocalDensity.current.run { toDp() }

val Float.px2dpCompose: Dp
  @Composable
  get() = LocalDensity.current.run { toDp() }

val Dp.px: Float
  @Composable
  get() = LocalDensity.current.run { toPx() }