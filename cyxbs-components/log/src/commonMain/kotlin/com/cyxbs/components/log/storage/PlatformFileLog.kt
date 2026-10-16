package com.cyxbs.components.log.storage

import com.cyxbs.components.log.FileLogConfig
import com.cyxbs.components.log.FileLogHealth
import com.cyxbs.components.log.LogExportResult
import com.cyxbs.components.log.LogFlushMode
import com.cyxbs.components.log.LogFlushResult
import com.cyxbs.components.log.LogLevel
import com.cyxbs.components.log.LogStorageResult

internal data class FileLogEvent(
  val timestampMillis: Long,
  val level: LogLevel,
  val tag: String,
  val message: String,
  val stackTrace: String?,
  val snapshotTruncated: Boolean,
)

internal expect object PlatformFileLog {
  fun accepts(level: LogLevel): Boolean

  fun offer(event: FileLogEvent)

  suspend fun start(config: FileLogConfig): LogStorageResult

  suspend fun flush(mode: LogFlushMode): LogFlushResult

  suspend fun clear(): LogStorageResult

  suspend fun export(): LogExportResult

  suspend fun close(): LogStorageResult

  fun health(): FileLogHealth
}
