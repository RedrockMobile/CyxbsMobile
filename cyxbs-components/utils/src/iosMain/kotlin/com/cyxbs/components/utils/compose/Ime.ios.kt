package com.cyxbs.components.utils.compose

import com.cyxbs.components.init.InitialService
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIKeyboardWillHideNotification
import platform.UIKit.UIKeyboardWillShowNotification

/**
 * 同步读取 UIKit 键盘通知维护的进程内状态。
 */
actual fun isImeVisible(): Boolean = IosImeInitialService.isVisible

/**
 * iOS 应用启动时注册全局键盘通知并保存当前可见性，避免首次查询时已经错过键盘显示事件。
 */
@ImplProvider(clazz = InitialService::class, name = "IosImeInitialService")
@OptIn(ExperimentalForeignApi::class)
object IosImeInitialService : InitialService {

  var isVisible: Boolean = false
    private set

  /** 主进程启动后立即注册观察者，不依赖任何 Compose 组件生命周期。 */
  override fun onMainProcess() {
    listOf(
      UIKeyboardWillShowNotification to true,
      UIKeyboardWillHideNotification to false,
    ).forEach { (notificationName, visible) ->
      NSNotificationCenter.defaultCenter.addObserverForName(
        name = notificationName,
        `object` = null,
        queue = null,
      ) { isVisible = visible }
    }
  }
}
