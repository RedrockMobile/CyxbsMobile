package com.cyxbs.components.config.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.account.api.ILoginDialogContent
import com.cyxbs.components.config.service.impl

/**
 * 需要判断是否登录展示的dialog
 */
@Stable
class LoginDialogState {

  val showParamsState = mutableStateOf<Params?>(null)

  fun doIfLogin(
    function: String = "此功能",
    onDismissRequest: (() -> Unit)? = null,
    next: (() -> Unit)? = null
  ): Boolean {
    return if (IAccountService::class.impl().isLogin()) {
      next?.invoke()
      true
    } else {
      showParamsState.value = Params(
        function = function,
        onDismissRequest = onDismissRequest,
      )
      false
    }
  }

  class Params(
    val function: String,
    val onDismissRequest: (() -> Unit)?,
  )
}

@Composable
fun rememberLoginDialogState(): LoginDialogState {
  val state = remember { LoginDialogState() }
  val params = state.showParamsState.value
  if (params != null) {
    Dialog(
      properties = DialogProperties(
        dismissOnBackPress = true,
        dismissOnClickOutside = false,
      ),
      onDismissRequest = {
        params.onDismissRequest?.invoke()
        state.showParamsState.value = null
      },
    ) {
      remember { ILoginDialogContent::class.impl() }.Content(params.function) {
        // 点击去登录的监听
        state.showParamsState.value = null
      }
    }
  }
  return state
}