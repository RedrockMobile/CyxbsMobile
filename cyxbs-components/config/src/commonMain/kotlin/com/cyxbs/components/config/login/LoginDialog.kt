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

  fun isLogin(): Boolean {
    return IAccountService::class.impl().isLogin()
  }

  fun showDialog(function: String = "此功能", onDismissRequest: (() -> Unit)? = null) {
    showParamsState.value = Params(
      function = function,
      onDismissRequest = onDismissRequest,
    )
  }

  inline fun doIfLogin(
    function: String = "此功能",
    noinline onDismissRequest: (() -> Unit)? = null,
    next: () -> Unit = {}
  ): LoginDialogState {
    if (isLogin()) {
      next.invoke()
    } else {
      showDialog(function, onDismissRequest)
    }
    return this
  }

  /**
   * 链式调用:
   * ```
   * val loginDialog = rememberLoginDialogState()
   * loginDaiglog.doIfLoginNotShowDialog {
   *     Text("...")
   * }.doIfNotLogin {
   *     Button("...", onClick = { showDialog() })
   * }
   * ```
   */
  inline fun doIfLoginNotShowDialog(action: LoginDialogState.() -> Unit = {}): LoginDialogState {
    if (isLogin()) {
      action.invoke(this)
    }
    return this
  }

  inline fun doIfNotLogin(action: LoginDialogState.() -> Unit) {
    if (!isLogin()) {
      action.invoke(this)
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