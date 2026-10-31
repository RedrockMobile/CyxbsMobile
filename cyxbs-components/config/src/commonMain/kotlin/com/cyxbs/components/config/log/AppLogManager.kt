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
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 文件日志在主进程、隐私已同意后默认开启，用户显式关闭时保持关闭。
 * 控制台日志策略独立于文件采集开关。
 */
object AppLogManager {
  private val initialized = atomic(false)
  private val isMainProcess = atomic(false)
  private val operations = Mutex()
  private val policyVersion = atomic(0L)
  private val policyGate = SynchronizedObject()

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
    CyxbsLog.setFileStorageCollectionEnabled(false)
    setPlatformDiagnosticCollectionEnabled(false)
    if (shouldCollect) {
      appCoroutineScope.launch { ensureStarted() }
    }
  }

  fun isDiagnosticLoggingEnabled(): Boolean =
    defaultSettings.getBoolean(SP_DIAGNOSTIC_LOG_ENABLED, true)

  fun isDiagnosticLoggingSupported(): Boolean = platformFileLogConfig() != null

  fun fileStorageHealth(): FileLogHealth = CyxbsLog.fileStorageHealth()

  suspend fun setDiagnosticLoggingEnabled(enabled: Boolean): LogStorageResult = operations.withLock {
    if (enabled && !defaultSettings.getBoolean(SP_PRIVACY_AGREED, false)) {
      return@withLock LogStorageResult.Failure("请先同意隐私政策")
    }
    if (!enabled) {
      invalidateCollectionPolicy()
      setPlatformDiagnosticCollectionEnabled(false)
      defaultSettings.putBoolean(SP_DIAGNOSTIC_LOG_ENABLED, false)
      return@withLock CyxbsLog.closeFileStorage()
    }
    if (!isMainProcess.value) {
      return@withLock LogStorageResult.Failure("诊断日志仅支持主进程")
    }
    val requestedPolicyVersion = advancePolicyVersion()
    defaultSettings.putBoolean(SP_DIAGNOSTIC_LOG_ENABLED, true)
    try {
      val result = startLocked()
      if (result != LogStorageResult.Success && rollbackEnableIfCurrent(requestedPolicyVersion)) {
        setPlatformDiagnosticCollectionEnabled(false)
      }
      result
    } catch (cancellation: CancellationException) {
      if (rollbackEnableIfCurrent(requestedPolicyVersion)) {
        setPlatformDiagnosticCollectionEnabled(false)
      }
      withContext(NonCancellable) {
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
      if (startedForOperation) {
        withContext(NonCancellable) {
          if (shouldRunFileStorage()) {
            startLocked()
          } else {
            CyxbsLog.setFileStorageCollectionEnabled(false)
            setPlatformDiagnosticCollectionEnabled(false)
            CyxbsLog.closeFileStorage()
          }
        }
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
    advancePolicyVersion()
    if (shouldRunFileStorage()) {
      appCoroutineScope.launch { ensureStarted() }
    }
  }

  fun onPrivacyWithdrawn() {
    invalidateCollectionPolicy()
    setPlatformDiagnosticCollectionEnabled(false)
    appCoroutineScope.launch {
      operations.withLock {
        if (shouldRunFileStorage()) {
          startLocked()
        } else {
          CyxbsLog.setFileStorageCollectionEnabled(false)
          setPlatformDiagnosticCollectionEnabled(false)
          CyxbsLog.closeFileStorage()
        }
      }
    }
  }

  private fun advancePolicyVersion(): Long = synchronized(policyGate) {
    policyVersion.incrementAndGet()
  }

  private fun invalidateCollectionPolicy() {
    synchronized(policyGate) {
      policyVersion.incrementAndGet()
      CyxbsLog.setFileStorageCollectionEnabled(false)
    }
  }

  private fun rollbackEnableIfCurrent(expectedVersion: Long): Boolean = synchronized(policyGate) {
    if (policyVersion.value != expectedVersion) {
      false
    } else {
      policyVersion.incrementAndGet()
      CyxbsLog.setFileStorageCollectionEnabled(false)
      defaultSettings.putBoolean(SP_DIAGNOSTIC_LOG_ENABLED, false)
      true
    }
  }

  private fun tryEnableCollection(expectedVersion: Long): Boolean = synchronized(policyGate) {
    if (policyVersion.value != expectedVersion || !shouldRunFileStorage()) {
      false
    } else {
      CyxbsLog.setFileStorageCollectionEnabled(true)
      true
    }
  }

  internal fun <T> withCurrentDiagnosticCollectionPolicy(
    expectedVersion: Long,
    block: () -> T,
  ): T? = synchronized(policyGate) {
    if (policyVersion.value == expectedVersion && shouldRunFileStorage()) block() else null
  }

  private fun shouldRunFileStorage(): Boolean =
    isMainProcess.value &&
      defaultSettings.getBoolean(SP_PRIVACY_AGREED, false) &&
      isDiagnosticLoggingEnabled()

  private suspend fun ensureStarted(): LogStorageResult = operations.withLock {
    startLocked()
  }

  private suspend fun startLocked(): LogStorageResult {
    val expectedPolicyVersion = policyVersion.value
    if (!shouldRunFileStorage()) return LogStorageResult.Failure("诊断日志当前不可用")
    val current = CyxbsLog.fileStorageHealth().state
    val result = if (current == FileLogState.Running || current == FileLogState.Starting) {
      LogStorageResult.Success
    } else {
      val config = platformFileLogConfig() ?: return LogStorageResult.Unsupported
      startFileStorage(config)
    }
    if (result == LogStorageResult.Success) {
      if (!tryEnableCollection(expectedPolicyVersion)) {
        setPlatformDiagnosticCollectionEnabled(false)
        CyxbsLog.closeFileStorage()
        return LogStorageResult.Failure("诊断日志当前不可用")
      }
      setPlatformDiagnosticCollectionEnabled(
        enabled = true,
        expectedPolicyVersion = expectedPolicyVersion,
      )
      if (withCurrentDiagnosticCollectionPolicy(expectedPolicyVersion) { true } != true) {
        return LogStorageResult.Failure("诊断日志当前不可用")
      }
    }
    return result
  }

  private suspend fun startFileStorage(config: FileLogConfig): LogStorageResult {
    try {
      preparePlatformFileLog(config)
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (_: Throwable) {
      return LogStorageResult.Failure("日志存储平台准备失败")
    }

    val result = CyxbsLog.startFileStorage(config)
    if (result != LogStorageResult.Success) return result
    try {
      onPlatformFileLogStarted(config)
    } catch (cancellation: CancellationException) {
      withContext(NonCancellable) { CyxbsLog.closeFileStorage() }
      throw cancellation
    } catch (_: Throwable) {
      CyxbsLog.closeFileStorage()
      return LogStorageResult.Failure("日志存储平台校验失败")
    }
    return LogStorageResult.Success
  }
}
