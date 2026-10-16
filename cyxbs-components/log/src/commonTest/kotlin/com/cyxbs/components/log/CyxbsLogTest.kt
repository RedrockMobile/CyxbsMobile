package com.cyxbs.components.log

import com.cyxbs.components.log.storage.sanitizeForFileLog

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CyxbsLogTest {
  @Test
  fun filteredMessageIsNotEvaluated() {
    CyxbsLog.configure(LogConfig(minLevel = LogLevel.Error))
    var evaluated = false

    CyxbsLog.d {
      evaluated = true
      "debug"
    }

    assertFalse(evaluated)
  }

  @Test
  fun messageFailureDoesNotEscapeLoggingBoundary() {
    CyxbsLog.configure(LogConfig(minLevel = LogLevel.Debug))

    CyxbsLog.d { error("message failed") }
  }

  @Test
  fun rawFileSnapshotUsesUtf8ByteLimitWithoutSplittingUnicode() {
    val chinese = "中".repeat(30_000).takeRawFileSnapshot()
    val emoji = "😀".repeat(20_000).takeRawFileSnapshot()

    assertTrue(chinese.truncated)
    assertTrue(emoji.truncated)
    assertTrue(chinese.value.encodeToByteArray().size <= 64 * 1_024)
    assertTrue(emoji.value.encodeToByteArray().size <= 64 * 1_024)
    assertFalse(emoji.value.last().isHighSurrogate())
  }

  @Test
  fun fileLogSanitizerRemovesCommonSecretsAndIdentifiers() {
    val sanitized = "Authorization: Bearer abc.def token=secret Cookie: sid=abc; user=42 " +
      "email=user@example.com studentId=2020123456 phone=13800138000 " +
      "deviceToken:abcDEF123 push_token=pushValue"

    val result = sanitized.sanitizeForFileLog()

    assertFalse(result.contains("abc.def"))
    assertFalse(result.contains("secret"))
    assertFalse(result.contains("sid=abc"))
    assertFalse(result.contains("user@example.com"))
    assertFalse(result.contains("2020123456"))
    assertFalse(result.contains("13800138000"))
    assertFalse(result.contains("abcDEF123"))
    assertFalse(result.contains("pushValue"))
    assertTrue(result.contains("[REDACTED]"))
  }
}
