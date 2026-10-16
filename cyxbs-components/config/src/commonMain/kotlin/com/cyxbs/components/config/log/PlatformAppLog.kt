package com.cyxbs.components.config.log

import com.cyxbs.components.log.FileLogConfig

/** Returns null on platforms without persistent file logging. */
internal expect fun platformFileLogConfig(): FileLogConfig?

/** Applies platform metadata after the engine has created its directory. */
internal expect fun onPlatformFileLogStarted(config: FileLogConfig)

/** Installs platform lifecycle callbacks once. */
internal expect fun installPlatformLogLifecycle(onBackground: () -> Unit)
