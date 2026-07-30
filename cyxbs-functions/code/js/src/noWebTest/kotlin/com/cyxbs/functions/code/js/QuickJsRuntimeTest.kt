package com.cyxbs.functions.code.js

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * 跨 Android 设备、iOS 与 Desktop 复用的 QuickJS 基础能力测试。
 */
class QuickJsRuntimeTest {

  /**
   * 验证基础表达式执行和 Kotlin 数值转换。
   */
  @Test
  fun evaluateBasicExpression() = runTest {
    val runtime = QuickJsRuntime()
    try {
      assertEquals(42, runtime.evaluate<Int>("40 + 2"))
    } finally {
      runtime.close()
    }
  }

  /**
   * 验证源码可先编译为字节码，再由同一 QuickJS 版本执行。
   */
  @Test
  fun compileAndEvaluateBytecode() = runTest {
    val runtime = QuickJsRuntime()
    try {
      val bytecode = runtime.compile(
        code = "21 * 2",
        filename = "compiled.js",
      )
      assertEquals(42, runtime.evaluate<Int>(bytecode))
    } finally {
      runtime.close()
    }
  }

  /**
   * 验证 Promise Pending Job 会被持续调度，并可通过顶层 await 把最终结果返回 Kotlin。
   */
  @Test
  fun awaitPromiseResult() = runTest {
    val runtime = QuickJsRuntime()
    try {
      val result = runtime.evaluate<Int>(
        "await Promise.resolve(40).then(value => value + 2)",
      )
      assertEquals(42, result)
    } finally {
      runtime.close()
    }
  }

  /**
   * 验证 Kotlin suspend 宿主函数会以 Promise 形式暴露给 JavaScript。
   */
  @Test
  fun invokeSuspendHostFunction() = runTest {
    val runtime = QuickJsRuntime()
    try {
      runtime.bindAsyncFunction("doubleAsync") { args ->
        delay(1.milliseconds)
        (args.single() as Number).toInt() * 2
      }

      assertEquals(42, runtime.evaluate<Int>("await doubleAsync(21)"))
    } finally {
      runtime.close()
    }
  }

  /**
   * 验证多个 ES Module 可以注册，并由入口模块通过标准 import 调用。
   */
  @Test
  fun loadEsModule() = runTest {
    val runtime = QuickJsRuntime()
    var captured: Int? = null
    try {
      runtime.bindFunction("capture") { args ->
        captured = (args.single() as Number).toInt()
      }
      runtime.addModule(
        name = "answer",
        code = "export const value = 42;",
      )

      runtime.evaluate<Any?>(
        code = """
          import { value } from "answer";
          capture(value);
        """.trimIndent(),
        filename = "entry.js",
        asModule = true,
      )

      assertEquals(42, captured)
    } finally {
      runtime.close()
    }
  }

  /**
   * 验证关闭操作可重复调用，并正确暴露生命周期状态。
   */
  @Test
  fun closeRuntimeIdempotently() {
    val runtime = QuickJsRuntime()
    assertFalse(runtime.isClosed)

    runtime.close()
    runtime.close()

    assertTrue(runtime.isClosed)
  }
}
