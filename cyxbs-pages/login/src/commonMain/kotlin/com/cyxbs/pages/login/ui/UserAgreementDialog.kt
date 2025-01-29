package com.cyxbs.pages.login.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.appName
import com.cyxbs.components.view.ui.ChooseDialogCompose
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.pages.login.viewmodel.LoginViewModel

/**
 * 用户协议与隐私政策 dialog
 *
 * @author 985892345
 * @date 2025/1/5
 */

@Composable
fun UserAgreementDialog() {
  val viewModel = viewModel(LoginViewModel::class)
  val showState = remember { mutableStateOf(true) }
  ChooseDialogCompose(
    showState = showState,
    positiveBtnText = "同意并继续",
    negativeBtnText = "不同意",
    btnSize = DpSize(119.dp, 38.dp),
    onClickPositiveBtn = {
      showState.value = false
      viewModel.isCheckUserArgument.value = true
    },
    onClickNegativeBtn = {
      viewModel.clickDisagreeUserAgreement()
    },
    onDismissRequest = {
      showState.value = false
    }
  ) {
    Column(
      modifier = Modifier.padding(top = 28.dp, bottom = 20.dp, start = 22.dp, end = 22.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(text = "温馨提示", color = LocalAppColors.current.tvLv4, fontSize = 18.sp)
      Text(
        text = buildAnnotatedString {
          append("友友，欢迎使用${appName}！在您使用${appName}前，请认真阅读")
          append(buildAnnotatedString {
            append("《用户协议》")
            addLink(LinkAnnotation.Clickable(
              tag = "用户协议",
              styles = TextLinkStyles(
                style = SpanStyle(color = Color(0xFF2CDEFF))
              )
            ) {
              viewModel.clickUserAgreement()
            }, 0, length)
          })
          append("和")
          append(buildAnnotatedString {
            append("《隐私政策》")
            addLink(LinkAnnotation.Clickable(
              tag = "隐私政策",
              styles = TextLinkStyles(
                style = SpanStyle(color = Color(0xFF2CDEFF))
              )
            ) {
              viewModel.clickPrivacyPolicy()
            }, 0, length)
          })
          append("，它们将帮助您了解我们所采集的个人信息与用途的对应关系。如您同意，请点击下方同意并继续按钮开始接受我们的服务。")
        },
        color = LocalAppColors.current.tvLv4,
        fontSize = 14.sp,
        modifier = Modifier.padding(top = 10.dp)
      )
    }
  }
}