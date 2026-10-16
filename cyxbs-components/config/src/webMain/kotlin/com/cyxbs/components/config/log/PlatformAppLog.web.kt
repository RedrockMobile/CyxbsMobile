package com.cyxbs.components.config.log

import com.cyxbs.components.log.FileLogConfig

internal actual fun platformFileLogConfig(): FileLogConfig? = null

internal actual fun onPlatformFileLogStarted(config: FileLogConfig) = Unit

internal actual fun installPlatformLogLifecycle(onBackground: () -> Unit) = Unit
