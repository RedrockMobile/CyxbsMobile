package com.cyxbs.components.utils.extensions

import com.cyxbs.components.log.CyxbsLog
import com.cyxbs.components.log.FileLogConfig
import com.cyxbs.components.log.LogConfig
import com.cyxbs.components.log.LogExportResult
import com.cyxbs.components.log.LogLevel
import com.cyxbs.components.log.LogStorageResult
import java.nio.file.Files
import java.nio.file.Paths
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LogExtensionsTest {
  @Test
  fun shortcutsPreserveTagLevelMessageAndBusinessCallSite() = runBlocking {
    val root = Files.createTempDirectory("cyxbs-log-extensions-test")
    CyxbsLog.configure(LogConfig(minLevel = LogLevel.Assert, defaultTag = "OtherDefault"))
    CyxbsLog.setFileStorageCollectionEnabled(true)
    try {
      assertEquals(
        LogStorageResult.Success,
        CyxbsLog.startFileStorage(
          FileLogConfig(root.resolve("logs").toString(), root.resolve("exports").toString(), LogLevel.Debug)
        ),
      )
      val expected = mutableListOf<Triple<String, String, String>>()
      fun expectedNextMessage(message: String): String =
        "LogExtensionsTest.kt:${Throwable().stackTrace[1].lineNumber + 1} -> $message"
      expected += Triple("Debug", "ggg", expectedNextMessage("null"))
      logg(null)
      expected += Triple("Debug", "ggg", expectedNextMessage("17"))
      logg(17)
      expected += Triple("Debug", "ggg", expectedNextMessage("default-tag"))
      log("default-tag")
      expected += Triple("Debug", "CustomTag", expectedNextMessage("custom-tag"))
      log("CustomTag", "custom-tag")
      expected += Triple("Warn", "SharedChain", expectedNextMessage("retry"))
      log(LogLevel.Warn, "SharedChain", "retry")

      val exported = assertIs<LogExportResult.Success>(CyxbsLog.exportFileStorage())
      val records = Files.readAllLines(Paths.get(exported.filePath)).drop(1)
      assertEquals(expected.size, records.size)
      records.zip(expected).forEach { (record, fields) ->
        val (level, tag, message) = fields
        assertTrue(record.contains("\"level\":\"$level\""), record)
        assertTrue(record.contains("\"tag\":\"$tag\""), record)
        assertTrue(record.contains("\"message\":\"$message\""), record)
      }
    } finally {
      CyxbsLog.closeFileStorage()
      CyxbsLog.configure(LogConfig())
      root.toFile().deleteRecursively()
    }
  }
}
