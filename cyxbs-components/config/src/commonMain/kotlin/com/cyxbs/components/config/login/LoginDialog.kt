package com.cyxbs.components.config.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.login.api.LoginNavArgument

/**
 * 需要判断是否登录展示的dialog
 */
@Composable
fun LoginDialog(
  state: LoginDialogState
) {
  Dialog(
    properties = DialogProperties(
      dismissOnBackPress = true,
      dismissOnClickOutside = false,
    ),
    onDismissRequest = {
      val showParams = state.showParamsState.value
      showParams?.onDismissRequest?.invoke()
      state.showParamsState.value = null
    },
  ) {
    Box(
      modifier = Modifier.width(300.dp).height(150.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(LocalAppColors.current.topBg),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        val msg = state.showParamsState.value?.msg ?: "此功能"
        Box(
          modifier = Modifier.fillMaxWidth().weight(1F),
          contentAlignment = Alignment.Center
        ) {
          Text(text = "请先登录才能使用${msg}哦~", fontSize = 14.sp, color = LocalAppColors.current.tvLv4)
        }
        Box(
          modifier = Modifier.padding(bottom = 30.dp)
            .width(80.dp)
            .height(34.dp)
            .clip(MaterialTheme.shapes.large)
            .background(LocalAppColors.current.positive)
            .clickable {
              LoginNavArgument.navigate(target = null, clearStack = false) // 打开登录页
            },
          contentAlignment = Alignment.Center
        ) {
          Text(text = "去登录", color = Color.White)
        }
      }
    }
  }
}

@Stable
class LoginDialogState {

  val showParamsState = mutableStateOf<Params?>(null)

  fun doIfLogin(
    msg: String = "此功能",
    onDismissRequest: (() -> Unit)? = null,
    next: (() -> Unit)? = null
  ): Boolean {
    return if (IAccountService::class.impl().isLogin()) {
      next?.invoke()
      true
    } else {
      showParamsState.value = Params(msg, onDismissRequest)
      false
    }
  }

  class Params(
    val msg: String,
    val onDismissRequest: (() -> Unit)?
  )
}

@Composable
fun rememberLoginDialogState(): LoginDialogState {
  val state = remember { LoginDialogState() }
  if (state.showParamsState.value != null) {
    LoginDialog(state = state)
  }
  return state
}