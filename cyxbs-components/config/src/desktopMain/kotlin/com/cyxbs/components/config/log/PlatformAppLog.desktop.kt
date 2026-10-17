package com.cyxbs.components.config.log

import com.cyxbs.components.log.FileLogConfig
import java.io.File

internal actual fun platformFileLogConfig(): FileLogConfig? {
  val appDirectory = File(System.getProperty("user.home"), ".cyxbs")
  return FileLogConfig(
    directory = File(appDirectory, "log").absolutePath,
    exportDirectory = File(appDirectory, "log-export").absolutePath,
  )
}

internal actual suspend fun preparePlatformFileLog(config: FileLogConfig) = Unit

internal actual fun setPlatformDiagnosticCollectionEnabled(
  enabled: Boolean,
  expectedPolicyVersion: Long?,
) = Unit

internal actual suspend fun onPlatformFileLogStarted(config: FileLogConfig) = Unit

internal actual fun installPlatformLogLifecycle(onBackground: () -> Unit) = Unit
