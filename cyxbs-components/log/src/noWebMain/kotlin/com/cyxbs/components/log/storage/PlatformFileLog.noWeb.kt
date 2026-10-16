package com.cyxbs.components.log.storage

import com.cyxbs.components.log.FileLogConfig
import com.cyxbs.components.log.FileLogHealth
import com.cyxbs.components.log.FileLogState
import com.cyxbs.components.log.LogExportResult
import com.cyxbs.components.log.LogFlushMode
import com.cyxbs.components.log.LogFlushResult
import com.cyxbs.components.log.LogLevel
import com.cyxbs.components.log.LogStorageResult

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal actual object PlatformFileLog {
  private val managementMutex = Mutex()
  private val engine = atomic<FileLogEngine?>(null)

  actual fun accepts(level: LogLevel): Boolean = engine.value?.accepts(level) == true

  actual fun offer(event: FileLogEvent) {
    engine.value?.offer(event)
  }

  actual suspend fun start(config: FileLogConfig): LogStorageResult = managementMutex.withLock {
    val previous = engine.value
    previous?.close()
    engine.compareAndSet(previous, null)

    val replacement = FileLogEngine(config)
    try {
      val result = replacement.start()
      if (result == LogStorageResult.Success) {
        engine.value = replacement
      } else {
        withContext(NonCancellable) { replacement.close() }
      }
      result
    } catch (cancellation: CancellationException) {
      withContext(NonCancellable) { replacement.close() }
      throw cancellation
    }
  }

  actual suspend fun flush(mode: LogFlushMode): LogFlushResult =
    engine.value?.flush(mode) ?: LogFlushResult.Failure("file log storage has not started")

  actual suspend fun clear(): LogStorageResult =
    engine.value?.clear() ?: LogStorageResult.Failure("file log storage has not started")

  actual suspend fun export(): LogExportResult =
    engine.value?.export() ?: LogExportResult.Failure("file log storage has not started")

  actual suspend fun close(): LogStorageResult = managementMutex.withLock {
    val current = engine.value ?: return@withLock LogStorageResult.Success
    val result = current.close()
    engine.compareAndSet(current, null)
    result
  }

  actual fun health(): FileLogHealth = engine.value?.health() ?: stoppedHealth()

  private fun stoppedHealth() = FileLogHealth(
    state = FileLogState.Stopped,
    acceptedEntries = 0,
    droppedQueueFull = 0,
    droppedByteBudget = 0,
    droppedOversize = 0,
    truncatedEntries = 0,
    droppedClosed = 0,
    droppedWriteBackoff = 0,
    writeFailures = 0,
    queuedEntries = 0,
    queuedBytes = 0,
  )
}
