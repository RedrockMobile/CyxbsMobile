package com.cyxbs.components.view.ui

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/**
 * 窗口组件，类似于 Dialog，但全屏展示
 *
 * - 安卓基于官方 Compose Dialog 实现，并移除平台默认蒙层
 *
 * @author 985892345
 * @date 2025/3/24
 */
@Composable
actual fun Window(
  dismissOnBackPress: (() -> Unit)?,
  content: @Composable () -> Unit,
) {
  Dialog(
    onDismissRequest = { dismissOnBackPress?.invoke() },
    properties = DialogProperties(
      // null 表示由窗口内容自己的 BackHandler 处理，不能让 Dialog 抢先消费返回事件。
      dismissOnBackPress = dismissOnBackPress != null,
      dismissOnClickOutside = false,
      usePlatformDefaultWidth = false,
      // IME 位移由 commonMain 统一处理，避免 Android adjustPan 与其他平台行为不一致。
      decorFitsSystemWindows = false,
    )
  ) {
    val view = LocalView.current
    DisposableEffect(Unit) {
      // view 是 AndroidComposeView，其父 View 是 DialogLayout
      // 官方通过 DialogWindowProvider 来暴露 window
      val window = (view.parent as DialogWindowProvider).window
      window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND) // 移除蒙层
      onDispose {  }
    }
    content.invoke()
  }
}
