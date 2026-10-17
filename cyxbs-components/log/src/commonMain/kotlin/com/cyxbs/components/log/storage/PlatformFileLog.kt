package com.cyxbs.components.log.storage

import com.cyxbs.components.log.FileLogConfig
import com.cyxbs.components.log.FileLogHealth
import com.cyxbs.components.log.LogExportResult
import com.cyxbs.components.log.LogFlushMode
import com.cyxbs.components.log.LogFlushResult
import com.cyxbs.components.log.LogLevel
import com.cyxbs.components.log.LogStorageResult

internal data class FileLogEvent(
  val eventTimestampMillis: Long,
  val observedTimestampMillis: Long,
  val level: LogLevel,
  val tag: String,
  val message: String,
  val stackTrace: String?,
  val context: PreparedLogContext?,
  val snapshotTruncated: Boolean,
)

internal data class PreparedLogContext(
  val operation: String?,
  val route: String?,
  val method: String?,
  val statusCode: Int?,
  val durationMillis: Long?,
  val result: String?,
  val retryCount: Int?,
  val fromState: String?,
  val toState: String?,
  val correlationId: String?,
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
