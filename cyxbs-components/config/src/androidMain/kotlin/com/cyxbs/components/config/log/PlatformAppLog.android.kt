package com.cyxbs.components.config.log

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.cyxbs.components.init.appContext
import com.cyxbs.components.init.appLifecycle
import com.cyxbs.components.log.FileLogConfig
import java.io.File

internal actual fun platformFileLogConfig(): FileLogConfig? = FileLogConfig(
  directory = File(appContext.noBackupFilesDir, "cyxbs-log").absolutePath,
  exportDirectory = File(appContext.cacheDir, "cyxbs-log-export").absolutePath,
)

internal actual fun onPlatformFileLogStarted(config: FileLogConfig) = Unit

internal actual fun installPlatformLogLifecycle(onBackground: () -> Unit) {
  appLifecycle.addObserver(
    object : DefaultLifecycleObserver {
      override fun onStop(owner: LifecycleOwner) {
        onBackground()
      }
    }
  )
}
