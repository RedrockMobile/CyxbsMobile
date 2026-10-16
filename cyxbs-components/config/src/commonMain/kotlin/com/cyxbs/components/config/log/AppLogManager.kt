package com.cyxbs.components.config.log

import com.cyxbs.components.config.isDebug
import com.cyxbs.components.config.sp.SP_DIAGNOSTIC_LOG_ENABLED
import com.cyxbs.components.config.sp.SP_PRIVACY_AGREED
import com.cyxbs.components.config.sp.defaultSettings
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.log.CyxbsLog
import com.cyxbs.components.log.FileLogConfig
import com.cyxbs.components.log.FileLogHealth
import com.cyxbs.components.log.FileLogState
import com.cyxbs.components.log.LogConfig
import com.cyxbs.components.log.LogExportResult
import com.cyxbs.components.log.LogFlushResult
import com.cyxbs.components.log.LogLevel
import com.cyxbs.components.log.LogStorageResult
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Applies application policy to the platform-agnostic log engine.
 *
 * Persistent logging is conservative by default: only the main process, after privacy consent,
 * and after an explicit user opt-in. Console logging remains available independently.
 */
object AppLogManager {
  private val initialized = atomic(false)
  private val isMainProcess = atomic(false)
  private val operations = Mutex()

  fun initialize(mainProcess: Boolean) {
    isMainProcess.value = mainProcess
    CyxbsLog.configure(
      LogConfig(
        enabled = true,
        minLevel = if (isDebug()) LogLevel.Debug else LogLevel.Warn,
        defaultTag = "Cyxbs",
      )
    )
    if (mainProcess && initialized.compareAndSet(expect = false, update = true)) {
      installPlatformLogLifecycle {
        appCoroutineScope.launch { CyxbsLog.flushFileStorage() }
      }
    }
    val shouldCollect = shouldRunFileStorage()
    CyxbsLog.setFileStorageCollectionEnabled(shouldCollect)
    if (shouldCollect) {
      appCoroutineScope.launch { ensureStarted() }
    }
  }

  fun isDiagnosticLoggingEnabled(): Boolean =
    defaultSettings.getBoolean(SP_DIAGNOSTIC_LOG_ENABLED, false)

  fun isDiagnosticLoggingSupported(): Boolean = platformFileLogConfig() != null

  fun fileStorageHealth(): FileLogHealth = CyxbsLog.fileStorageHealth()

  suspend fun setDiagnosticLoggingEnabled(enabled: Boolean): LogStorageResult = operations.withLock {
    if (enabled && !defaultSettings.getBoolean(SP_PRIVACY_AGREED, false)) {
      return@withLock LogStorageResult.Failure("请先同意隐私政策")
    }
    if (!enabled) {
      CyxbsLog.setFileStorageCollectionEnabled(false)
      defaultSettings.putBoolean(SP_DIAGNOSTIC_LOG_ENABLED, false)
      return@withLock CyxbsLog.closeFileStorage()
    }
    if (!isMainProcess.value) {
      return@withLock LogStorageResult.Failure("诊断日志仅支持主进程")
    }
    defaultSettings.putBoolean(SP_DIAGNOSTIC_LOG_ENABLED, true)
    CyxbsLog.setFileStorageCollectionEnabled(true)
    try {
      val result = startLocked()
      if (result != LogStorageResult.Success) {
        CyxbsLog.setFileStorageCollectionEnabled(false)
        defaultSettings.putBoolean(SP_DIAGNOSTIC_LOG_ENABLED, false)
      }
      result
    } catch (cancellation: CancellationException) {
      CyxbsLog.setFileStorageCollectionEnabled(false)
      withContext(NonCancellable) {
        defaultSettings.putBoolean(SP_DIAGNOSTIC_LOG_ENABLED, false)
        CyxbsLog.closeFileStorage()
      }
      throw cancellation
    }
  }

  suspend fun clearDiagnosticLogs(): LogStorageResult = operations.withLock {
    if (!isMainProcess.value) return@withLock LogStorageResult.Failure("诊断日志仅支持主进程")
    val wasRunning = CyxbsLog.fileStorageHealth().state == FileLogState.Running
    var startedForOperation = false
    try {
      if (!wasRunning) {
        val config = platformFileLogConfig() ?: return@withLock LogStorageResult.Unsupported
        when (val start = startFileStorage(config)) {
          LogStorageResult.Success -> startedForOperation = true
          LogStorageResult.Unsupported -> return@withLock LogStorageResult.Unsupported
          is LogStorageResult.Failure -> return@withLock start
        }
      }
      CyxbsLog.clearFileStorage()
    } finally {
      if (startedForOperation && !shouldRunFileStorage()) {
        withContext(NonCancellable) { CyxbsLog.closeFileStorage() }
      }
    }
  }

  suspend fun exportDiagnosticLogs(): LogExportResult = operations.withLock {
    if (!isDiagnosticLoggingEnabled()) {
      return@withLock LogExportResult.Failure("诊断日志尚未开启")
    }
    when (val start = startLocked()) {
      LogStorageResult.Success -> CyxbsLog.exportFileStorage()
      LogStorageResult.Unsupported -> LogExportResult.Unsupported
      is LogStorageResult.Failure -> LogExportResult.Failure(start.message)
    }
  }

  suspend fun flush(): LogFlushResult = CyxbsLog.flushFileStorage()

  fun onPrivacyAgreed() {
    if (shouldRunFileStorage()) {
      CyxbsLog.setFileStorageCollectionEnabled(true)
      appCoroutineScope.launch { ensureStarted() }
    }
  }

  fun onPrivacyWithdrawn() {
    CyxbsLog.setFileStorageCollectionEnabled(false)
    appCoroutineScope.launch {
      operations.withLock {
        if (!shouldRunFileStorage()) CyxbsLog.closeFileStorage()
      }
    }
  }

  private fun shouldRunFileStorage(): Boolean =
    isMainProcess.value &&
      defaultSettings.getBoolean(SP_PRIVACY_AGREED, false) &&
      isDiagnosticLoggingEnabled()

  private suspend fun ensureStarted(): LogStorageResult = operations.withLock {
    val result = startLocked()
    if (result != LogStorageResult.Success) {
      CyxbsLog.setFileStorageCollectionEnabled(false)
    }
    result
  }

  private suspend fun startLocked(): LogStorageResult {
    if (!shouldRunFileStorage()) return LogStorageResult.Failure("诊断日志当前不可用")
    val current = CyxbsLog.fileStorageHealth().state
    if (current == FileLogState.Running || current == FileLogState.Starting) {
      return LogStorageResult.Success
    }
    val config = platformFileLogConfig() ?: return LogStorageResult.Unsupported
    return startFileStorage(config)
  }

  private suspend fun startFileStorage(config: FileLogConfig): LogStorageResult {
    val result = CyxbsLog.startFileStorage(config)
    if (result == LogStorageResult.Success) onPlatformFileLogStarted(config)
    return result
  }
}
