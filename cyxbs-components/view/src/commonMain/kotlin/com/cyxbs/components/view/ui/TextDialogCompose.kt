package com.cyxbs.components.view.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import kotlin.math.max

/**
 * 简单文本描述的 dialog
 *
 * ```
 * // 使用示例
 *
 * val dialog = rememberTextDialog()
 *
 * Text(
 *   modifier = Modifier.clickable {
 *     dialog.show() // 点击时弹出 dialog
 *   }
 * )
 * ```
 *
 * @author 985892345
 * @date 2026/1/18
 */


@Composable
fun rememberTextDialog(
  text: CharSequence,
  modifier: Modifier = Modifier.width(300.dp).wrapContentHeight(),
  dismissOnBackPress: Boolean = true, // 按返回键时是否关闭
  dismissOnClickOutside: Boolean = true, // 点击外部时是否关闭
  positiveBtnText: String = "确定",
  negativeBtnText: String? = null, // 如果不需要第二个按钮，则传 null
  btnSize: DpSize = if (max(
      positiveBtnText.length,
      negativeBtnText?.length ?: 0
    ) > 2
  ) DpSize(110.dp, 36.dp) else DpSize(80.dp, 34.dp),
  scrimColor: Color = Color.Transparent.copy(alpha = 0.6F),
  onDismissRequest: TextDialogState.() -> Unit = { dismiss() },
  onClickPositiveBtn: TextDialogState.() -> Unit = { dismiss() },
  onClickNegativeBtn: TextDialogState.() -> Unit = { dismiss() },
): TextDialogState {
  val state = remember {
    TextDialogState(
      text = text,
      dismissOnBackPress = dismissOnBackPress,
      dismissOnClickOutside = dismissOnClickOutside,
      positiveBtnText = positiveBtnText,
      negativeBtnText = negativeBtnText,
      btnSize = btnSize,
      scrimColor = scrimColor,
      onDismissRequest = onDismissRequest,
      onClickPositiveBtn = onClickPositiveBtn,
      onClickNegativeBtn = onClickNegativeBtn,
    )
  }
  TextDialogCompose(modifier, state)
  return state
}

@Stable
class TextDialogState(
  val text: CharSequence,
  val dismissOnBackPress: Boolean, // 按返回键时是否关闭
  val dismissOnClickOutside: Boolean, // 点击外部时是否关闭
  val positiveBtnText: String,
  val negativeBtnText: String?, // 如果不需要第二个按钮，则传 null
  val btnSize: DpSize,
  val scrimColor: Color = Color.Transparent.copy(alpha = 0.6F),
  val onDismissRequest: TextDialogState.() -> Unit,
  val onClickPositiveBtn: TextDialogState.() -> Unit,
  val onClickNegativeBtn: TextDialogState.() -> Unit,
) {

  val showState = mutableStateOf(false)

  var textProxy: CharSequence? = null
  var onDismissRequestProxy: (TextDialogState.() -> Unit)? = null
  var onClickPositiveBtnProxy: (TextDialogState.() -> Unit)? = null
  var onClickNegativeBtnProxy: (TextDialogState.() -> Unit)? = null

  fun show() {
    showAndCover()
  }

  /**
   * 展示 dialog，部分参数可用于覆盖默认参数
   */
  fun showAndCover(
    textProxy: CharSequence? = null,
    onDismissRequestProxy: TextDialogState.() -> Unit = { this.onDismissRequest.invoke(this) },
    onClickPositiveBtnProxy: TextDialogState.() -> Unit = { this.onClickPositiveBtn.invoke(this) },
    onClickNegativeBtnProxy: TextDialogState.() -> Unit = { this.onClickNegativeBtn.invoke(this) },
  ) {
    this.textProxy = textProxy
    this.onDismissRequestProxy = onDismissRequestProxy
    this.onClickPositiveBtnProxy = onClickPositiveBtnProxy
    this.onClickNegativeBtnProxy = onClickNegativeBtnProxy
    showState.value = true
  }

  fun dismiss() {
    showState.value = false
    textProxy = null
    onDismissRequestProxy = null
    onClickPositiveBtnProxy = null
    onClickNegativeBtnProxy = null
  }
}

@Composable
private fun TextDialogCompose(
  modifier: Modifier,
  state: TextDialogState,
) {
  ChooseDialogCompose(
    showState = state.showState,
    modifier = modifier,
    dismissOnBackPress = state.dismissOnBackPress,
    dismissOnClickOutside = state.dismissOnClickOutside,
    positiveBtnText = state.positiveBtnText,
    negativeBtnText = state.negativeBtnText,
    btnSize = state.btnSize,
    scrimColor = state.scrimColor,
    onDismissRequest = { (state.onDismissRequestProxy ?: state.onDismissRequest).invoke(state) },
    onClickPositiveBtn = { (state.onClickPositiveBtnProxy ?: state.onClickPositiveBtn).invoke(state) },
    onClickNegativeBtn = { (state.onClickNegativeBtnProxy ?: state.onClickNegativeBtn).invoke(state) },
    content = {
      Box(
        modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 28.dp),
        contentAlignment = Alignment.Center,
      ) {
        val text = state.textProxy ?: state.text
        Text(
          text = text as? AnnotatedString ?: AnnotatedString(text.toString()),
          color = LocalAppColors.current.tvLv2,
          fontSize = 14.sp,
        )
      }
    }
  )
}