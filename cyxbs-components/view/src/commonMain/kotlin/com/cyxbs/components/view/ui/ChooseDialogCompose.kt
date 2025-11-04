package com.cyxbs.components.view.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cyxbs.components.config.compose.theme.LocalAppColors
import kotlin.math.max

/**
 * 通用带选择按钮的 Dialog
 *
 * @author 985892345
 * @date 2023/12/21 21:06
 */
@Composable
fun ChooseDialogCompose(
  showState: MutableState<Boolean>,
  modifier: Modifier = Modifier.width(300.dp).wrapContentHeight(),
  properties: DialogProperties = DialogProperties(),
  positiveBtnText: String = "确定",
  negativeBtnText: String? = null, // 如果不需要第二个按钮，则传 null
  btnSize: DpSize = if (max(
      positiveBtnText.length,
      negativeBtnText?.length ?: 0
    ) > 2
  ) DpSize(110.dp, 36.dp) else DpSize(80.dp, 34.dp),
  onDismissRequest: () -> Unit = { showState.value = false },
  onClickPositiveBtn: () -> Unit = { },
  onClickNegativeBtn: () -> Unit = { },
  content: @Composable ColumnScope.() -> Unit,
) {
  if (showState.value) {
    Dialog(
      properties = properties,
      onDismissRequest = onDismissRequest,
    ) {
      ChooseDialogComposeContent(
        modifier = modifier,
        positiveBtnText = positiveBtnText,
        negativeBtnText = negativeBtnText,
        btnSize = btnSize,
        onClickPositiveBtn = onClickPositiveBtn,
        onClickNegativeBtn = onClickNegativeBtn,
        content,
      )
    }
  }
}

@Composable
fun ChooseDialogComposeContent(
  modifier: Modifier = Modifier.width(300.dp).wrapContentHeight(),
  positiveBtnText: String = "确定",
  negativeBtnText: String? = null, // 如果不需要第二个按钮，则传 null
  btnSize: DpSize = if (max(
      positiveBtnText.length,
      negativeBtnText?.length ?: 0
    ) > 2
  ) DpSize(110.dp, 36.dp) else DpSize(80.dp, 34.dp),
  onClickPositiveBtn: () -> Unit = { },
  onClickNegativeBtn: () -> Unit = { },
  content: @Composable ColumnScope.() -> Unit,
) {
  Box(
    modifier = modifier.clip(RoundedCornerShape(16.dp))
      .background(LocalAppColors.current.topBg),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
    ) {
      content()
      if (negativeBtnText != null) {
        DialogTwoBtnCompose(
          positiveBtnText = positiveBtnText,
          negativeBtnText = negativeBtnText,
          btnSize = btnSize,
          onClickPositiveBtn = onClickPositiveBtn,
          onClickNegativeBtn = onClickNegativeBtn
        )
      } else {
        DialogOneBtnCompose(
          positiveBtnText = positiveBtnText,
          btnSize = btnSize,
          onClickPositiveBtn = onClickPositiveBtn
        )
      }
    }
  }
}

@Composable
fun DialogTwoBtnCompose(
  modifier: Modifier = Modifier.padding(bottom = 30.dp).fillMaxWidth(),
  positiveBtnText: String = "确定",
  negativeBtnText: String = "取消",
  btnSize: DpSize = if (positiveBtnText.length > 2) DpSize(110.dp, 36.dp) else DpSize(80.dp, 34.dp),
  onClickPositiveBtn: () -> Unit = { },
  onClickNegativeBtn: () -> Unit = { },
) {
  Row(modifier = modifier, horizontalArrangement = Arrangement.SpaceEvenly) {
    DialogNegativeBtnCompose(
      negativeBtnText = negativeBtnText,
      modifier = Modifier.size(btnSize).clickable(onClick = onClickNegativeBtn)
    )
    DialogPositiveBtnCompose(
      positiveBtnText = positiveBtnText,
      modifier = Modifier.size(btnSize).clickable(onClick = onClickPositiveBtn)
    )
  }
}

@Composable
fun DialogOneBtnCompose(
  modifier: Modifier = Modifier.padding(bottom = 30.dp).fillMaxWidth(),
  positiveBtnText: String = "确定",
  btnSize: DpSize = if (positiveBtnText.length > 2) DpSize(110.dp, 36.dp) else DpSize(80.dp, 34.dp),
  onClickPositiveBtn: () -> Unit = { },
) {
  Box(
    modifier = modifier,
    contentAlignment = Alignment.BottomCenter
  ) {
    DialogPositiveBtnCompose(
      positiveBtnText = positiveBtnText,
      modifier = Modifier.size(btnSize).clickable(onClick = onClickPositiveBtn)
    )
  }
}

@Composable
fun DialogPositiveBtnCompose(
  modifier: Modifier = Modifier,
  positiveBtnText: String = "确定",
  textColor: Color = Color.White,
  backgroundColor: Color = LocalAppColors.current.positive,
) {
  Box(
    modifier = modifier.clip(MaterialTheme.shapes.large).background(backgroundColor),
    contentAlignment = Alignment.Center
  ) {
    Text(text = positiveBtnText, color = textColor)
  }
}

@Composable
fun DialogNegativeBtnCompose(
  modifier: Modifier = Modifier,
  negativeBtnText: String = "取消",
  textColor: Color = Color.White,
  backgroundColor: Color = LocalAppColors.current.negative,
) {
  Box(
    modifier = modifier.clip(MaterialTheme.shapes.large).background(backgroundColor),
    contentAlignment = Alignment.Center
  ) {
    Text(text = negativeBtnText, color = textColor)
  }
}