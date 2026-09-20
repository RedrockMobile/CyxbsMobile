package com.cyxbs.components.utils.compose

import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.cyxbs.components.init.appTopActivity

/**
 * 从当前前台 Activity 的根窗口即时查询 IME 可见性，不注册 Insets 监听。
 *
 * [appTopActivity] 未初始化或弱引用已失效时直接返回 false，不再从 Activity 栈中兜底查找。
 */
actual fun isImeVisible(): Boolean {
  val activity = runCatching { appTopActivity.get() }.getOrNull() ?: return false
  val rootView = activity.currentFocus?.rootView ?: activity.window.decorView
  return ViewCompat.getRootWindowInsets(rootView)
    ?.isVisible(WindowInsetsCompat.Type.ime()) == true
}
