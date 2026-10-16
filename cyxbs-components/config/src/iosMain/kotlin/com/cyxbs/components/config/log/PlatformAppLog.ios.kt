package com.cyxbs.components.config.log

import com.cyxbs.components.log.FileLogConfig
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIApplicationDidEnterBackgroundNotification

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformFileLogConfig(): FileLogConfig? {
  val support = NSSearchPathForDirectoriesInDomains(
    NSApplicationSupportDirectory,
    NSUserDomainMask,
    true,
  ).firstOrNull() as? String ?: return null
  val caches = NSSearchPathForDirectoriesInDomains(
    NSCachesDirectory,
    NSUserDomainMask,
    true,
  ).firstOrNull() as? String ?: return null
  return FileLogConfig(
    directory = "$support/cyxbs-log",
    exportDirectory = "$caches/cyxbs-log-export",
  )
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun onPlatformFileLogStarted(config: FileLogConfig) {
  NSURL.fileURLWithPath(config.directory).setResourceValue(
    value = true,
    forKey = NSURLIsExcludedFromBackupKey,
    error = null,
  )
}

internal actual fun installPlatformLogLifecycle(onBackground: () -> Unit) {
  NSNotificationCenter.defaultCenter.addObserverForName(
    name = UIApplicationDidEnterBackgroundNotification,
    `object` = null,
    queue = null,
  ) { onBackground() }
}
