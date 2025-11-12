package com.cyxbs.pages.map.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.pointer.PointerEvent

/**
 * @Desc : 加载Map组件
 * @Author : zzx
 * @Date : 2025/11/11 21:18
 */

@Composable
expect fun MapImageLoad(
  inputStream: ByteArray?,
  pointerEventCallback: (PointerEvent) -> Unit
)