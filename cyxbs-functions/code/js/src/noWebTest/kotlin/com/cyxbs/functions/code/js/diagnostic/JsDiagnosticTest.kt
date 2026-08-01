package com.cyxbs.functions.code.js.diagnostic

import com.cyxbs.functions.code.js.runtime.JsModuleLoader
import com.cyxbs.functions.code.js.runtime.QuickJsRuntime
import com.cyxbs.functions.code.js.runtime.QuickJsRuntimeConfig
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.QuickJsInterruptedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * JavaScript 异常到编辑器诊断模型的跨平台映射测试。
 */
class JsDiagnosticTest {

  /** 验证语法错误保留逻辑文件和源码位置，同时不会在消息中重复堆栈。 */
  @Test
  fun mapSyntaxErrorWithSourceLocation() = runTest {
    val runtime = QuickJsRuntime()
    try {
      val error = assertFailsWith<QuickJsException> {
        runtime.evaluate<Any?>(
          code = "fn test() {}",
          filename = "lesson/syntax.js",
        )
      }

      val diagnostic = error.toJsDiagnostic()

      assertEquals(JsDiagnosticKind.SYNTAX_ERROR, diagnostic.kind)
      assertContains(diagnostic.message, "SyntaxError")
      assertEquals("lesson/syntax.js", diagnostic.fileName)
      assertEquals(1, diagnostic.lineNumber)
      assertNotNull(diagnostic.stack)
      assertEquals(false, diagnostic.message.endsWith(diagnostic.stack))
    } finally {
      runtime.close()
    }
  }

  /** 验证普通 JavaScript 引用错误归入运行时错误。 */
  @Test
  fun mapRuntimeError() = runTest {
    val runtime = QuickJsRuntime()
    try {
      val error = assertFailsWith<QuickJsException> {
        runtime.evaluate<Any?>(
          code = "missingValue + 1",
          filename = "lesson/runtime.js",
        )
      }

      val diagnostic = error.toJsDiagnostic()

      assertEquals(JsDiagnosticKind.RUNTIME_ERROR, diagnostic.kind)
      assertContains(diagnostic.message, "ReferenceError")
      assertEquals("lesson/runtime.js", diagnostic.fileName)
      assertEquals(1, diagnostic.lineNumber)
    } finally {
      runtime.close()
    }
  }

  /** 验证 Module Loader 找不到静态依赖时给出独立分类。 */
  @Test
  fun mapMissingModuleError() = runTest {
    val runtime = QuickJsRuntime(
      moduleLoader = JsModuleLoader { null },
    )
    try {
      val error = assertFailsWith<QuickJsException> {
        runtime.evaluate<Any?>(
          code = "import { value } from 'missing'; globalThis.value = value;",
          filename = "lesson/main.js",
          asModule = true,
        )
      }

      val diagnostic = error.toJsDiagnostic()

      assertEquals(JsDiagnosticKind.MODULE_RESOLUTION_ERROR, diagnostic.kind)
      assertContains(diagnostic.message, "could not load module")
    } finally {
      runtime.close()
    }
  }

  /** 验证 Kotlin 宿主桥抛出的异常不会被误判为 JavaScript 语法或运行错误。 */
  @Test
  fun mapHostCapabilityError() = runTest {
    val runtime = QuickJsRuntime()
    try {
      runtime.bindFunction("failFromHost") {
        throw IllegalStateException("Host capability failed.")
      }

      val error = assertFailsWith<IllegalStateException> {
        runtime.evaluate<Any?>("failFromHost()")
      }
      val diagnostic = error.toJsDiagnostic()

      assertEquals(JsDiagnosticKind.HOST_ERROR, diagnostic.kind)
      assertEquals("Host capability failed.", diagnostic.message)
      assertNull(diagnostic.fileName)
      assertNull(diagnostic.stack)
    } finally {
      runtime.close()
    }
  }

  /** 验证无限循环被超时中断时使用统一中断分类。 */
  @Test
  fun mapEvaluationTimeoutAsInterrupted() = runTest {
    val runtime = QuickJsRuntime(
      config = QuickJsRuntimeConfig(evaluationTimeoutMillis = 20L),
    )
    try {
      val error = assertFailsWith<QuickJsInterruptedException> {
        runtime.evaluate<Any?>("while (true) {}")
      }

      val diagnostic = error.toJsDiagnostic()

      assertEquals(JsDiagnosticKind.INTERRUPTED, diagnostic.kind)
    } finally {
      runtime.close()
    }
  }

  /** 验证协程取消与 QuickJS 的超时、主动中断保持不同分类。 */
  @Test
  fun mapCoroutineCancellation() {
    val diagnostic = CancellationException("Lesson execution cancelled.").toJsDiagnostic()

    assertEquals(JsDiagnosticKind.CANCELLED, diagnostic.kind)
    assertEquals("Lesson execution cancelled.", diagnostic.message)
  }
}
