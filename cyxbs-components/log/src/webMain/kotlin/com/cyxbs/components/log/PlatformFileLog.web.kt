package com.cyxbs.components.log

internal actual object PlatformFileLog {
  actual fun accepts(level: LogLevel): Boolean = false

  actual fun offer(event: FileLogEvent) = Unit

  actual suspend fun start(config: FileLogConfig): LogStorageResult =
    LogStorageResult.Unsupported

  actual suspend fun flush(mode: LogFlushMode): LogFlushResult = LogFlushResult.Unsupported

  actual suspend fun clear(): LogStorageResult = LogStorageResult.Unsupported

  actual suspend fun export(): LogExportResult = LogExportResult.Unsupported

  actual suspend fun close(): LogStorageResult = LogStorageResult.Unsupported

  actual fun health(): FileLogHealth = FileLogHealth(
    state = FileLogState.Unsupported,
    acceptedEntries = 0,
    droppedQueueFull = 0,
    droppedByteBudget = 0,
    droppedOversize = 0,
    truncatedEntries = 0,
    droppedClosed = 0,
    writeFailures = 0,
    queuedEntries = 0,
    queuedBytes = 0,
  )
}
