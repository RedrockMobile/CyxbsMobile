package com.cyxbs.components.config.compose.theme

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * .
 *
 * @author 985892345
 * @date 2025/1/4
 */

@Composable
fun AppTheme(
  content: @Composable () -> Unit,
) {
  CompositionLocalProvider(
    LocalAppDark provides isSystemInDarkTheme(),
  ) {
    ConfigAppThemeBefore {
      MaterialTheme(
        colors = if (!LocalAppDark.current) LightColor else DarkColor,
        typography = createTypography(),
        shapes = Shapes,
      ) {
        DefaultIndication = LocalIndication.current
        CompositionLocalProvider(
          LocalAppColors provides if (!LocalAppDark.current) AppColor else AppDarkColor,
          LocalIndication provides CardIndicationNodeFactory,
        ) {
          ConfigAppThemeAfter(content)
        }
      }
    }
  }
}

// 默认的点击效果，带波纹
lateinit var DefaultIndication: Indication

// 目前 XML 界面中常见的颜色值
private val windowBackgroundColor = 0xFFF2F3F8 and 0xFF000000
private val discoverCardColor = 0xFFF8F9FC and 0xFF1D1D1D
private val mineCardColor = 0xFFFCFDFF and 0xFF1D1D1D
private val storeCardColor = 0xFFFBFCFF and 0xFF1D1D1D
private val topCardColor = 0xFFFFFFFF and 0xFF2D2D2D

internal val LightColor = lightColors(
  primary = Color(0xFF788EFA),            // 主要颜色是应用程序屏幕和组件中最常显示的颜色
  primaryVariant = Color(0xFFF2F3F8),     // 主要变体颜色用于使用主要颜色区分应用程序的两个元素，例如顶部应用程序栏和系统栏
  secondary = Color(0xFF788EFA),          // 辅助色提供了更多方式来强调和区分您的产品。次要颜色最适合：浮动操作按钮、选择控件，例如复选框和单选按钮、突出显示选定的文本、链接和标题
  secondaryVariant = Color(0xFF4A44E4),   // 次要变体颜色用于使用次要颜色区分应用程序的两个元素
  background = AppColor.bottomBg,               // 背景颜色出现在可滚动内容后面
  surface = AppColor.middleBg,                  // 表面颜色用于组件的表面，例如卡片、工作表和菜单
  error = Color(0xFFB00020),              // 错误颜色用于指示组件内的错误，例如文本字段
  onPrimary = AppColor.tvLv1,                   // 用于显示在主颜色之上的文本和图标的颜色
  onSecondary = AppColor.tvLv2,                 // 用于显示在辅助颜色之上的文本和图标的颜色
  onBackground = AppColor.tvLv2,                // 用于显示在背景颜色之上的文本和图标的颜色
  onSurface = AppColor.tvLv2,                   // 用于显示在表面颜色之上的文本和图标的颜色
  onError = Color.White,                        // 用于显示在错误颜色之上的文本和图标的颜色
)

internal val DarkColor = darkColors(
  primary = Color.White,                        // 主要颜色是应用程序屏幕和组件中最常显示的颜色
  primaryVariant = Color.White,                 // 主要变体颜色用于使用主要颜色区分应用程序的两个元素，例如顶部应用程序栏和系统栏
  secondary = Color(0xFF788EFA),          // 辅助色提供了更多方式来强调和区分您的产品。次要颜色最适合：浮动操作按钮、选择控件，例如复选框和单选按钮、突出显示选定的文本、链接和标题
  secondaryVariant = Color(0xFF4A44E4),   // 次要变体颜色用于使用次要颜色区分应用程序的两个元素
  background = AppDarkColor.bottomBg,           // 背景颜色出现在可滚动内容后面
  surface = AppDarkColor.middleBg,              // 表面颜色用于组件的表面，例如卡片、工作表和菜单
  error = Color(0xFFCF6679),              // 错误颜色用于指示组件内的错误，例如文本字段
  onPrimary = AppDarkColor.tvLv1,               // 用于显示在主颜色之上的文本和图标的颜色
  onSecondary = AppDarkColor.tvLv2,             // 用于显示在辅助颜色之上的文本和图标的颜色
  onBackground = AppDarkColor.tvLv2,            // 用于显示在背景颜色之上的文本和图标的颜色
  onSurface = AppDarkColor.tvLv2,               // 用于显示在表面颜色之上的文本和图标的颜色
  onError = Color.Black                         // 用于显示在错误颜色之上的文本和图标的颜色
)

@Composable
internal expect fun ConfigAppThemeBefore(content: @Composable () -> Unit)

@Composable
internal expect fun ConfigAppThemeAfter(content: @Composable () -> Unit)

@Composable
internal expect fun getFontFamily(): FontFamily

@Composable
private fun createTypography(): Typography {
  val defaultFontFamily = getFontFamily()
  return Typography(
    // 去掉字体的默认行高间距
    h1 = MaterialTheme.typography.h1.copy(
      lineHeight = TextUnit.Unspecified,
      fontFamily = defaultFontFamily
    ),
    h2 = MaterialTheme.typography.h2.copy(
      lineHeight = TextUnit.Unspecified,
      fontFamily = defaultFontFamily
    ),
    h3 = MaterialTheme.typography.h3.copy(
      lineHeight = TextUnit.Unspecified,
      fontFamily = defaultFontFamily
    ),
    h4 = MaterialTheme.typography.h4.copy(
      lineHeight = TextUnit.Unspecified,
      fontFamily = defaultFontFamily
    ),
    h5 = MaterialTheme.typography.h5.copy(
      lineHeight = TextUnit.Unspecified,
      fontFamily = defaultFontFamily
    ),
    h6 = MaterialTheme.typography.h6.copy(
      lineHeight = TextUnit.Unspecified,
      fontFamily = defaultFontFamily
    ),
    subtitle1 = MaterialTheme.typography.subtitle1.copy(
      lineHeight = TextUnit.Unspecified,
      fontFamily = defaultFontFamily
    ),
    subtitle2 = MaterialTheme.typography.subtitle2.copy(
      lineHeight = TextUnit.Unspecified,
      fontFamily = defaultFontFamily
    ),
    body1 = MaterialTheme.typography.body1.copy(
      lineHeight = TextUnit.Unspecified,
      fontFamily = defaultFontFamily
    ),
    body2 = MaterialTheme.typography.body2.copy(
      lineHeight = TextUnit.Unspecified,
      fontFamily = defaultFontFamily
    ),
    button = MaterialTheme.typography.button.copy(
      lineHeight = TextUnit.Unspecified,
      fontFamily = defaultFontFamily
    ),
    caption = MaterialTheme.typography.caption.copy(
      lineHeight = TextUnit.Unspecified,
      fontFamily = defaultFontFamily
    ),
    overline = MaterialTheme.typography.overline.copy(
      lineHeight = TextUnit.Unspecified,
      fontFamily = defaultFontFamily
    ),
  )
}

private val Shapes = Shapes(
  small = RoundedCornerShape(4.dp),
  medium = RoundedCornerShape(8.dp),
  large = RoundedCornerShape(16.dp),
)


private data object CardIndicationNodeFactory : IndicationNodeFactory {

  override fun create(interactionSource: InteractionSource): DelegatableNode =
    DefaultDebugIndicationInstance(interactionSource, radius = 4.dp)

  class DefaultDebugIndicationInstance(
    private val interactionSource: InteractionSource,
    private val radius: Dp,
  ) : Modifier.Node(), DrawModifierNode {
    private var isPressed = false
    private var isHovered = false
    private var isFocused = false
    override fun onAttach() {
      coroutineScope.launch {
        var pressCount = 0
        var hoverCount = 0
        var focusCount = 0
        interactionSource.interactions.collect { interaction ->
          when (interaction) {
            is PressInteraction.Press -> pressCount++
            is PressInteraction.Release -> pressCount--
            is PressInteraction.Cancel -> pressCount--
            is HoverInteraction.Enter -> hoverCount++
            is HoverInteraction.Exit -> hoverCount--
            is FocusInteraction.Focus -> focusCount++
            is FocusInteraction.Unfocus -> focusCount--
          }
          val pressed = pressCount > 0
          val hovered = hoverCount > 0
          val focused = focusCount > 0
          var invalidateNeeded = false
          if (isPressed != pressed) {
            isPressed = pressed
            invalidateNeeded = true
          }
          if (isHovered != hovered) {
            isHovered = hovered
            invalidateNeeded = true
          }
          if (isFocused != focused) {
            isFocused = focused
            invalidateNeeded = true
          }
          if (invalidateNeeded) invalidateDraw()
        }
      }
    }

    override fun ContentDrawScope.draw() {
      drawContent()
      if (isPressed) {
        drawRoundRect(
          color = Color.Black.copy(alpha = 0.2f),
          size = size,
          cornerRadius = CornerRadius(radius.toPx())
        )
      } else if (isHovered || isFocused) {
        drawRoundRect(
          color = Color.Black.copy(alpha = 0.1f),
          size = size,
          cornerRadius = CornerRadius(radius.toPx())
        )
      }
    }
  }
}