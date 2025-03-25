package com.cyxbs.components.utils.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize

/**
 * .
 *
 * @author 985892345
 * @date 2025/1/1
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun getWindowScreenSize(): DpSize {
  return LocalWindowInfo.current.containerSize.let {
    DpSize(it.width.px2dpCompose, it.height.px2dpCompose)
  }
}