package com.cyxbs.functions.code.editor.workbench.internal

import android.graphics.Color
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 让 Android 系统导航栏透明覆盖工作台，并在编辑器离开组合时恢复原有窗口样式。
 *
 * 三键导航设备可能由系统自动添加浅色对比度遮罩，因此这里同时关闭遮罩并切换为浅色导航图标。
 * 仅保存和恢复本方法修改的属性，不接管 Activity 的状态栏或系统栏显隐行为。
 */
@Composable
@Suppress("DEPRECATION")
internal actual fun CodeEditorSystemBarsEffect() {
  val activity = LocalActivity.current ?: return
  val view = LocalView.current
  DisposableEffect(activity, view) {
    val window = activity.window
    val controller = WindowCompat.getInsetsController(window, view)
    val previousNavigationBarColor = window.navigationBarColor
    val previousLightNavigationBars = controller.isAppearanceLightNavigationBars
    val previousContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      window.isNavigationBarContrastEnforced
    } else {
      null
    }

    window.navigationBarColor = Color.TRANSPARENT
    controller.isAppearanceLightNavigationBars = false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      window.isNavigationBarContrastEnforced = false
    }

    onDispose {
      window.navigationBarColor = previousNavigationBarColor
      controller.isAppearanceLightNavigationBars = previousLightNavigationBars
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && previousContrastEnforced != null) {
        window.isNavigationBarContrastEnforced = previousContrastEnforced
      }
    }
  }
}
