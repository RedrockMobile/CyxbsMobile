package com.cyxbs.components.log

/** 文件日志管理操作的通用结果。 */
sealed interface LogStorageResult {
  data object Success : LogStorageResult
  data object Unsupported : LogStorageResult
  data class Failure(val message: String) : LogStorageResult
}

enum class LogFlushMode {
  /** 将库内缓冲交给操作系统，不承诺断电后仍保留。 */
  Buffered,

  /** 请求平台持久化同步；平台无法保证时必须返回 Unsupported。 */
  Durable,
}

sealed interface LogFlushResult {
  data object Buffered : LogFlushResult
  data object Durable : LogFlushResult
  data object Unsupported : LogFlushResult
  data class Failure(val message: String) : LogFlushResult
}

/** 文件日志导出结果。成功时 [filePath] 指向稳定的 JSONL 副本。 */
sealed interface LogExportResult {
  data object Unsupported : LogExportResult
  data class Success(
    val filePath: String,
    val exportedBytes: Long,
    val truncated: Boolean,
  ) : LogExportResult
  data class Failure(val message: String) : LogExportResult
}

/** 各日志等级的计数快照，用于定位队列压力下实际丢失的事件。 */
data class FileLogLevelCounts(
  val verbose: Long = 0,
  val debug: Long = 0,
  val info: Long = 0,
  val warn: Long = 0,
  val error: Long = 0,
  val assert: Long = 0,
) {
  operator fun get(level: LogLevel): Long = when (level) {
    LogLevel.Verbose -> verbose
    LogLevel.Debug -> debug
    LogLevel.Info -> info
    LogLevel.Warn -> warn
    LogLevel.Error -> error
    LogLevel.Assert -> assert
  }
}

/** 当前进程内文件日志的可观察健康状态。 */
data class FileLogHealth(
  val state: FileLogState,
  val acceptedEntries: Long,
  val droppedQueueFull: Long,
  val droppedByteBudget: Long,
  val droppedOversize: Long,
  val truncatedEntries: Long,
  val droppedClosed: Long,
  val droppedWriteBackoff: Long,
  val writeFailures: Long,
  val queuedEntries: Int,
  val queuedBytes: Long,
  val droppedByLevel: FileLogLevelCounts = FileLogLevelCounts(),
)

enum class FileLogState {
  Unsupported,
  Stopped,
  Starting,
  Running,
  Closing,
  Failed,
}
