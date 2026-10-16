package com.cyxbs.components.log

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import java.nio.file.Files
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
      assertEquals(2, lines.size)
      lines.forEach { Json.parseToJsonElement(it).jsonObject }
      assertEquals("课表同步\n完成 \"ok\"", lines.first().let {
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

      assertEquals(LogStorageResult.Success, engine.clear())
      val afterClear = fileSystem.list(logDirectory.toPath())
        .filter { it.name.endsWith(".jsonl") }
      assertTrue(afterClear.isEmpty())
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
      assertEquals("{\"ok\":true}\n", fileSystem.read(export.filePath.toPath()) { readUtf8() })
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
        .map { Json.parseToJsonElement(it).jsonObject.getValue("message").jsonPrimitive.content }
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
    level: LogLevel = LogLevel.Debug,
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
