package com.cyxbs.components.log.storage

import com.cyxbs.components.log.FileLogConfig
import com.cyxbs.components.log.FileLogHealth
import com.cyxbs.components.log.FileLogLevelCounts
import com.cyxbs.components.log.FileLogState
import com.cyxbs.components.log.LogExportResult
import com.cyxbs.components.log.LogFlushMode
import com.cyxbs.components.log.LogFlushResult
import com.cyxbs.components.log.LogLevel
import com.cyxbs.components.log.LogStorageResult

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okio.BufferedSink
import okio.FileSystem
import okio.HashingSink
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.blackholeSink
import okio.buffer
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private const val QUEUE_CAPACITY = 1_024
private const val QUEUE_BYTE_CAPACITY = 2L * 1_024 * 1_024
private const val HIGH_PRIORITY_RESERVED_PERCENT = 20
private const val LOW_PRIORITY_QUEUE_CAPACITY =
  QUEUE_CAPACITY * (100 - HIGH_PRIORITY_RESERVED_PERCENT) / 100
private const val LOW_PRIORITY_QUEUE_BYTE_CAPACITY =
  QUEUE_BYTE_CAPACITY * (100 - HIGH_PRIORITY_RESERVED_PERCENT) / 100
private const val MAX_ENTRY_BYTES = 16 * 1_024
private const val BATCH_SIZE_BYTES = 64 * 1_024
private const val FLUSH_INTERVAL_MILLIS = 1_000L
private const val MAX_FILES = 8
private const val MAX_EXPORT_BYTES = 32L * 1_024 * 1_024
private const val MAX_EXPORT_SOURCE_FILES = 3
private const val MAX_PUBLISHED_EXPORT_FILES = 1
private const val RECOVERY_SCAN_CHUNK_BYTES = 64 * 1_024
private const val FILE_RETRY_BASE_MILLIS = 250L
private const val FILE_RETRY_MAX_MILLIS = 30_000L
private const val LEGACY_FILE_PREFIX = "cyxbs-log-v1-"
private const val FILE_PREFIX = "cyxbs-log-v2-"
private const val LEGACY_EXPORT_PREFIX = "cyxbs-log-export-v1-"
private const val EXPORT_PREFIX = "cyxbs-log-export-v2-"
private const val GENERATION_WIDTH = 20
private const val FILE_INDEX_WIDTH = 6
private const val DIGEST_BUFFER_BYTES = 64 * 1_024

/**
 * noWeb 平台的有界 JSONL 文件引擎：调用方只入队，单一工作协程负责写盘、轮转、导出和恢复。
 * 所有磁盘异常都隔离在引擎内，并通过健康统计暴露，不得影响业务线程。
 */
internal class FileLogEngine(
  private val config: FileLogConfig,
  private val fileSystem: FileSystem = FileSystem.SYSTEM,
  private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
  private sealed interface Command {
    data class Record(val event: FileLogEvent, val estimatedBytes: Int) : Command
    data class Flush(val result: CompletableDeferred<LogFlushResult>) : Command
    data class Clear(val epoch: Long, val result: CompletableDeferred<LogStorageResult>) : Command
    data class Export(val result: CompletableDeferred<LogExportResult>) : Command
    data object Close : Command
  }

  private val directory = config.directory.toPath()
  private val exportDirectory = config.exportDirectory.toPath()
  private val mailbox = Channel<Command>(Channel.UNLIMITED)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val managementMutex = Mutex()
  private val gate = SynchronizedObject()
  private val accepting = atomic(false)
  private val receptionEpoch = atomic(0L)
  private val state = atomic(FileLogState.Starting)
  private val acceptedEntries = atomic(0L)
  private val droppedQueueFull = atomic(0L)
  private val droppedByteBudget = atomic(0L)
  private val droppedOversize = atomic(0L)
  private val truncatedEntries = atomic(0L)
  private val droppedClosed = atomic(0L)
  private val droppedWriteBackoff = atomic(0L)
  private val droppedVerbose = atomic(0L)
  private val droppedDebug = atomic(0L)
  private val droppedInfo = atomic(0L)
  private val droppedWarn = atomic(0L)
  private val droppedError = atomic(0L)
  private val droppedAssert = atomic(0L)
  private val writeFailures = atomic(0L)
  private val lastSuccessfulWriteAtMillis = atomic(0L)
  private val lastWriteFailureAtMillis = atomic(0L)
  private val lastWriteFailureType = atomic<String?>(null)
  private val consecutiveWriteFailures = atomic(0)
  private val lastFlushDurationMillis = atomic(-1L)
  private val managedFileCount = atomic(0)
  private val managedBytes = atomic(0L)
  private val queuedEntries = atomic(0)
  private val queuedBytes = atomic(0L)
  private val queuedLowPriorityEntries = atomic(0)
  private val queuedLowPriorityBytes = atomic(0L)
  private val ready = CompletableDeferred<LogStorageResult>()
  private val closeResult = CompletableDeferred<LogStorageResult>()
  private val worker = atomic<Job?>(null)
  private val session = "${nowMillis()}-${Random.nextLong().toULong().toString(16)}"
  private var fileFailureStreak = 0
  private var fileRetryAfter: TimeMark? = null

  fun accepts(level: LogLevel): Boolean = accepting.value && level >= config.minLevel

  fun offer(event: FileLogEvent) {
    val estimatedBytes = estimatePayloadBytes(event)
    synchronized(gate) {
      if (!accepting.value) {
        droppedClosed.incrementAndGet()
        incrementDropped(event.level)
        return
      }
      if (event.level < config.minLevel) return
      val lowPriority = event.level < LogLevel.Warn
      if (queuedEntries.value >= QUEUE_CAPACITY) {
        droppedQueueFull.incrementAndGet()
        incrementDropped(event.level)
        return
      }
      if (lowPriority && queuedLowPriorityEntries.value >= LOW_PRIORITY_QUEUE_CAPACITY) {
        droppedQueueFull.incrementAndGet()
        incrementDropped(event.level)
        return
      }
      if (queuedBytes.value + estimatedBytes > QUEUE_BYTE_CAPACITY) {
        droppedByteBudget.incrementAndGet()
        incrementDropped(event.level)
        return
      }
      if (lowPriority && queuedLowPriorityBytes.value + estimatedBytes > LOW_PRIORITY_QUEUE_BYTE_CAPACITY) {
        droppedByteBudget.incrementAndGet()
        incrementDropped(event.level)
        return
      }
      adjustQueuedRecord(event.level, estimatedBytes, delta = 1)
      val result = mailbox.trySend(Command.Record(event, estimatedBytes))
      if (result.isFailure) {
        adjustQueuedRecord(event.level, estimatedBytes, delta = -1)
        droppedClosed.incrementAndGet()
        incrementDropped(event.level)
      } else {
        acceptedEntries.incrementAndGet()
      }
    }
  }

  /** 启动单一写入协程，先恢复未完成文件，再建立新的 active 文件。 */
  suspend fun start(): LogStorageResult {
    if (worker.value == null) {
      val launched = scope.launch { runWorker() }
      if (!worker.compareAndSet(null, launched)) launched.cancel()
    }
    return ready.await()
  }

  suspend fun flush(mode: LogFlushMode): LogFlushResult {
    if (mode == LogFlushMode.Durable) return LogFlushResult.Unsupported
    return managementMutex.withLock {
      val result = CompletableDeferred<LogFlushResult>()
      if (!enqueueWhileRunning(Command.Flush(result))) return@withLock unavailableFlushResult()
      result.await()
    }
  }

  /** 在工作协程内清除原始日志和导出副本，再重新打开空的 active 文件。 */
  suspend fun clear(): LogStorageResult = managementMutex.withLock {
    val result = CompletableDeferred<LogStorageResult>()
    val accepted = synchronized(gate) {
      if (state.value != FileLogState.Running) return@synchronized false
      accepting.value = false
      val epoch = receptionEpoch.incrementAndGet()
      mailbox.trySend(Command.Clear(epoch, result)).isSuccess
    }
    if (!accepted) return@withLock unavailableStorageResult()
    result.await()
  }

  /** 将有界源文件流式合并为带 manifest 的 JSONL 副本，并在完成后清理旧副本。 */
  suspend fun export(): LogExportResult = managementMutex.withLock {
    val result = CompletableDeferred<LogExportResult>()
    if (!enqueueWhileRunning(Command.Export(result))) {
      return@withLock LogExportResult.Failure("file log storage is ${state.value.name.lowercase()}")
    }
    result.await()
  }

  suspend fun close(): LogStorageResult {
    val enqueueFailed = synchronized(gate) {
      when (state.value) {
        FileLogState.Stopped -> {
          closeResult.complete(LogStorageResult.Success)
          false
        }
        FileLogState.Unsupported -> {
          closeResult.complete(LogStorageResult.Unsupported)
          false
        }
        FileLogState.Failed, FileLogState.Closing -> false
        else -> {
          accepting.value = false
          receptionEpoch.incrementAndGet()
          state.value = FileLogState.Closing
          !mailbox.trySend(Command.Close).isSuccess
        }
      }
    }
    if (enqueueFailed) finishFailed("file log mailbox is closed")
    return closeResult.await()
  }

  fun health(): FileLogHealth = FileLogHealth(
    state = state.value,
    acceptedEntries = acceptedEntries.value,
    droppedQueueFull = droppedQueueFull.value,
    droppedByteBudget = droppedByteBudget.value,
    droppedOversize = droppedOversize.value,
    truncatedEntries = truncatedEntries.value,
    droppedClosed = droppedClosed.value,
    droppedWriteBackoff = droppedWriteBackoff.value,
    writeFailures = writeFailures.value,
    queuedEntries = queuedEntries.value,
    queuedBytes = queuedBytes.value,
    droppedByLevel = FileLogLevelCounts(
      verbose = droppedVerbose.value,
      debug = droppedDebug.value,
      info = droppedInfo.value,
      warn = droppedWarn.value,
      error = droppedError.value,
      assert = droppedAssert.value,
    ),
    lastSuccessfulWriteAtMillis = lastSuccessfulWriteAtMillis.value.takeIf { it > 0 },
    lastWriteFailureAtMillis = lastWriteFailureAtMillis.value.takeIf { it > 0 },
    lastWriteFailureType = lastWriteFailureType.value,
    consecutiveWriteFailures = consecutiveWriteFailures.value,
    lastFlushDurationMillis = lastFlushDurationMillis.value.takeIf { it >= 0 },
    managedFileCount = managedFileCount.value,
    managedBytes = managedBytes.value,
  )

  private fun adjustQueuedRecord(level: LogLevel, estimatedBytes: Int, delta: Int) {
    queuedEntries.addAndGet(delta)
    queuedBytes.addAndGet(estimatedBytes.toLong() * delta)
    if (level < LogLevel.Warn) {
      queuedLowPriorityEntries.addAndGet(delta)
      queuedLowPriorityBytes.addAndGet(estimatedBytes.toLong() * delta)
    }
  }

  private fun incrementDropped(level: LogLevel) {
    when (level) {
      LogLevel.Verbose -> droppedVerbose.incrementAndGet()
      LogLevel.Debug -> droppedDebug.incrementAndGet()
      LogLevel.Info -> droppedInfo.incrementAndGet()
      LogLevel.Warn -> droppedWarn.incrementAndGet()
      LogLevel.Error -> droppedError.incrementAndGet()
      LogLevel.Assert -> droppedAssert.incrementAndGet()
    }
  }

  private fun updateFileMetrics(metrics: WorkerFileMetrics) {
    managedFileCount.value = metrics.fileCount
    managedBytes.value = metrics.totalBytes
  }

  private fun recordWriteFailure(throwable: Throwable) {
    writeFailures.incrementAndGet()
    if (consecutiveWriteFailures.value < Int.MAX_VALUE) {
      consecutiveWriteFailures.incrementAndGet()
    }
    lastWriteFailureAtMillis.value = nowMillis()
    lastWriteFailureType.value = throwable::class.simpleName ?: "UnknownFailure"
  }

  private inline fun <T> measureFlushDuration(block: () -> T): T {
    val startedAt = TimeSource.Monotonic.markNow()
    return try {
      block()
    } finally {
      lastFlushDurationMillis.value = startedAt.elapsedNow().inWholeMilliseconds.coerceAtLeast(0)
    }
  }

  private fun enqueueWhileRunning(command: Command): Boolean = synchronized(gate) {
    state.value == FileLogState.Running && mailbox.trySend(command).isSuccess
  }

  private fun finishFailed(message: String) {
    synchronized(gate) {
      state.value = FileLogState.Failed
      accepting.value = false
      mailbox.close()
    }
    val failure = LogStorageResult.Failure(message)
    if (!ready.isCompleted) ready.complete(failure)
    closeResult.complete(failure)
    failPendingCommands(IOException(message))
  }

  private suspend fun runWorker() {
    val files = WorkerFiles(
      config = config,
      directory = directory,
      exportDirectory = exportDirectory,
      session = session,
      fileSystem = fileSystem,
      nowMillis = nowMillis,
      onMetricsChanged = ::updateFileMetrics,
    )
    try {
      files.initialize()
      synchronized(gate) {
        if (state.value == FileLogState.Starting) {
          state.value = FileLogState.Running
          accepting.value = true
        }
      }
      ready.complete(LogStorageResult.Success)

      var running = true
      var lastFlush = TimeSource.Monotonic.markNow()
      while (running) {
        val remainingMillis = (FLUSH_INTERVAL_MILLIS - lastFlush.elapsedNow().inWholeMilliseconds)
          .coerceAtLeast(1)
        val command = withTimeoutOrNull(remainingMillis) { mailbox.receive() }
        if (command == null) {
          runFileOperation(files, countBackoffDrop = false) {
            measureFlushDuration { flush() }
          }
          lastFlush = TimeSource.Monotonic.markNow()
          continue
        }
        when (command) {
          is Command.Record -> {
            adjustQueuedRecord(command.event.level, command.estimatedBytes, delta = -1)
            val encoded = encodeLogLine(command.event, session, files.nextSequence(), MAX_ENTRY_BYTES)
            if (encoded == null) {
              droppedOversize.incrementAndGet()
              incrementDropped(command.event.level)
            } else {
              if (encoded.truncated) truncatedEntries.incrementAndGet()
              val written = runFileOperation(files, countBackoffDrop = true) { write(encoded.bytes) }
              if (!written) incrementDropped(command.event.level)
                if (lastFlush.elapsedNow().inWholeMilliseconds >= FLUSH_INTERVAL_MILLIS) {
                  runFileOperation(files, countBackoffDrop = false) {
                    measureFlushDuration { flush() }
                  }
                  lastFlush = TimeSource.Monotonic.markNow()
                }
            }
          }
          is Command.Flush -> {
            val outcome = runCommand(
              failure = { LogFlushResult.Failure(it) },
              block = {
                measureFlushDuration { files.flush() }
                LogFlushResult.Buffered
              },
            )
            if (outcome is LogFlushResult.Failure) {
              files.recoverAfterFailure()
              scheduleFileRetry()
            } else {
              resetFileRetryBackoff()
            }
            command.result.complete(outcome)
            lastFlush = TimeSource.Monotonic.markNow()
          }
          is Command.Clear -> {
            val outcome = runCommand(
              failure = { LogStorageResult.Failure(it) },
              block = {
                files.clear()
                LogStorageResult.Success
              },
            )
            if (outcome != LogStorageResult.Success) {
              files.recoverAfterFailure()
              scheduleFileRetry()
            } else {
              resetFileRetryBackoff()
            }
            if (state.value == FileLogState.Running &&
              receptionEpoch.value == command.epoch
            ) {
              synchronized(gate) {
                if (receptionEpoch.value == command.epoch) accepting.value = true
              }
            }
            command.result.complete(outcome)
            lastFlush = TimeSource.Monotonic.markNow()
          }
          is Command.Export -> {
            val outcome = runCommand(
              failure = { LogExportResult.Failure(it) },
              block = { files.export() },
            )
            if (outcome is LogExportResult.Failure) {
              files.recoverAfterFailure()
              scheduleFileRetry()
            } else {
              resetFileRetryBackoff()
            }
            command.result.complete(outcome)
            lastFlush = TimeSource.Monotonic.markNow()
          }
          Command.Close -> {
            val outcome = runCommand(
              failure = { LogStorageResult.Failure(it) },
              block = {
                files.close()
                LogStorageResult.Success
              },
            )
            synchronized(gate) {
              state.value = if (outcome == LogStorageResult.Success) {
                FileLogState.Stopped
              } else {
                FileLogState.Failed
              }
              mailbox.close()
            }
            closeResult.complete(outcome)
            running = false
          }
        }
      }
    } catch (cancellation: CancellationException) {
      if (!ready.isCompleted) ready.completeExceptionally(cancellation)
      finishFailed("file log worker was cancelled")
      throw cancellation
    } catch (throwable: Throwable) {
      recordWriteFailure(throwable)
      finishFailed(throwable.safeMessage())
    } finally {
      synchronized(gate) {
        accepting.value = false
        mailbox.close()
      }
      runCatching { files.close() }
      failPendingCommands(IOException("file log worker has stopped"))
      scope.cancel()
    }
  }

  private inline fun <T> runCommand(
    failure: (String) -> T,
    block: () -> T,
  ): T {
    return try {
      block()
    } catch (throwable: Throwable) {
      if (throwable is CancellationException) throw throwable
      recordWriteFailure(throwable)
      failure(throwable.safeMessage())
    }
  }

  private fun failPendingCommands(throwable: Throwable) {
    while (true) {
      when (val command = mailbox.tryReceive().getOrNull() ?: break) {
        is Command.Record -> {
          adjustQueuedRecord(command.event.level, command.estimatedBytes, delta = -1)
          incrementDropped(command.event.level)
        }
        is Command.Flush -> command.result.complete(LogFlushResult.Failure(throwable.safeMessage()))
        is Command.Clear -> command.result.complete(LogStorageResult.Failure(throwable.safeMessage()))
        is Command.Export -> command.result.complete(LogExportResult.Failure(throwable.safeMessage()))
        Command.Close -> closeResult.complete(LogStorageResult.Failure(throwable.safeMessage()))
      }
    }
  }

  private inline fun runFileOperation(
    files: WorkerFiles,
    countBackoffDrop: Boolean,
    block: WorkerFiles.() -> Unit,
  ): Boolean {
    if (fileRetryAfter?.hasPassedNow() == false) {
      if (countBackoffDrop) droppedWriteBackoff.incrementAndGet()
      return false
    }
    return try {
      files.block()
      if (countBackoffDrop) {
        lastSuccessfulWriteAtMillis.value = nowMillis()
        consecutiveWriteFailures.value = 0
      }
      resetFileRetryBackoff()
      true
    } catch (throwable: Throwable) {
      if (throwable is CancellationException) throw throwable
      recordWriteFailure(throwable)
      files.recoverAfterFailure()
      scheduleFileRetry()
      false
    }
  }

  private fun scheduleFileRetry() {
    fileFailureStreak = (fileFailureStreak + 1).coerceAtMost(8)
    val multiplier = 1L shl (fileFailureStreak - 1)
    val retryMillis = (FILE_RETRY_BASE_MILLIS * multiplier).coerceAtMost(FILE_RETRY_MAX_MILLIS)
    fileRetryAfter = TimeSource.Monotonic.markNow() + retryMillis.milliseconds
  }

  private fun resetFileRetryBackoff() {
    fileFailureStreak = 0
    fileRetryAfter = null
  }

  private fun unavailableStorageResult(): LogStorageResult = when (state.value) {
    FileLogState.Unsupported -> LogStorageResult.Unsupported
    else -> LogStorageResult.Failure("file log storage is ${state.value.name.lowercase()}")
  }

  private fun unavailableFlushResult(): LogFlushResult = when (state.value) {
    FileLogState.Unsupported -> LogFlushResult.Unsupported
    else -> LogFlushResult.Failure("file log storage is ${state.value.name.lowercase()}")
  }
}

private data class WorkerFileMetrics(
  val fileCount: Int,
  val totalBytes: Long,
)

private class WorkerFiles(
  private val config: FileLogConfig,
  private val directory: Path,
  private val exportDirectory: Path,
  private val session: String,
  private val fileSystem: FileSystem,
  private val nowMillis: () -> Long,
  private val onMetricsChanged: (WorkerFileMetrics) -> Unit,
) {
  private val maxFileBytes = min(4L * 1_024 * 1_024, config.maxTotalBytes)
  private var generation = 0L
  private var exportGeneration = 0L
  private var fileIndex = 0
  private var sequence = 0L
  private var sink: BufferedSink? = null
  private var activePath: Path? = null
  private var activeBytes = 0L
  private var pendingBytes = 0
  private var managedFileCount = 0
  private var managedBytes = 0L

  fun nextSequence(): Long = ++sequence

  fun initialize() {
    fileSystem.createDirectories(directory)
    fileSystem.createDirectories(exportDirectory)
    generation = nextGeneration(managedLogFiles(includeActive = true), FILE_PREFIX)
    exportGeneration = maxGeneration(managedExportFiles(), EXPORT_PREFIX)
    recoverActiveFiles()
    cleanupExports()
    cleanupLogs(reserveActiveFile = true)
    openActive()
  }

  fun write(bytes: ByteArray) {
    if (activeBytes > 0 && activeBytes + bytes.size > maxFileBytes) {
      sealActive()
      cleanupLogs(reserveActiveFile = true)
      openActive()
    }
    cleanupLogs(extraActiveBytes = bytes.size.toLong())
    sink?.write(bytes) ?: throw IOException("active log sink is unavailable")
    activeBytes += bytes.size
    pendingBytes += bytes.size
    if (pendingBytes >= BATCH_SIZE_BYTES) flush()
  }

  fun flush() {
    (sink ?: throw IOException("active log sink is unavailable")).flush()
    pendingBytes = 0
  }

  fun clear() {
    closeActive(deleteEmpty = true)
    managedLogFiles(includeActive = true).forEach { path -> fileSystem.delete(path, mustExist = false) }
    cleanupExports(deleteAll = true)
    managedFileCount = 1
    managedBytes = 0
    publishMetrics()
    openActive()
  }

  fun export(): LogExportResult {
    sealActive()
    cleanupLogs(reserveActiveFile = true)
    openActive()

    val candidates = managedLogFiles(includeActive = false)
      .sortedWith(managedFileComparatorDescending(FILE_PREFIX))
    val selected = ArrayList<Path>()
    var selectedBytes = 0L
    var truncated = false
    for ((index, path) in candidates.withIndex()) {
      if (index >= MAX_EXPORT_SOURCE_FILES) {
        truncated = true
        break
      }
      val size = fileSystem.metadata(path).size ?: 0L
      if (selectedBytes + size > MAX_EXPORT_BYTES) {
        truncated = true
        continue
      }
      selected += path
      selectedBytes += size
    }
    selected.reverse()

    fileSystem.createDirectories(exportDirectory)
    val generatedAtMillis = nowMillis()
    val payloadIntegrity = calculatePayloadIntegrity(selected)
    val manifest = encodeExportManifest(
      generatedAtMillis = generatedAtMillis,
      sourceFileCount = selected.size,
      exportedLogBytes = selectedBytes,
      exportedRecordCount = payloadIntegrity.recordCount,
      payloadSha256 = payloadIntegrity.sha256,
      truncated = truncated,
    )
    exportGeneration += 1
    val generationPart = exportGeneration.toString().padStart(GENERATION_WIDTH, '0')
    val id = "$generationPart-$generatedAtMillis-${Random.nextLong().toULong().toString(16)}"
    val partial = exportDirectory / "$EXPORT_PREFIX$id.jsonl.partial"
    val published = exportDirectory / "$EXPORT_PREFIX$id.jsonl"
    try {
      val output = fileSystem.sink(partial).buffer()
      try {
        output.write(manifest)
        selected.forEach { path ->
          val input = fileSystem.source(path)
          try {
            output.writeAll(input)
          } finally {
            input.close()
          }
        }
      } finally {
        output.close()
      }
      fileSystem.atomicMove(partial, published)
      cleanupExports(except = published)
      return LogExportResult.Success(
        filePath = published.toString(),
        exportedBytes = selectedBytes + manifest.size,
        truncated = truncated,
        exportedRecordCount = payloadIntegrity.recordCount,
        payloadSha256 = payloadIntegrity.sha256,
      )
    } finally {
      fileSystem.delete(partial, mustExist = false)
    }
  }

  private data class PayloadIntegrity(
    val recordCount: Long,
    val sha256: String,
  )

  private fun calculatePayloadIntegrity(paths: List<Path>): PayloadIntegrity {
    val hashingSink = HashingSink.sha256(blackholeSink())
    val hashingOutput = hashingSink.buffer()
    val chunk = ByteArray(DIGEST_BUFFER_BYTES)
    var recordCount = 0L
    try {
      paths.forEach { path ->
        val input = fileSystem.source(path).buffer()
        try {
          while (true) {
            val read = input.read(chunk)
            if (read == -1) break
            for (index in 0 until read) {
              if (chunk[index] == '\n'.code.toByte()) recordCount += 1
            }
            hashingOutput.write(chunk, 0, read)
          }
        } finally {
          input.close()
        }
      }
    } finally {
      hashingOutput.close()
    }
    return PayloadIntegrity(recordCount, hashingSink.hash.hex())
  }

  fun close() {
    closeActive(deleteEmpty = true)
  }

  private fun openActive() {
    check(sink == null) { "active sink is already open" }
    fileIndex += 1
    val generationPart = generation.toString().padStart(GENERATION_WIDTH, '0')
    val indexPart = fileIndex.toString().padStart(FILE_INDEX_WIDTH, '0')
    activePath = directory / "$FILE_PREFIX$generationPart-$session-$indexPart.active"
    sink = fileSystem.appendingSink(activePath!!, mustExist = false).buffer()
    activeBytes = fileSystem.metadata(activePath!!).size ?: 0L
    pendingBytes = 0
  }

  private fun sealActive() {
    val path = activePath ?: return
    closeActive(deleteEmpty = true)
    if (!fileSystem.exists(path)) return
    val sealed = path.parent!! / "${path.name.removeSuffix(".active")}.jsonl"
    fileSystem.atomicMove(path, sealed)
  }

  private fun closeActive(deleteEmpty: Boolean) {
    val path = activePath
    try {
      try {
        sink?.flush()
      } finally {
        sink?.close()
      }
    } finally {
      sink = null
      activePath = null
      activeBytes = 0
      pendingBytes = 0
    }
    if (deleteEmpty && path != null && fileSystem.exists(path)) {
      val size = fileSystem.metadata(path).size ?: 0L
      if (size == 0L) {
        fileSystem.delete(path, mustExist = false)
        managedFileCount = (managedFileCount - 1).coerceAtLeast(0)
        publishMetrics()
      }
    }
  }

  fun recoverAfterFailure(): Boolean {
    runCatching { closeActive(deleteEmpty = true) }
    if (sink != null) return true
    return runCatching {
      recoverActiveFiles()
      cleanupLogs(reserveActiveFile = true)
      openActive()
      flush()
    }.isSuccess
  }

  private fun recoverActiveFiles() {
    managedLogFiles(includeActive = true)
      .filter { it.name.endsWith(".active") }
      .forEach { path ->
        val size = fileSystem.metadata(path).size ?: 0L
        if (size > maxFileBytes) {
          fileSystem.delete(path, mustExist = false)
          return@forEach
        }
        val lastNewline = findLastNewline(path, size)
        if (lastNewline < 0L) {
          fileSystem.delete(path, mustExist = false)
        } else {
          if (lastNewline != size - 1L) {
            val handle = fileSystem.openReadWrite(path)
            try {
              handle.resize(lastNewline + 1L)
              handle.flush()
            } finally {
              handle.close()
            }
          }
          val sealed = path.parent!! / "${path.name.removeSuffix(".active")}.jsonl"
          fileSystem.atomicMove(path, sealed)
        }
      }
  }

  /** 从文件尾部按固定小块查找最后一条完整 JSONL 的换行符，避免恢复时整文件读入内存。 */
  private fun findLastNewline(path: Path, size: Long): Long {
    if (size <= 0L) return -1L
    val chunk = ByteArray(RECOVERY_SCAN_CHUNK_BYTES)
    val handle = fileSystem.openReadOnly(path)
    try {
      var chunkEnd = size
      while (chunkEnd > 0L) {
        val chunkStart = (chunkEnd - chunk.size).coerceAtLeast(0L)
        val requested = (chunkEnd - chunkStart).toInt()
        var filled = 0
        while (filled < requested) {
          val read = handle.read(chunkStart + filled, chunk, filled, requested - filled)
          if (read <= 0) return -1L
          filled += read
        }
        for (index in requested - 1 downTo 0) {
          if (chunk[index] == '\n'.code.toByte()) return chunkStart + index
        }
        chunkEnd = chunkStart
      }
    } finally {
      handle.close()
    }
    return -1L
  }

  private fun cleanupLogs(
    extraActiveBytes: Long = 0L,
    reserveActiveFile: Boolean = false,
  ) {
    val now = nowMillis()
    val sealed = managedLogFiles(includeActive = false)
      .map { path -> path to fileSystem.metadata(path) }
      .sortedWith(Comparator { left, right -> compareManagedLogs(left.first, right.first) })
      .toMutableList()

    sealed.removeAll { (path, metadata) ->
      val modified = metadata.lastModifiedAtMillis
      val expired = modified != null && now >= modified && now - modified > config.maxAgeMillis
      if (expired) fileSystem.delete(path, mustExist = false)
      expired
    }

    var totalBytes = activeBytes + extraActiveBytes + sealed.sumOf { (_, metadata) -> metadata.size ?: 0L }
    val activeFileCount = when {
      activePath != null -> 1
      reserveActiveFile -> 1
      else -> 0
    }
    while (sealed.isNotEmpty() &&
      (sealed.size + activeFileCount > MAX_FILES || totalBytes > config.maxTotalBytes)
    ) {
      val (path, metadata) = sealed.removeAt(0)
      fileSystem.delete(path, mustExist = false)
      totalBytes -= metadata.size ?: 0L
    }
    managedFileCount = sealed.size + activeFileCount
    managedBytes = totalBytes.coerceAtLeast(0)
    publishMetrics()
  }

  private fun cleanupExports(except: Path? = null, deleteAll: Boolean = false) {
    if (!fileSystem.exists(exportDirectory)) return
    val exports = managedExportFiles()
      .sortedWith(Comparator { left, right -> compareManagedExports(right, left) })
    var retainedPublishedFiles = 0
    exports.forEach { path ->
      if (path == except) return@forEach
      val modifiedAt = fileSystem.metadata(path).lastModifiedAtMillis ?: nowMillis()
      val expired = nowMillis() >= modifiedAt && nowMillis() - modifiedAt > config.maxAgeMillis
      val partial = path.name.endsWith(".partial")
      val mustDelete = deleteAll || except != null || expired || partial ||
        retainedPublishedFiles >= MAX_PUBLISHED_EXPORT_FILES
      if (mustDelete) {
        fileSystem.delete(path, mustExist = false)
      } else {
        retainedPublishedFiles++
      }
    }
  }

  private fun publishMetrics() {
    onMetricsChanged(WorkerFileMetrics(managedFileCount, managedBytes))
  }

  private fun managedLogFiles(includeActive: Boolean): List<Path> {
    if (!fileSystem.exists(directory)) return emptyList()
    return fileSystem.list(directory).filter { path ->
      (path.name.startsWith(FILE_PREFIX) || path.name.startsWith(LEGACY_FILE_PREFIX)) &&
        (path.name.endsWith(".jsonl") || includeActive && path.name.endsWith(".active"))
    }
  }

  private fun managedExportFiles(): List<Path> {
    if (!fileSystem.exists(exportDirectory)) return emptyList()
    return fileSystem.list(exportDirectory).filter { path ->
      path.name.startsWith(EXPORT_PREFIX) || path.name.startsWith(LEGACY_EXPORT_PREFIX)
    }
  }

  private fun managedFileComparatorDescending(prefix: String): Comparator<Path> = Comparator { left, right ->
    compareManaged(right, left, prefix)
  }

  private fun compareManagedLogs(left: Path, right: Path): Int = compareManaged(left, right, FILE_PREFIX)

  private fun compareManagedExports(left: Path, right: Path): Int = compareManaged(left, right, EXPORT_PREFIX)

  private fun compareManaged(left: Path, right: Path, prefix: String): Int {
    val leftGeneration = parseGeneration(left, prefix)
    val rightGeneration = parseGeneration(right, prefix)
    if (leftGeneration != null || rightGeneration != null) {
      if (leftGeneration == null) return -1
      if (rightGeneration == null) return 1
      val generationComparison = leftGeneration.compareTo(rightGeneration)
      if (generationComparison != 0) return generationComparison
      val indexComparison = parseFileIndex(left).compareTo(parseFileIndex(right))
      if (indexComparison != 0) return indexComparison
    }
    val modifiedComparison = (fileSystem.metadata(left).lastModifiedAtMillis ?: Long.MIN_VALUE)
      .compareTo(fileSystem.metadata(right).lastModifiedAtMillis ?: Long.MIN_VALUE)
    return if (modifiedComparison != 0) modifiedComparison else left.name.compareTo(right.name)
  }

  private fun nextGeneration(paths: List<Path>, prefix: String): Long {
    val maximum = maxGeneration(paths, prefix)
    check(maximum < Long.MAX_VALUE) { "file log generation is exhausted" }
    return maximum + 1
  }

  private fun maxGeneration(paths: List<Path>, prefix: String): Long =
    paths.maxOfOrNull { path -> parseGeneration(path, prefix) ?: 0L } ?: 0L

  private fun parseGeneration(path: Path, prefix: String): Long? = path.name
    .takeIf { it.startsWith(prefix) }
    ?.removePrefix(prefix)
    ?.substringBefore('-')
    ?.toLongOrNull()

  private fun parseFileIndex(path: Path): Int = path.name
    .substringBeforeLast('.')
    .substringAfterLast('-')
    .toIntOrNull()
    ?: 0
}

private fun estimatePayloadBytes(event: FileLogEvent): Int {
  val stackBytes = event.stackTrace?.encodeToByteArray()?.size ?: 0
  val contextBytes = event.context?.let { context ->
    listOfNotNull(
      context.operation,
      context.route,
      context.method,
      context.result,
      context.fromState,
      context.toState,
      context.correlationId,
    ).sumOf { it.encodeToByteArray().size }
  } ?: 0
  return (
    event.tag.encodeToByteArray().size +
      event.message.encodeToByteArray().size +
      stackBytes +
      contextBytes +
      256
    ).coerceAtMost(Int.MAX_VALUE)
}

private fun Throwable.safeMessage(): String = message?.take(512) ?: this::class.simpleName ?: "unknown error"
