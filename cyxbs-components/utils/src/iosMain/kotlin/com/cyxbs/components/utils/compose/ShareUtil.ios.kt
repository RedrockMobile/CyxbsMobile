package com.cyxbs.components.utils.compose

import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.extensions.toast
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.CoreGraphics.CGRectGetMidX
import platform.CoreGraphics.CGRectGetMidY
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UINavigationController
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UITabBarController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController

@OptIn(ExperimentalForeignApi::class)
actual fun shareText(text: String) {
  appCoroutineScope.launch(Dispatchers.Main.immediate) {
    val window = UIApplication.sharedApplication.connectedScenes
      .filterIsInstance<UIWindowScene>()
      .filter { it.activationState == UISceneActivationStateForegroundActive }
      .flatMap { it.windows.filterIsInstance<UIWindow>() }
      .firstOrNull { it.isKeyWindow() }
      // 混合壳仍由 AppDelegate 管理窗口，没有 UIWindowScene。
      ?: UIApplication.sharedApplication.keyWindow
    val presenter = window?.rootViewController?.sharePresenter()
    if (presenter == null) {
      "暂时无法打开分享面板".toast()
      return@launch
    }

    // 传入文本即可由系统提供分享扩展及“拷贝”操作。
    val activity = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
    activity.popoverPresentationController?.apply {
      sourceView = presenter.view
      sourceRect = CGRectMake(
        CGRectGetMidX(presenter.view.bounds),
        CGRectGetMidY(presenter.view.bounds),
        0.0,
        0.0,
      )
      permittedArrowDirections = 0uL
    }
    presenter.presentViewController(activity, animated = true, completion = null)
  }
}

private fun UIViewController.sharePresenter(): UIViewController {
  var current = this
  while (true) {
    val next = current.presentedViewController ?: when (val container = current) {
      is UINavigationController -> container.visibleViewController
      is UITabBarController -> container.selectedViewController
      else -> null
    }
    current = next ?: return current
  }
}
