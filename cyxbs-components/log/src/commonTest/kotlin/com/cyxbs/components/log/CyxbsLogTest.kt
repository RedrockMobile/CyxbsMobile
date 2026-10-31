package com.cyxbs.components.log

import com.cyxbs.components.log.storage.sanitizeForFileLog

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CyxbsLogTest {
  @Test
  fun callSitePrefixKeepsOnlySourceFileAndLine() {
    assertEquals("Screen.kt:123", sourceLocationFromFrame("at load (/private/work/Screen.kt:123:9)"))
    assertEquals("app.js:45", sourceLocationFromFrame("load@https://example.com/assets/app.js:45:7"))
    assertEquals(null, sourceLocationFromFrame("native frame without debug information"))
    assertEquals("Screen.kt:123 -> message", messageWithCallSite("message", "Screen.kt:123"))
    assertEquals("Unknown:0 -> message", messageWithCallSite("message", null))
  }

  @Test
  fun callSitePrefixPreservesMessageBudgetAndSanitization() {
    val payload = prepareLogPayload(
      tag = "Test",
      message = "token=secret " + "x".repeat(70_000),
      throwable = null,
      callSite = "Screen.kt:123",
    )
    assertTrue(payload.message.startsWith("Screen.kt:123 -> token=[REDACTED]"))
    assertTrue(payload.message.length <= 16 * 1_024)
    assertTrue(payload.truncated)
    assertFalse(payload.message.contains("secret"))
  }

  @Test
  fun disabledLoggingDoesNotRenderThrowable() {
    var rendered = false
    val throwable = object : Throwable() {
      override val message: String get() {
        rendered = true
        return "detail"
      }
    }
    CyxbsLog.configure(LogConfig(enabled = false))
    try {
      CyxbsLog.e(message = "disabled", throwable = throwable)
      assertFalse(rendered)
    } finally {
      CyxbsLog.configure(LogConfig())
    }
  }

  @Test
  fun throwableMessageFailureDoesNotEscapeLoggingBoundary() {
    CyxbsLog.configure(LogConfig(minLevel = LogLevel.Debug))
    try {
      CyxbsLog.e(
        throwable = object : Throwable() {
          override val message: String get() = error("render failed")
        },
        message = "operation failed",
      )
    } finally {
      CyxbsLog.configure(LogConfig())
    }
  }

  @Test
  fun throwableTreeTraversalAndMessageRenderingAreBounded() {
    var visited = 0
    fun counted(cause: Throwable? = null) = object : Throwable(null, cause) {
      override val message: String get() {
        visited += 1
        return "detail"
      }
    }
    var cause: Throwable? = null
    repeat(100) { cause = counted(cause) }
    val root = counted(cause)
    repeat(100) { root.addSuppressed(counted()) }
    val tree = root.boundedThrowableSnapshot()
    assertTrue(tree.truncated)
    assertTrue(visited <= 8)

    val huge = object : Throwable() {
      override val message: String = "😀".repeat(100_000)
      override val cause: Throwable get() = error("must stop before reading cause")
    }.boundedThrowableSnapshot()
    assertTrue(huge.truncated)
    assertTrue(huge.value.length <= 16 * 1_024)
    assertFalse(huge.value.last().isHighSurrogate())
  }

  @Test
  fun throwableCauseCycleStopsWithoutExpandingForever() {
    val cyclic = object : Throwable("cycle") {
      override val cause: Throwable get() = this
    }.boundedThrowableSnapshot()
    assertTrue(cyclic.truncated)
    assertTrue(cyclic.value.contains("circular exception"))
    assertEquals(1, Regex("cycle").findAll(cyclic.value).count())
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
  fun preparedPayloadSanitizesMessageTagAndThrowableBeforeAnySink() {
    val payload = prepareLogPayload(
      tag = "Authorization: Bearer tag-secret",
      message = "token=message-secret ${"界".repeat(30_000)}",
      throwable = IllegalStateException("password=throwable-secret"),
    )

    assertFalse(payload.tag.contains("tag-secret"))
    assertFalse(payload.message.contains("message-secret"))
    assertFalse(payload.stackTrace.orEmpty().contains("throwable-secret"))
    assertTrue(payload.tag.contains("[REDACTED]"))
    assertTrue(payload.message.contains("[REDACTED]"))
    assertTrue(payload.stackTrace.orEmpty().contains("[REDACTED]"))
    assertTrue(payload.truncated)
    assertTrue(payload.message.length <= 16 * 1_024)
  }

  @Test
  fun boundedThrowableSummaryDoesNotExpandStackOrCauseChain() {
    val payload = prepareLogPayload(
      tag = "CrashMonitor",
      message = "uncaught",
      throwable = IllegalStateException("detail " + "x".repeat(70_000), RuntimeException("cause-secret")),
      throwableRendering = ThrowableRendering.Summary,
    )

    assertTrue(payload.stackTrace.orEmpty().contains("IllegalStateException"))
    assertFalse(payload.stackTrace.orEmpty().contains("cause-secret"))
    assertFalse(payload.stackTrace.orEmpty().contains("\n\tat "))
    assertTrue(payload.stackTrace.orEmpty().encodeToByteArray().size <= 16 * 1_024)
    assertTrue(payload.truncated)
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
