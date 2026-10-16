package com.cyxbs.components.log.storage

import com.cyxbs.components.log.CyxbsLog
import com.cyxbs.components.log.FileLogConfig
import com.cyxbs.components.log.FileLogState
import com.cyxbs.components.log.LogConfig
import com.cyxbs.components.log.LogExportResult
import com.cyxbs.components.log.LogFlushMode
import com.cyxbs.components.log.LogFlushResult
import com.cyxbs.components.log.LogLevel
import com.cyxbs.components.log.LogStorageResult

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.FileHandle
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.ForwardingSink
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.Sink
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FileLogEngineTest {
  private val fileSystem = FileSystem.SYSTEM

  @Test
  fun writesParseableJsonlAndExportsOnlySealedFiles() = runTest {
    withTemporaryDirectories { logDirectory, exportDirectory ->
      val engine = FileLogEngine(config(logDirectory, exportDirectory))
      assertEquals(LogStorageResult.Success, engine.start())

      engine.offer(event(message = "课表同步\n完成 \"ok\""))
      engine.offer(event(level = LogLevel.Error, message = "解析失败", stackTrace = "line1\nline2"))
      assertEquals(LogFlushResult.Buffered, engine.flush(LogFlushMode.Buffered))

      val export = assertIs<LogExportResult.Success>(engine.export())
      val lines = fileSystem.read(export.filePath.toPath()) { readUtf8() }
        .lineSequence()
        .filter { it.isNotBlank() }
        .toList()
      assertEquals(3, lines.size)
      lines.forEach { Json.parseToJsonElement(it).jsonObject }
      val manifest = Json.parseToJsonElement(lines.first()).jsonObject
      assertEquals("manifest", manifest.getValue("recordType").jsonPrimitive.content)
      assertEquals("1", manifest.getValue("sourceFileCount").jsonPrimitive.content)
      assertEquals(fileSystem.metadata(export.filePath.toPath()).size, export.exportedBytes)
      assertEquals("课表同步\n完成 \"ok\"", lines[1].let {
        Json.parseToJsonElement(it).jsonObject.getValue("message").jsonPrimitive.content
      })
      assertFalse(export.truncated)
      assertTrue(fileSystem.list(exportDirectory.toPath()).none { it.name.endsWith(".partial") })

      assertEquals(LogStorageResult.Success, engine.close())
      assertEquals(FileLogState.Stopped, engine.health().state)
    }
  }

  @Test
  fun rotationAndCleanupKeepManagedFilesWithinTotalBudget() = runTest {
    withTemporaryDirectories { logDirectory, exportDirectory ->
      val maxTotalBytes = 256L * 1_024
      val engine = FileLogEngine(
        config(logDirectory, exportDirectory).copy(maxTotalBytes = maxTotalBytes)
      )
      assertEquals(LogStorageResult.Success, engine.start())

      repeat(100) { index ->
        engine.offer(event(message = "$index:${"界".repeat(5_000)}"))
      }
      assertEquals(LogFlushResult.Buffered, engine.flush(LogFlushMode.Buffered))

      val files = fileSystem.list(logDirectory.toPath())
        .filter { it.name.startsWith("cyxbs-log-v1-") }
      val totalBytes = files.sumOf { fileSystem.metadata(it).size ?: 0L }
      assertTrue(totalBytes <= maxTotalBytes, "managed bytes $totalBytes exceeded $maxTotalBytes")
      assertTrue(files.size <= 8)
      assertEquals(0, engine.health().queuedEntries)

      val firstExport = assertIs<LogExportResult.Success>(engine.export())
      assertTrue(fileSystem.exists(firstExport.filePath.toPath()))

      assertEquals(LogStorageResult.Success, engine.clear())
      val afterClear = fileSystem.list(logDirectory.toPath())
        .filter { it.name.endsWith(".jsonl") }
      assertTrue(afterClear.isEmpty())
      assertFalse(fileSystem.exists(firstExport.filePath.toPath()))
      assertTrue(fileSystem.list(exportDirectory.toPath()).none { it.name.startsWith("cyxbs-log-export-v1-") })
      assertEquals(LogStorageResult.Success, engine.close())
    }
  }

  @Test
  fun quietTailIsFlushedWithoutAnotherLogEntry() = runTest {
    withTemporaryDirectories { logDirectory, exportDirectory ->
      val engine = FileLogEngine(config(logDirectory, exportDirectory))
      assertEquals(LogStorageResult.Success, engine.start())
      engine.offer(event(message = "quiet-tail"))

      val deadlineNanos = System.nanoTime() + 3_000_000_000L
      var persisted = false
      while (!persisted && System.nanoTime() < deadlineNanos) {
        Thread.sleep(50)
        persisted = fileSystem.list(logDirectory.toPath()).any { path ->
          (fileSystem.metadata(path).size ?: 0L) > 0L
        }
      }
      assertTrue(persisted, "quiet tail was not flushed before the deadline")
      assertEquals(LogStorageResult.Success, engine.close())
    }
  }

  @Test
  fun recoveryDropsPartialTailBeforePublishingExport() = runTest {
    withTemporaryDirectories { logDirectory, exportDirectory ->
      val logPath = logDirectory.toPath()
      fileSystem.createDirectories(logPath)
      fileSystem.write(logPath / "cyxbs-log-v1-old-000001.active") {
        writeUtf8("{\"ok\":true}\npartial-tail")
      }

      val engine = FileLogEngine(config(logDirectory, exportDirectory))
      assertEquals(LogStorageResult.Success, engine.start())
      val export = assertIs<LogExportResult.Success>(engine.export())
      val lines = fileSystem.read(export.filePath.toPath()) { readUtf8() }
        .lineSequence()
        .filter { it.isNotBlank() }
        .toList()
      assertEquals(2, lines.size)
      assertEquals("manifest", Json.parseToJsonElement(lines.first()).jsonObject
        .getValue("recordType").jsonPrimitive.content)
      assertEquals("{\"ok\":true}", lines.last())
      assertEquals(LogStorageResult.Success, engine.close())
    }
  }

  @Test
  fun recoveryHandlesLegalShortReadsWithoutDroppingCompleteRecords() = runTest {
    withTemporaryDirectories { logDirectory, exportDirectory ->
      val logPath = logDirectory.toPath()
      fileSystem.createDirectories(logPath)
      fileSystem.write(logPath / "cyxbs-log-v1-short-read-000001.active") {
        writeUtf8("{\"ok\":true}\npartial-tail")
      }
      val shortReadFileSystem = object : ForwardingFileSystem(fileSystem) {
        override fun openReadOnly(file: Path): FileHandle {
          val delegate = super.openReadOnly(file)
          return object : FileHandle(readWrite = false) {
            override fun protectedRead(
              fileOffset: Long,
              array: ByteArray,
              arrayOffset: Int,
              byteCount: Int,
            ): Int = delegate.read(fileOffset, array, arrayOffset, minOf(byteCount, 7))

            override fun protectedSize(): Long = delegate.size()
            override fun protectedClose() = delegate.close()
            override fun protectedWrite(
              fileOffset: Long,
              array: ByteArray,
              arrayOffset: Int,
              byteCount: Int,
            ) = error("只读句柄不支持写入")

            override fun protectedFlush() = Unit
            override fun protectedResize(size: Long) = error("只读句柄不支持调整大小")
          }
        }
      }

      val engine = FileLogEngine(config(logDirectory, exportDirectory), shortReadFileSystem)
      assertEquals(LogStorageResult.Success, engine.start())
      val export = assertIs<LogExportResult.Success>(engine.export())
      val lines = fileSystem.read(export.filePath.toPath()) { readUtf8() }
        .lineSequence()
        .filter { it.isNotBlank() }
        .toList()
      assertEquals(2, lines.size)
      assertEquals("{\"ok\":true}", lines.last())
      assertEquals(LogStorageResult.Success, engine.close())
    }
  }

  @Test
  fun recoveryScansLargeActiveFileTailWithoutKeepingPartialRecord() = runTest {
    withTemporaryDirectories { logDirectory, exportDirectory ->
      val logPath = logDirectory.toPath()
      fileSystem.createDirectories(logPath)
      val largeRecord = "{\"payload\":\"${"x".repeat(70 * 1_024)}\"}"
      fileSystem.write(logPath / "cyxbs-log-v1-large-000001.active") {
        writeUtf8(largeRecord)
        writeUtf8("\npartial-tail")
      }

      val engine = FileLogEngine(config(logDirectory, exportDirectory))
      assertEquals(LogStorageResult.Success, engine.start())
      val export = assertIs<LogExportResult.Success>(engine.export())
      val lines = fileSystem.read(export.filePath.toPath()) { readUtf8() }
        .lineSequence()
        .filter { it.isNotBlank() }
        .toList()
      assertEquals(2, lines.size)
      assertEquals(largeRecord, lines.last())
      assertEquals(LogStorageResult.Success, engine.close())
    }
  }

  @Test
  fun durableFlushIsExplicitlyUnsupportedAndCloseIsIdempotent() = runTest {
    withTemporaryDirectories { logDirectory, exportDirectory ->
      val engine = FileLogEngine(config(logDirectory, exportDirectory))
      assertEquals(LogStorageResult.Success, engine.start())
      assertEquals(LogFlushResult.Unsupported, engine.flush(LogFlushMode.Durable))
      assertEquals(LogStorageResult.Success, engine.close())
      assertEquals(LogStorageResult.Success, engine.close())

      engine.offer(event(message = "closed"))
      assertEquals(1, engine.health().droppedClosed)
    }
  }

  @Test
  fun cancelledCloseCanBeRetriedWithoutHanging() = runBlocking {
    withTemporaryDirectories { logDirectory, exportDirectory ->
      val engine = FileLogEngine(config(logDirectory, exportDirectory))
      assertEquals(LogStorageResult.Success, engine.start())
      repeat(500) { engine.offer(event(message = "queued-$it")) }

      val firstClose = launch { engine.close() }
      firstClose.cancelAndJoin()
      assertEquals(LogStorageResult.Success, withTimeout(5_000) { engine.close() })
      assertEquals(FileLogState.Stopped, engine.health().state)
    }
  }

  @Test
  fun startupReservesOneSlotForActiveFile() = runTest {
    withTemporaryDirectories { logDirectory, exportDirectory ->
      val logPath = logDirectory.toPath()
      fileSystem.createDirectories(logPath)
      repeat(8) { index ->
        fileSystem.write(logPath / "cyxbs-log-v1-old-${index.toString().padStart(6, '0')}.jsonl") {
          writeUtf8("{}\n")
        }
      }

      val engine = FileLogEngine(config(logDirectory, exportDirectory))
      assertEquals(LogStorageResult.Success, engine.start())
      val managed = fileSystem.list(logPath).filter { it.name.startsWith("cyxbs-log-v1-") }
      assertTrue(managed.size <= 8)
      assertEquals(LogStorageResult.Success, engine.close())
    }
  }

  @Test
  fun offerRechecksCurrentEngineMinimumLevel() = runTest {
    withTemporaryDirectories { logDirectory, exportDirectory ->
      val engine = FileLogEngine(
        config(logDirectory, exportDirectory).copy(minLevel = LogLevel.Error)
      )
      assertEquals(LogStorageResult.Success, engine.start())
      engine.offer(event(level = LogLevel.Debug, message = "filtered"))
      engine.offer(event(level = LogLevel.Error, message = "kept"))
      assertEquals(LogFlushResult.Buffered, engine.flush(LogFlushMode.Buffered))

      val export = assertIs<LogExportResult.Success>(engine.export())
      val messages = fileSystem.read(export.filePath.toPath()) { readUtf8() }
        .lineSequence()
        .filter { it.isNotBlank() }
        .map { Json.parseToJsonElement(it).jsonObject }
        .filter { it["recordType"]?.jsonPrimitive?.content == "log" }
        .map { it.getValue("message").jsonPrimitive.content }
        .toList()
      assertEquals(listOf("kept"), messages)
      assertEquals(LogStorageResult.Success, engine.close())
    }
  }

  @Test
  fun exportFailureRecoversActiveFileAndContinuesWriting() = runTest {
    withTemporaryDirectories { logDirectory, exportDirectory ->
      var failNextSeal = true
      val failingFileSystem = object : ForwardingFileSystem(fileSystem) {
        override fun atomicMove(source: Path, target: Path) {
          if (failNextSeal && source.name.endsWith(".active") && target.name.endsWith(".jsonl")) {
            failNextSeal = false
            throw IOException("injected seal failure")
          }
          super.atomicMove(source, target)
        }
      }
      val engine = FileLogEngine(config(logDirectory, exportDirectory), failingFileSystem)
      assertEquals(LogStorageResult.Success, engine.start())
      engine.offer(event(message = "before-failure"))
      assertIs<LogExportResult.Failure>(engine.export())

      engine.offer(event(message = "after-recovery"))
      assertEquals(LogFlushResult.Buffered, engine.flush(LogFlushMode.Buffered))
      val export = assertIs<LogExportResult.Success>(engine.export())
      val text = fileSystem.read(export.filePath.toPath()) { readUtf8() }
      assertTrue(text.contains("before-failure"))
      assertTrue(text.contains("after-recovery"))
      assertTrue(fileSystem.list(logDirectory.toPath()).count { it.name.endsWith(".active") } <= 1)
      assertEquals(LogStorageResult.Success, engine.close())
    }
  }

  @Test
  fun exportLimitsSourceFilesAndReportsTruncation() = runTest {
    withTemporaryDirectories { logDirectory, exportDirectory ->
      val logPath = logDirectory.toPath()
      fileSystem.createDirectories(logPath)
      repeat(4) { index ->
        fileSystem.write(logPath / "cyxbs-log-v1-old-${index.toString().padStart(6, '0')}.jsonl") {
          writeUtf8("{\"recordType\":\"log\",\"message\":\"$index\"}\n")
        }
      }

      val engine = FileLogEngine(config(logDirectory, exportDirectory))
      assertEquals(LogStorageResult.Success, engine.start())
      val export = assertIs<LogExportResult.Success>(engine.export())
      val lines = fileSystem.read(export.filePath.toPath()) { readUtf8() }
        .lineSequence()
        .filter { it.isNotBlank() }
        .toList()
      val manifest = Json.parseToJsonElement(lines.first()).jsonObject
      assertEquals("3", manifest.getValue("sourceFileCount").jsonPrimitive.content)
      assertEquals("true", manifest.getValue("truncated").jsonPrimitive.content)
      assertEquals(4, lines.size)
      assertEquals(LogStorageResult.Success, engine.close())
    }
  }

  @Test
  fun publicApiBoundsAndSanitizesOversizedUnicodeBeforeExport() = runTest {
    withTemporaryDirectories { logDirectory, exportDirectory ->
      CyxbsLog.configure(LogConfig(minLevel = LogLevel.Assert))
      CyxbsLog.setFileStorageCollectionEnabled(true)
      assertEquals(
        LogStorageResult.Success,
        CyxbsLog.startFileStorage(config(logDirectory, exportDirectory)),
      )
      try {
        CyxbsLog.i(tag = "超长入口") {
          "phone=13800138000 " + "中😀".repeat(30_000)
        }
        assertEquals(LogFlushResult.Buffered, CyxbsLog.flushFileStorage(LogFlushMode.Buffered))
        val export = assertIs<LogExportResult.Success>(CyxbsLog.exportFileStorage())
        val records = fileSystem.read(export.filePath.toPath()) { readUtf8() }
          .lineSequence()
          .filter { it.isNotBlank() }
          .map { Json.parseToJsonElement(it).jsonObject }
          .toList()
        val record = records.single { it.getValue("recordType").jsonPrimitive.content == "log" }

        assertEquals("true", record.getValue("truncated").jsonPrimitive.content)
        assertFalse(record.getValue("message").jsonPrimitive.content.contains("13800138000"))
      } finally {
        CyxbsLog.closeFileStorage()
        CyxbsLog.configure(LogConfig())
      }
    }
  }

  @Test
  fun collectionGateRejectsFileLogImmediately() = runTest {
    withTemporaryDirectories { logDirectory, exportDirectory ->
      CyxbsLog.configure(LogConfig(minLevel = LogLevel.Assert))
      CyxbsLog.setFileStorageCollectionEnabled(true)
      assertEquals(
        LogStorageResult.Success,
        CyxbsLog.startFileStorage(config(logDirectory, exportDirectory)),
      )
      try {
        CyxbsLog.setFileStorageCollectionEnabled(false)
        var evaluated = false
        CyxbsLog.e {
          evaluated = true
          "must-not-be-collected"
        }
        assertFalse(evaluated)
        assertEquals(0, CyxbsLog.fileStorageHealth().acceptedEntries)
      } finally {
        CyxbsLog.setFileStorageCollectionEnabled(true)
        CyxbsLog.closeFileStorage()
        CyxbsLog.configure(LogConfig())
      }
    }
  }

  @Test
  fun persistentWriteFailureEntersBackoffInsteadOfRetryingEveryRecord() = runBlocking {
    withTemporaryDirectories { logDirectory, exportDirectory ->
      val flushAttempts = AtomicInteger()
      val failingFileSystem = object : ForwardingFileSystem(fileSystem) {
        override fun appendingSink(file: Path, mustExist: Boolean): Sink {
          return object : ForwardingSink(super.appendingSink(file, mustExist)) {
            override fun flush() {
              flushAttempts.incrementAndGet()
              throw IOException("injected persistent flush failure")
            }
          }
        }
      }
      val engine = FileLogEngine(config(logDirectory, exportDirectory), failingFileSystem)
      assertEquals(LogStorageResult.Success, engine.start())

      repeat(64) { index ->
        engine.offer(event(message = "$index:${"x".repeat(4_096)}"))
      }
      withTimeout(5_000) {
        while (engine.health().queuedEntries > 0) delay(10)
      }

      val health = engine.health()
      assertTrue(health.writeFailures >= 1)
      assertTrue(health.droppedWriteBackoff > 0)
      assertTrue(flushAttempts.get() <= 3, "flush retried ${flushAttempts.get()} times before backoff")
      assertIs<LogStorageResult.Failure>(engine.close())
    }
  }

  @Test
  fun clockRollbackReplacesPreviousExportAndKeepsLatestParseable() = runTest {
    withTemporaryDirectories { logDirectory, exportDirectory ->
      var currentTimeMillis = 10_000L
      val engine = FileLogEngine(
        config(logDirectory, exportDirectory),
        nowMillis = { currentTimeMillis },
      )
      assertEquals(LogStorageResult.Success, engine.start())
      engine.offer(event(message = "before-rollback"))
      assertEquals(LogFlushResult.Buffered, engine.flush(LogFlushMode.Buffered))
      val first = assertIs<LogExportResult.Success>(engine.export())
      Files.setLastModifiedTime(
        first.filePath.toPath().toNioPath(),
        FileTime.fromMillis(20_000L),
      )

      currentTimeMillis = 1_000L
      engine.offer(event(message = "after-rollback"))
      assertEquals(LogFlushResult.Buffered, engine.flush(LogFlushMode.Buffered))
      val second = assertIs<LogExportResult.Success>(engine.export())

      assertTrue(first.filePath != second.filePath)
      assertFalse(fileSystem.exists(first.filePath.toPath()))
      fileSystem.read(second.filePath.toPath()) { readUtf8() }
        .lineSequence()
        .filter { it.isNotBlank() }
        .forEach { Json.parseToJsonElement(it).jsonObject }
      assertEquals(1, fileSystem.list(exportDirectory.toPath()).count { it.name.endsWith(".jsonl") })
      assertEquals(LogStorageResult.Success, engine.close())
    }
  }

  @Test
  fun concurrentProducersAndManagementCommandsCompleteWithoutHanging() = runBlocking {
    withTemporaryDirectories { logDirectory, exportDirectory ->
      val engine = FileLogEngine(config(logDirectory, exportDirectory))
      assertEquals(LogStorageResult.Success, engine.start())

      val producers = List(4) { producer ->
        launch(Dispatchers.Default) {
          repeat(500) { index -> engine.offer(event(message = "$producer-$index")) }
        }
      }
      val manager = launch(Dispatchers.Default) {
        repeat(12) { index ->
          when (index % 3) {
            0 -> engine.flush(LogFlushMode.Buffered)
            1 -> engine.export()
            else -> engine.clear()
          }
        }
      }
      withTimeout(10_000) { (producers + manager).joinAll() }
      assertEquals(LogStorageResult.Success, withTimeout(5_000) { engine.close() })
      assertEquals(FileLogState.Stopped, engine.health().state)
      assertEquals(0, engine.health().queuedEntries)
    }
  }

  @Test
  fun burstCallerLatencyRemainsNonBlockingOnDesktop() = runBlocking {
    withTemporaryDirectories { logDirectory, exportDirectory ->
      CyxbsLog.configure(LogConfig(minLevel = LogLevel.Assert))
      CyxbsLog.setFileStorageCollectionEnabled(true)
      assertEquals(
        LogStorageResult.Success,
        CyxbsLog.startFileStorage(config(logDirectory, exportDirectory)),
      )
      try {
        val payload = "x".repeat(128)
        repeat(1_000) { CyxbsLog.d(tag = "BurstBenchmark") { "warmup-$it-$payload" } }

        val samples = LongArray(20_000)
        val startedAt = System.nanoTime()
        samples.indices.forEach { index ->
          val callStartedAt = System.nanoTime()
          CyxbsLog.d(tag = "BurstBenchmark") { "burst-$index-$payload" }
          samples[index] = System.nanoTime() - callStartedAt
        }
        val elapsedNanos = System.nanoTime() - startedAt
        samples.sort()
        val p50 = samples[samples.size * 50 / 100]
        val p95 = samples[samples.size * 95 / 100]
        val p99 = samples[samples.size * 99 / 100]
        val callsPerSecond = 20_000L * 1_000_000_000L / elapsedNanos.coerceAtLeast(1L)
        println(
          "CYXBS_LOG_BURST desktop calls=20000 p50Ns=$p50 p95Ns=$p95 " +
            "p99Ns=$p99 callsPerSecond=$callsPerSecond"
        )
        assertTrue(p99 < 20_000_000L, "desktop burst p99 ${p99 / 1_000_000.0}ms indicates blocking")
      } finally {
        CyxbsLog.closeFileStorage()
        CyxbsLog.configure(LogConfig())
      }
    }
  }

  @Test
  fun encoderTruncatesOversizedUnicodeWithoutBreakingJson() {
    val encoded = encodeLogLine(
      event = event(message = "😀".repeat(2_000), stackTrace = "堆栈".repeat(2_000)),
      session = "test-session",
      sequence = 1,
      maxBytes = 1_024,
    )

    requireNotNull(encoded)
    assertTrue(encoded.truncated)
    assertTrue(encoded.bytes.size <= 1_024)
    val json = encoded.bytes.decodeToString().trimEnd()
    assertEquals("test-session", Json.parseToJsonElement(json).jsonObject.getValue("session").jsonPrimitive.content)
  }

  private fun config(logDirectory: String, exportDirectory: String) = FileLogConfig(
    directory = logDirectory,
    exportDirectory = exportDirectory,
  )

  private fun event(
    level: LogLevel = LogLevel.Info,
    message: String,
    stackTrace: String? = null,
  ) = FileLogEvent(
    timestampMillis = 123,
    level = level,
    tag = "FileLogEngineTest",
    message = message,
    stackTrace = stackTrace,
    snapshotTruncated = false,
  )

  private inline fun withTemporaryDirectories(block: (String, String) -> Unit) {
    val root = Files.createTempDirectory("cyxbs-log-test").toString().toPath()
    val logDirectory = root / "logs"
    val exportDirectory = root / "exports"
    try {
      block(logDirectory.toString(), exportDirectory.toString())
    } finally {
      fileSystem.deleteRecursively(root, mustExist = false)
    }
  }
}
