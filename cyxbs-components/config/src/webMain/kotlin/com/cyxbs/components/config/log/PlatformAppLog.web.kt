package com.cyxbs.components.config.log

import com.cyxbs.components.log.FileLogConfig

internal actual fun platformFileLogConfig(): FileLogConfig? = null

internal actual suspend fun preparePlatformFileLog(config: FileLogConfig) = Unit

internal actual fun setPlatformDiagnosticCollectionEnabled(
  enabled: Boolean,
  expectedPolicyVersion: Long?,
) = Unit

internal actual suspend fun onPlatformFileLogStarted(config: FileLogConfig) = Unit

internal actual fun installPlatformLogLifecycle(onBackground: () -> Unit) = Unit
