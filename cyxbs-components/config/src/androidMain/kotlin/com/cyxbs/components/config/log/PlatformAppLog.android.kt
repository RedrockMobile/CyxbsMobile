package com.cyxbs.components.config.log

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.cyxbs.components.init.appContext
import com.cyxbs.components.init.appLifecycle
import com.cyxbs.components.log.CyxbsLog
import com.cyxbs.components.log.FileLogConfig
import com.cyxbs.components.log.LogContext
import com.cyxbs.components.log.LogLevel
import java.io.File

private var lastReportedExitKey: String? = null

internal actual fun platformFileLogConfig(): FileLogConfig? = FileLogConfig(
  directory = File(appContext.noBackupFilesDir, "cyxbs-log").absolutePath,
  exportDirectory = File(appContext.cacheDir, "cyxbs-log-export").absolutePath,
)

internal actual suspend fun preparePlatformFileLog(config: FileLogConfig) = Unit

internal actual fun setPlatformDiagnosticCollectionEnabled(
  enabled: Boolean,
  expectedPolicyVersion: Long?,
) {
  if (enabled && expectedPolicyVersion != null) reportPreviousProcessExit(expectedPolicyVersion)
}

internal actual suspend fun onPlatformFileLogStarted(config: FileLogConfig) = Unit

private fun reportPreviousProcessExit(expectedPolicyVersion: Long) {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
  AppLogManager.withCurrentDiagnosticCollectionPolicy(expectedPolicyVersion) {
    val activityManager = appContext.getSystemService(ActivityManager::class.java)
      ?: return@withCurrentDiagnosticCollectionPolicy
    val exit = runCatching {
      activityManager.getHistoricalProcessExitReasons(appContext.packageName, 0, 1).firstOrNull()
    }.getOrNull() ?: return@withCurrentDiagnosticCollectionPolicy
    val exitKey = "${exit.reason}:${exit.timestamp}"
    if (lastReportedExitKey == exitKey) return@withCurrentDiagnosticCollectionPolicy

    CyxbsLog.log(
      level = LogLevel.Info,
      tag = "AppExit",
      context = LogContext(
        operation = "android.previous_process_exit",
        eventTimestampMillis = exit.timestamp,
      ),
      message = "Previous process exit: reason=${exit.reasonName()}, status=${exit.status}, importance=${exit.importance}",
    )
    lastReportedExitKey = exitKey
  }
}

private fun ApplicationExitInfo.reasonName(): String = when (reason) {
  ApplicationExitInfo.REASON_EXIT_SELF -> "exit_self"
  ApplicationExitInfo.REASON_SIGNALED -> "signaled"
  ApplicationExitInfo.REASON_LOW_MEMORY -> "low_memory"
  ApplicationExitInfo.REASON_CRASH -> "crash"
  ApplicationExitInfo.REASON_CRASH_NATIVE -> "native_crash"
  ApplicationExitInfo.REASON_ANR -> "anr"
  ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialization_failure"
  ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permission_change"
  ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive_resource_usage"
  ApplicationExitInfo.REASON_USER_REQUESTED -> "user_requested"
  ApplicationExitInfo.REASON_USER_STOPPED -> "user_stopped"
  ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency_died"
  ApplicationExitInfo.REASON_OTHER -> "other"
  else -> "unknown_$reason"
}

internal actual fun installPlatformLogLifecycle(onBackground: () -> Unit) {
  appLifecycle.addObserver(
    object : DefaultLifecycleObserver {
      override fun onStop(owner: LifecycleOwner) {
        onBackground()
      }
    }
  )
}
