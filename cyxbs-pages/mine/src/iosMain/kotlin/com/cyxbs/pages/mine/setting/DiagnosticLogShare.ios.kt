package com.cyxbs.pages.mine.setting

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectGetMidX
import platform.CoreGraphics.CGRectGetMidY
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
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
@Composable
internal actual fun rememberDiagnosticLogFileShareLauncher(
  onFailure: (message: String) -> Unit,
): DiagnosticLogFileShareLauncher = remember(onFailure) {
  DiagnosticLogFileShareLauncher(isSupported = true) { filePath ->
    val window = UIApplication.sharedApplication.connectedScenes
      .filterIsInstance<UIWindowScene>()
      .filter { it.activationState == UISceneActivationStateForegroundActive }
      .flatMap { it.windows.filterIsInstance<UIWindow>() }
      .firstOrNull { it.isKeyWindow() }
      ?: UIApplication.sharedApplication.keyWindow
    val presenter = window?.rootViewController?.diagnosticLogSharePresenter()
    if (presenter == null) {
      onFailure("暂时无法打开系统分享面板")
      return@DiagnosticLogFileShareLauncher
    }
    val fileUrl = NSURL.fileURLWithPath(filePath)
    val activity = UIActivityViewController(
      activityItems = listOf(fileUrl),
      applicationActivities = null,
    )
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

private fun UIViewController.diagnosticLogSharePresenter(): UIViewController {
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
