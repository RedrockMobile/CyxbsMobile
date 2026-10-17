package com.cyxbs.components.log.storage

import com.cyxbs.components.log.CyxbsLog
import com.cyxbs.components.log.FileLogConfig
import com.cyxbs.components.log.FileLogState
import com.cyxbs.components.log.LogConfig
import com.cyxbs.components.log.LogContext
import com.cyxbs.components.log.LogExportResult
import com.cyxbs.components.log.LogFlushMode
import com.cyxbs.components.log.LogFlushResult
import com.cyxbs.components.log.LogHttpMethod
import com.cyxbs.components.log.LogLevel
import com.cyxbs.components.log.LogResult
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
import okio.Buffer
import okio.FileHandle
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.ForwardingSink
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.Sink
import okio.ByteString.Companion.encodeUtf8
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FileLogEngineTest {
  private val fileSystem = FileSystem.SYSTEM

  @Test
  fun overriddenThrowableRenderersAreNotInvoked() = runBlocking {
    withTemporaryDirectories { logDirectory, exportDirectory ->
      CyxbsLog.configure(LogConfig(minLevel = LogLevel.Assert))
      assertEquals(LogStorageResult.Success, CyxbsLog.startFileStorage(config(logDirectory, exportDirectory)))
      try {
        val error = object : Throwable("safe-detail") {
          override fun toString(): String = error("toString must not be called")
          override fun printStackTrace(writer: java.io.PrintWriter) = error("printStackTrace must not be called")
        }
        CyxbsLog.e(throwable = error) { "operation-failed" }
        val exported = assertIs<LogExportResult.Success>(CyxbsLog.exportFileStorage())
        val contents = fileSystem.read(exported.filePath.toPath()) { readUtf8() }
        assertTrue(contents.contains("operation-failed"))
        assertTrue(contents.contains("safe-detail"))
        assertEquals(1, exported.exportedRecordCount)
      } finally {
        CyxbsLog.closeFileStorage()
        CyxbsLog.configure(LogConfig())
      }
    }
  }

  @Test
  fun inFlightLogCannotCrossCollectionDisable() = assertInFlightLogRejected {
    CyxbsLog.setFileStorageCollectionEnabled(false)
  }

  @Test
  fun inFlightLogCannotCrossCollectionDisableAndReenable() = assertInFlightLogRejected {
    CyxbsLog.setFileStorageCollectionEnabled(false)
    CyxbsLog.setFileStorageCollectionEnabled(true)
  }

  @Test
  fun inFlightLogCannotReappearAfterClear() = assertInFlightLogRejected {
    assertEquals(LogStorageResult.Success, CyxbsLog.clearFileStorage())
  }

  @Test
  fun inFlightLogCannotEnterReplacementEngine() = assertInFlightLogRejected { configuration ->
    assertEquals(LogStorageResult.Success, CyxbsLog.closeFileStorage())
    assertEquals(LogStorageResult.Success, CyxbsLog.startFileStorage(configuration))
  }

  private fun assertInFlightLogRejected(barrier: suspend (FileLogConfig) -> Unit) = runBlocking {
    withTemporaryDirectories { logDirectory, exportDirectory ->
      val configuration = config(logDirectory, exportDirectory)
      val entered = CountDownLatch(1)
      val resume = CountDownLatch(1)
      CyxbsLog.configure(LogConfig(minLevel = LogLevel.Assert))
      CyxbsLog.setFileStorageCollectionEnabled(true)
      assertEquals(LogStorageResult.Success, CyxbsLog.startFileStorage(configuration))
      val producer = launch(Dispatchers.Default) {
        CyxbsLog.i {
          entered.countDown()
          check(resume.await(5, TimeUnit.SECONDS))
          "stale-in-flight-event"
        }
      }
      try {
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        barrier(configuration)
        resume.countDown()
        withTimeout(5_000) { producer.join() }
        CyxbsLog.setFileStorageCollectionEnabled(true)
        CyxbsLog.i { "fresh-event" }
        val exported = assertIs<LogExportResult.Success>(CyxbsLog.exportFileStorage())
        val contents = fileSystem.read(exported.filePath.toPath()) { readUtf8() }
        assertFalse(contents.contains("stale-in-flight-event"))
        assertTrue(contents.contains("fresh-event"))
      } finally {
        resume.countDown()
        producer.join()
        CyxbsLog.closeFileStorage()
        CyxbsLog.setFileStorageCollectionEnabled(true)
        CyxbsLog.configure(LogConfig())
      }
    }
  }

  @Test
  fun writesParseableJsonlAndExportsOnlySealedFiles() = runTest {
    withTemporaryDirectories { logDirectory, exportDirectory ->
      val engine = FileLogEngine(config(logDirectory, exportDirectory))
      assertEquals(LogStorageResult.Success, engine.start())

      engine.offer(event(message = "课表同步\n完成 \"ok\""))
      engine.offer(event(level = LogLevel.Error, message = "解析失败", stackTrace = "line1\nline2"))
      assertEquals(LogFlushResult.Buffered, engine.flush(LogFlushMode.Buffered))
      val healthy = engine.health()
      assertTrue(healthy.lastSuccessfulWriteAtMillis != null)
      assertTrue(healthy.lastFlushDurationMillis != null)
      assertTrue(healthy.managedFileCount >= 1)
      assertTrue(healthy.managedBytes > 0)

      val export = assertIs<LogExportResult.Success>(engine.export())
      val exportedText = fileSystem.read(export.filePath.toPath()) { readUtf8() }
      val payload = exportedText.substringAfter('\n')
      val lines = exportedText
        .lineSequence()
        .filter { it.isNotBlank() }
        .toList()
      assertEquals(3, lines.size)
      lines.forEach { Json.parseToJsonElement(it).jsonObject }
      val manifest = Json.parseToJsonElement(lines.first()).jsonObject
      assertEquals("manifest", manifest.getValue("recordType").jsonPrimitive.content)
      assertEquals("2", manifest.getValue("schemaVersion").jsonPrimitive.content)
      assertEquals("1", manifest.getValue("sourceFileCount").jsonPrimitive.content)
      assertEquals("2", manifest.getValue("exportedRecordCount").jsonPrimitive.content)
      assertEquals(payload.encodeUtf8().sha256().hex(), manifest.getValue("payloadSha256").jsonPrimitive.content)
      assertEquals(2, export.exportedRecordCount)
      assertEquals(payload.encodeUtf8().sha256().hex(), export.payloadSha256)
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
  fun emptyExportHasVerifiableEmptyPayloadIntegrity() = runTest {
    withTemporaryDirectories { logDirectory, exportDirectory ->
      val engine = FileLogEngine(config(logDirectory, exportDirectory))
      assertEquals(LogStorageResult.Success, engine.start())

      val export = assertIs<LogExportResult.Success>(engine.export())
      val exportedText = fileSystem.read(export.filePath.toPath()) { readUtf8() }
      val manifest = Json.parseToJsonElement(exportedText.trimEnd()).jsonObject
      val emptyHash = "".encodeUtf8().sha256().hex()

      assertEquals("0", manifest.getValue("sourceFileCount").jsonPrimitive.content)
      assertEquals("0", manifest.getValue("exportedLogBytes").jsonPrimitive.content)
      assertEquals("0", manifest.getValue("exportedRecordCount").jsonPrimitive.content)
      assertEquals(emptyHash, manifest.getValue("payloadSha256").jsonPrimitive.content)
      assertEquals(0, export.exportedRecordCount)
      assertEquals(emptyHash, export.payloadSha256)
      assertFalse(export.truncated)
      assertEquals(LogStorageResult.Success, engine.close())
    }
  }

  @Test
  fun payloadIntegritySpansMultipleDigestChunks() = runTest {
    withTemporaryDirectories { logDirectory, exportDirectory ->
      val engine = FileLogEngine(config(logDirectory, exportDirectory))
      assertEquals(LogStorageResult.Success, engine.start())
      repeat(10) { index -> engine.offer(event(message = "$index:${"x".repeat(8_000)}")) }
      assertEquals(LogFlushResult.Buffered, engine.flush(LogFlushMode.Buffered))

      val export = assertIs<LogExportResult.Success>(engine.export())
      val exportedText = fileSystem.read(export.filePath.toPath()) { readUtf8() }
      val payload = exportedText.substringAfter('\n')
      val manifest = Json.parseToJsonElement(exportedText.substringBefore('\n')).jsonObject

      assertTrue(payload.encodeToByteArray().size > 64 * 1_024)
      assertEquals("10", manifest.getValue("exportedRecordCount").jsonPrimitive.content)
      assertEquals(payload.encodeUtf8().sha256().hex(), manifest.getValue("payloadSha256").jsonPrimitive.content)
      assertEquals(10, export.exportedRecordCount)
      assertEquals(LogStorageResult.Success, engine.close())
    }
  }

  @Test
  fun generationOrderingIgnoresReversedModificationTimes() = runTest {
    withTemporaryDirectories { logDirectory, exportDirectory ->
      val logPath = logDirectory.toPath()
      fileSystem.createDirectories(logPath)
      val currentTimeMillis = System.currentTimeMillis()
      repeat(4) { offset ->
        val generation = offset + 1
        val path = logPath /
          "cyxbs-log-v2-${generation.toString().padStart(20, '0')}-seed-000001.jsonl"
        fileSystem.write(path) {
          writeUtf8("{\"recordType\":\"log\",\"message\":\"generation-$generation\"}\n")
        }
        Files.setLastModifiedTime(
          path.toNioPath(),
          FileTime.fromMillis(currentTimeMillis + (5 - generation) * 1_000L),
        )
      }

      val engine = FileLogEngine(config(logDirectory, exportDirectory))
      assertEquals(LogStorageResult.Success, engine.start())
      val export = assertIs<LogExportResult.Success>(engine.export())
      val messages = fileSystem.read(export.filePath.toPath()) { readUtf8() }
        .lineSequence()
        .filter { it.isNotBlank() }
        .map { Json.parseToJsonElement(it).jsonObject }
        .filter { it["recordType"]?.jsonPrimitive?.content == "log" }
        .map { it.getValue("message").jsonPrimitive.content }
        .toList()

      assertEquals(listOf("generation-2", "generation-3", "generation-4"), messages)
      assertEquals(3, export.exportedRecordCount)
      assertTrue(export.truncated)
      assertEquals(LogStorageResult.Success, engine.close())
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
        .filter { it.name.startsWith("cyxbs-log-v1-") || it.name.startsWith("cyxbs-log-v2-") }
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
      assertTrue(fileSystem.list(exportDirectory.toPath()).none {
        it.name.startsWith("cyxbs-log-export-v1-") || it.name.startsWith("cyxbs-log-export-v2-")
      })
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
      val managed = fileSystem.list(logPath).filter {
        it.name.startsWith("cyxbs-log-v1-") || it.name.startsWith("cyxbs-log-v2-")
      }
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
  fun exportFailureRecoversActiveFileAndContinuesWriting() = runBlocking {
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

      delay(300)
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
  fun publicApiWritesSanitizedStructuredSchemaV2Context() = runTest {
    withTemporaryDirectories { logDirectory, exportDirectory ->
      CyxbsLog.configure(LogConfig(minLevel = LogLevel.Assert))
      CyxbsLog.setFileStorageCollectionEnabled(true)
      assertEquals(
        LogStorageResult.Success,
        CyxbsLog.startFileStorage(config(logDirectory, exportDirectory)),
      )
      try {
        CyxbsLog.log(
          level = LogLevel.Info,
          tag = "StructuredTest",
          context = LogContext(
            operation = "sync token=operation-secret",
            route = "/courses/{id}?studentId=2020123456#private",
            method = LogHttpMethod.Get,
            statusCode = 200,
            durationMillis = 15,
            result = LogResult.Success,
            retryCount = 2,
            fromState = "Loading",
            toState = "Ready",
            correlationId = "request-12:attempt_2",
            eventTimestampMillis = 42,
          ),
        ) { "structured" }
        CyxbsLog.log(
          level = LogLevel.Info,
          tag = "StructuredTest",
          context = LogContext(correlationId = "13800138000"),
        ) { "sensitive-correlation" }
        CyxbsLog.log(
          level = LogLevel.Info,
          tag = "StructuredTest",
          context = LogContext(route = "/" + "r".repeat(70_000) + "?token=route-secret"),
        ) { "bounded-route" }
        assertEquals(LogFlushResult.Buffered, CyxbsLog.flushFileStorage(LogFlushMode.Buffered))
        val export = assertIs<LogExportResult.Success>(CyxbsLog.exportFileStorage())
        val records = fileSystem.read(export.filePath.toPath()) { readUtf8() }
          .lineSequence()
          .filter { it.isNotBlank() }
          .map { Json.parseToJsonElement(it).jsonObject }
          .filter { it.getValue("recordType").jsonPrimitive.content == "log" }
          .toList()
        val record = records.single { it.getValue("message").jsonPrimitive.content == "structured" }
        val sensitiveCorrelation = records.single {
          it.getValue("message").jsonPrimitive.content == "sensitive-correlation"
        }
        val boundedRoute = records.single {
          it.getValue("message").jsonPrimitive.content == "bounded-route"
        }

        assertEquals("2", record.getValue("schemaVersion").jsonPrimitive.content)
        assertEquals("42", record.getValue("eventTimestampMillis").jsonPrimitive.content)
        assertTrue(record.getValue("observedTimestampMillis").jsonPrimitive.content.toLong() > 42)
        assertEquals("/courses/{id}", record.getValue("route").jsonPrimitive.content)
        assertEquals("Get", record.getValue("method").jsonPrimitive.content)
        assertEquals("200", record.getValue("statusCode").jsonPrimitive.content)
        assertEquals("15", record.getValue("durationMillis").jsonPrimitive.content)
        assertEquals("Success", record.getValue("result").jsonPrimitive.content)
        assertEquals("2", record.getValue("retryCount").jsonPrimitive.content)
        assertEquals("Loading", record.getValue("fromState").jsonPrimitive.content)
        assertEquals("Ready", record.getValue("toState").jsonPrimitive.content)
        assertEquals("request-12:attempt_2", record.getValue("correlationId").jsonPrimitive.content)
        assertFalse(sensitiveCorrelation.containsKey("correlationId"))
        assertEquals(256, boundedRoute.getValue("route").jsonPrimitive.content.length)
        assertTrue(boundedRoute.getValue("truncated").jsonPrimitive.content.toBoolean())
        assertFalse(boundedRoute.toString().contains("route-secret"))
        assertFalse(record.getValue("operation").jsonPrimitive.content.contains("operation-secret"))
        assertFalse(record.containsKey("timestampMillis"))
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
  fun infoFloodLeavesQueueCapacityForWarnAndAbove() = runBlocking {
    withTemporaryDirectories { logDirectory, exportDirectory ->
      val blockNextFlush = AtomicBoolean(false)
      val flushEntered = CountDownLatch(1)
      val releaseFlush = CountDownLatch(1)
      val blockingFileSystem = object : ForwardingFileSystem(fileSystem) {
        override fun appendingSink(file: Path, mustExist: Boolean): Sink {
          return object : ForwardingSink(super.appendingSink(file, mustExist)) {
            override fun flush() {
              if (blockNextFlush.compareAndSet(true, false)) {
                flushEntered.countDown()
                check(releaseFlush.await(5, TimeUnit.SECONDS)) { "timed out waiting to release flush" }
              }
              super.flush()
            }
          }
        }
      }
      val engine = FileLogEngine(config(logDirectory, exportDirectory), blockingFileSystem)
      assertEquals(LogStorageResult.Success, engine.start())
      engine.offer(event(message = "flush-trigger"))
      blockNextFlush.set(true)
      val blockedFlush = launch(Dispatchers.Default) { engine.flush(LogFlushMode.Buffered) }
      assertTrue(flushEntered.await(5, TimeUnit.SECONDS))

      try {
        repeat(819) { index -> engine.offer(event(message = "info-$index")) }
        engine.offer(event(message = "info-rejected"))
        repeat(205) { index ->
          engine.offer(event(level = LogLevel.Error, message = "protected-$index"))
        }
        engine.offer(event(level = LogLevel.Error, message = "protected-rejected"))

        val health = engine.health()
        assertEquals(1_024, health.queuedEntries)
        assertEquals(2, health.droppedQueueFull)
        assertEquals(1, health.droppedByLevel[LogLevel.Info])
        assertEquals(1, health.droppedByLevel[LogLevel.Error])
      } finally {
        releaseFlush.countDown()
      }

      withTimeout(5_000) { blockedFlush.join() }
      assertEquals(LogFlushResult.Buffered, engine.flush(LogFlushMode.Buffered))
      val export = assertIs<LogExportResult.Success>(engine.export())
      val messages = fileSystem.read(export.filePath.toPath()) { readUtf8() }
      val manifest = Json.parseToJsonElement(messages.substringBefore('\n')).jsonObject
      val health = manifest.getValue("health").jsonObject
      assertEquals("currentEngineSession", health.getValue("scope").jsonPrimitive.content)
      assertEquals("2", health.getValue("droppedQueueFull").jsonPrimitive.content)
      assertEquals("1", health.getValue("droppedByLevel").jsonObject.getValue("Info").jsonPrimitive.content)
      assertEquals("1", health.getValue("droppedByLevel").jsonObject.getValue("Error").jsonPrimitive.content)
      assertEquals("Info", manifest.getValue("collectionPolicy").jsonObject.getValue("minLevel").jsonPrimitive.content)
      repeat(205) { index -> assertTrue(messages.contains("protected-$index")) }
      assertFalse(messages.contains("protected-rejected"))
      assertEquals(LogStorageResult.Success, engine.close())
    }
  }

  @Test
  fun infoByteFloodLeavesByteCapacityForWarnAndAbove() = runBlocking {
    withTemporaryDirectories { logDirectory, exportDirectory ->
      val blockNextFlush = AtomicBoolean(false)
      val flushEntered = CountDownLatch(1)
      val releaseFlush = CountDownLatch(1)
      val blockingFileSystem = object : ForwardingFileSystem(fileSystem) {
        override fun appendingSink(file: Path, mustExist: Boolean): Sink {
          return object : ForwardingSink(super.appendingSink(file, mustExist)) {
            override fun flush() {
              if (blockNextFlush.compareAndSet(true, false)) {
                flushEntered.countDown()
                check(releaseFlush.await(5, TimeUnit.SECONDS)) { "timed out waiting to release flush" }
              }
              super.flush()
            }
          }
        }
      }
      val engine = FileLogEngine(config(logDirectory, exportDirectory), blockingFileSystem)
      assertEquals(LogStorageResult.Success, engine.start())
      engine.offer(event(message = "flush-trigger"))
      blockNextFlush.set(true)
      val blockedFlush = launch(Dispatchers.Default) { engine.flush(LogFlushMode.Buffered) }
      assertTrue(flushEntered.await(5, TimeUnit.SECONDS))

      try {
        val structuredContext = PreparedLogContext(
          operation = "o".repeat(128),
          route = "/" + "r".repeat(255),
          method = "Post",
          statusCode = 200,
          durationMillis = 1,
          result = "Success",
          retryCount = 1,
          fromState = "f".repeat(128),
          toState = "t".repeat(128),
          correlationId = "c".repeat(64),
        )
        val info = "i".repeat(1_400)
        repeat(819) { index -> engine.offer(event(message = "$index:$info", context = structuredContext)) }
        engine.offer(event(message = "info-byte-rejected:$info", context = structuredContext))
        engine.offer(event(level = LogLevel.Error, message = "protected-byte-budget"))

        val health = engine.health()
        assertTrue(health.queuedEntries < 819)
        assertTrue(health.droppedByteBudget > 0)
        assertEquals(health.droppedByteBudget, health.droppedByLevel[LogLevel.Info])
        assertEquals(0, health.droppedByLevel[LogLevel.Error])
      } finally {
        releaseFlush.countDown()
      }

      withTimeout(5_000) { blockedFlush.join() }
      assertEquals(LogFlushResult.Buffered, engine.flush(LogFlushMode.Buffered))
      val export = assertIs<LogExportResult.Success>(engine.export())
      val messages = fileSystem.read(export.filePath.toPath()) { readUtf8() }
      assertTrue(messages.contains("protected-byte-budget"))
      assertFalse(messages.contains("info-byte-rejected"))
      assertEquals(LogStorageResult.Success, engine.close())
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
      assertTrue(health.lastWriteFailureAtMillis != null)
      assertEquals("IOException", health.lastWriteFailureType)
      assertTrue(health.consecutiveWriteFailures >= 1)
      assertTrue(flushAttempts.get() <= 3, "flush retried ${flushAttempts.get()} times before backoff")
      assertIs<LogStorageResult.Failure>(engine.close())
    }
  }

  @Test
  fun successfulRecoveryDoesNotHideWriteFailureOrSkipBackoff() = runBlocking {
    withTemporaryDirectories { logDirectory, exportDirectory ->
      val failNextNonEmptyFlush = AtomicBoolean(true)
      val recoveringFileSystem = object : ForwardingFileSystem(fileSystem) {
        override fun appendingSink(file: Path, mustExist: Boolean): Sink {
          return object : ForwardingSink(super.appendingSink(file, mustExist)) {
            private var receivedPayload = false

            override fun write(source: Buffer, byteCount: Long) {
              receivedPayload = true
              super.write(source, byteCount)
            }

            override fun flush() {
              if (receivedPayload && failNextNonEmptyFlush.compareAndSet(true, false)) {
                throw IOException("injected one-shot non-empty flush failure")
              }
              super.flush()
            }
          }
        }
      }
      val engine = FileLogEngine(config(logDirectory, exportDirectory), recoveringFileSystem)
      assertEquals(LogStorageResult.Success, engine.start())
      engine.offer(event(message = "x".repeat(12 * 1_024)))

      assertIs<LogFlushResult.Failure>(engine.flush(LogFlushMode.Buffered))
      assertEquals(1, engine.health().consecutiveWriteFailures)

      engine.offer(event(message = "backoff-drop"))
      withTimeout(5_000) {
        while (engine.health().queuedEntries > 0) delay(10)
      }
      assertTrue(engine.health().droppedWriteBackoff >= 1)

      delay(300)
      engine.offer(event(message = "recovered-write"))
      assertEquals(LogFlushResult.Buffered, engine.flush(LogFlushMode.Buffered))
      assertEquals(0, engine.health().consecutiveWriteFailures)
      val export = assertIs<LogExportResult.Success>(engine.export())
      val manifest = fileSystem.read(export.filePath.toPath()) { readUtf8LineStrict() }
        .let { Json.parseToJsonElement(it).jsonObject }
      val exportedHealth = manifest.getValue("health").jsonObject
      assertTrue(exportedHealth.getValue("writeFailures").jsonPrimitive.content.toLong() >= 1)
      assertTrue(exportedHealth.getValue("droppedWriteBackoff").jsonPrimitive.content.toLong() >= 1)
      assertEquals("0", exportedHealth.getValue("consecutiveWriteFailures").jsonPrimitive.content)
      assertEquals(LogStorageResult.Success, engine.close())
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
        repeat(1_000) { CyxbsLog.i(tag = "BurstBenchmark") { "warmup-$it-$payload" } }

        val samples = LongArray(20_000)
        val startedAt = System.nanoTime()
        samples.indices.forEach { index ->
          val callStartedAt = System.nanoTime()
          CyxbsLog.i(tag = "BurstBenchmark") { "burst-$index-$payload" }
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
    context: PreparedLogContext? = null,
  ) = FileLogEvent(
    eventTimestampMillis = 123,
    observedTimestampMillis = 456,
    level = level,
    tag = "FileLogEngineTest",
    message = message,
    stackTrace = stackTrace,
    context = context,
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
