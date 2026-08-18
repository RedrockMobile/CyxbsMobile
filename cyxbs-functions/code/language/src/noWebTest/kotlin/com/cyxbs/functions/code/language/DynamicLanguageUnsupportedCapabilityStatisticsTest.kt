package com.cyxbs.functions.code.language

import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationDiagnostic
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationDiagnosticSeverity
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 不支持能力聚合、隐私边界和容量上限测试。 */
class DynamicLanguageUnsupportedCapabilityStatisticsTest {

  /** 同一次编译按 code 去重受影响次数，同时保留诊断实际出现次数。 */
  @Test
  fun aggregatesUnsupportedDiagnosticsWithoutSourceDetails() = runTest {
    val store = InMemoryDynamicLanguageUnsupportedCapabilityStatisticsStore()
    val statistics = DynamicLanguageUnsupportedCapabilityStatistics(store)
    val diagnostics = listOf(
      diagnostic("java.frontend.unsupported", "secret source name"),
      diagnostic("java.frontend.unsupported", "another secret"),
      diagnostic("java.semantic.undefined_name", "not a capability gap"),
    )

    repeat(2) {
      statistics.record(JAVA_LANGUAGE, "0.2.0", diagnostics)
    }

    val entry = statistics.snapshot().single()
    assertEquals(2, entry.affectedCompilationCount)
    assertEquals(4, entry.diagnosticOccurrenceCount)
    assertEquals("java.frontend.unsupported", entry.diagnosticCode)
    val persisted = Json.encodeToString(store.state)
    assertFalse("secret" in persisted)
    assertFalse("undefined_name" in persisted)
  }

  /** 包版本独立统计，清理单个语言不会误删其他语言。 */
  @Test
  fun separatesVersionsAndClearsOnlyRequestedLanguage() = runTest {
    val statistics = DynamicLanguageUnsupportedCapabilityStatistics(
      InMemoryDynamicLanguageUnsupportedCapabilityStatisticsStore(),
    )
    statistics.record(JAVA_LANGUAGE, "0.2.0", listOf(diagnostic("JAVA_BACKEND_UNSUPPORTED")))
    statistics.record(JAVA_LANGUAGE, "0.3.0", listOf(diagnostic("JAVA_BACKEND_UNSUPPORTED")))
    statistics.record(JS_LANGUAGE, "1.0.0", listOf(diagnostic("JAVASCRIPT_COMMONJS_UNSUPPORTED")))

    assertEquals(2, statistics.snapshot("java").size)
    statistics.clear("java")

    assertTrue(statistics.snapshot("java").isEmpty())
    assertEquals("javascript", statistics.snapshot().single().languageId)
  }

  /** 损坏语言包不能通过不断生成新 code 让本地统计无限增长。 */
  @Test
  fun limitsPersistentStatisticCardinality() = runTest {
    val statistics = DynamicLanguageUnsupportedCapabilityStatistics(
      InMemoryDynamicLanguageUnsupportedCapabilityStatisticsStore(),
    )
    statistics.record(
      JAVA_LANGUAGE,
      "0.2.0",
      List(300) { index -> diagnostic("java.unsupported.feature_$index") } +
        diagnostic("java.unsupported.${"x".repeat(200)}"),
    )

    assertEquals(256, statistics.snapshot().size)
    assertTrue(statistics.snapshot().none { it.diagnosticCode.length > 160 })
  }

  private fun diagnostic(code: String, message: String = "unsupported") =
    DynamicCompilationDiagnostic(
      code = code,
      message = message,
      severity = DynamicCompilationDiagnosticSeverity.ERROR,
    )

  private companion object {
    val JAVA_LANGUAGE = DynamicLanguageInfo(
      languageId = "java",
      displayName = "Java",
      npmPackageName = "@cyxbs-mobile/language-java",
      fileExtensions = listOf("java"),
    )
    val JS_LANGUAGE = DynamicLanguageInfo(
      languageId = "javascript",
      displayName = "JavaScript",
      npmPackageName = "@cyxbs-mobile/language-javascript",
      fileExtensions = listOf("js"),
    )
  }
}
