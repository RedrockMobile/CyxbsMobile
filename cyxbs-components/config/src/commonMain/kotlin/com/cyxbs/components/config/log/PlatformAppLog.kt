package com.cyxbs.components.config.log

import com.cyxbs.components.log.FileLogConfig

/** Returns null on platforms without persistent file logging. */
internal expect fun platformFileLogConfig(): FileLogConfig?

/** Prepares platform storage only after application policy has allowed startup. */
internal expect suspend fun preparePlatformFileLog(config: FileLogConfig)

/** Applies and verifies platform metadata after the engine has created its directory. */
internal expect suspend fun onPlatformFileLogStarted(config: FileLogConfig)

/** Enables or disables optional platform diagnostics under the same user collection policy. */
internal expect fun setPlatformDiagnosticCollectionEnabled(
  enabled: Boolean,
  expectedPolicyVersion: Long? = null,
)

/** Installs platform lifecycle callbacks once. */
internal expect fun installPlatformLogLifecycle(onBackground: () -> Unit)
