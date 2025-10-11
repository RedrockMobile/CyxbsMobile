package com.cyxbs.components.config.compose.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.text.font.FontFamily

@Composable
internal actual fun ConfigAppThemeBefore(
  content: @Composable () -> Unit
) {
  content()
}

@Composable
internal actual fun ConfigAppThemeAfter(
  content: @Composable () -> Unit
) {
  CompositionLocalProvider(
    LocalIndication provides NoIndicationNodeFactory, // 安卓上不显示默认的点击效果
  ) {
    content()
  }
}

@Composable
internal actual fun getFontFamily(): FontFamily {
  return FontFamily.Default
}

private data object NoIndicationNodeFactory : IndicationNodeFactory {

  override fun create(interactionSource: InteractionSource): DelegatableNode {
    return object : Modifier.Node() {}
  }
}
