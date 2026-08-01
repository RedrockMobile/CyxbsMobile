package com.cyxbs.functions.code.js.runtime

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
   * 验证不含 import 的独立 ES Module 字节码能够直接加载。
   */
  @Test
  fun compileAndEvaluateStandaloneModuleBytecode() = runTest {
    val runtime = QuickJsRuntime()
    try {
      val bytecode = runtime.compile(
        code = "globalThis.__moduleBytecodeValue = 40 + 2",
        filename = "module-bytecode.mjs",
        asModule = true,
      )

      runtime.evaluate<Unit>(bytecode)

      assertEquals(42, runtime.evaluate<Int>("globalThis.__moduleBytecodeValue"))
    } finally {
      runtime.close()
    }
  }

  /**
   * 验证无限循环会被 1.0.8 提供的原生超时机制中断，避免长期占用执行线程。
   */
  @Test
  fun interruptInfiniteLoopOnTimeout() = runTest {
    val runtime = QuickJsRuntime(
      config = QuickJsRuntimeConfig(evaluationTimeoutMillis = 50L),
    )
    try {
      kotlin.test.assertFailsWith<com.dokar.quickjs.QuickJsInterruptedException> {
        runtime.evaluate<Unit>("while (true) {}")
      }
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
   * 验证 ES Module 可以通过 Runtime 级 loader 按需提供，并由入口代码通过标准 import 调用。
   */
  @Test
  fun loadEsModule() = runTest {
    val runtime = QuickJsRuntime(
      moduleLoader = JsModuleLoader { name ->
        if (name == "answer") {
          JsModuleContent.Source("export const value = 42;")
        } else {
          null
        }
      },
    )
    var captured: Int? = null
    try {
      runtime.bindFunction("capture") { args ->
        captured = (args.single() as Number).toInt()
      }

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
   * 验证静态依赖图可按需编译，并在新 Runtime 中混用有效缓存与失效源码。
   */
  @Test
  fun compileModuleGraphAndRefreshChangedSource() = runTest {
    val initialSources = mapOf(
      "leaf" to "export const value = 21;",
      "middle" to """
        import { value } from "leaf";
        export const doubled = value * 2;
      """.trimIndent(),
    )
    val initialCache = mutableMapOf<String, ByteArray>()
    val initialLoader = object : JsModuleLoader {
      override fun load(name: String): JsModuleContent? {
        return initialCache[name]?.let(JsModuleContent::Bytecode)
          ?: initialSources[name]?.let(JsModuleContent::Source)
      }

      override fun onCompiled(name: String, bytecode: ByteArray) {
        // 立即放入内存缓存，使临时解析 Context 结束后的正式执行可以复用同一份字节码。
        initialCache[name] = bytecode
      }
    }
    val entryBytecode = QuickJsRuntime(moduleLoader = initialLoader).let { runtime ->
      try {
        runtime.compile(
          code = """
            import { doubled } from "middle";
            capture(doubled);
          """.trimIndent(),
          filename = "entry",
          asModule = true,
        ).also { bytecode ->
          assertEquals(setOf("entry", "middle", "leaf"), runtime.resolveModuleGraph(bytecode))
        }
      } finally {
        runtime.close()
      }
    }

    assertEquals(setOf("leaf", "middle"), initialCache.keys)

    val refreshedSources = initialSources + mapOf(
      "middle" to """
        import { value } from "leaf";
        export const doubled = value * 2 + 1;
      """.trimIndent(),
    )
    val refreshedCache = initialCache.toMutableMap().apply {
      remove("middle")
    }
    val recompiledNames = mutableListOf<String>()
    val refreshedLoader = object : JsModuleLoader {
      override fun load(name: String): JsModuleContent? {
        return refreshedCache[name]?.let(JsModuleContent::Bytecode)
          ?: refreshedSources[name]?.let(JsModuleContent::Source)
      }

      override fun onCompiled(name: String, bytecode: ByteArray) {
        refreshedCache[name] = bytecode
        recompiledNames += name
      }
    }
    val refreshedRuntime = QuickJsRuntime(moduleLoader = refreshedLoader)
    var captured: Int? = null
    try {
      refreshedRuntime.bindFunction("capture") { args ->
        captured = (args.single() as Number).toInt()
      }

      assertEquals(
        setOf("entry", "middle", "leaf"),
        refreshedRuntime.resolveModuleGraph(entryBytecode),
      )
      assertEquals(listOf("middle"), recompiledNames)
      refreshedRuntime.evaluate<Any?>(entryBytecode)

      assertEquals(43, captured)
      assertEquals(listOf("middle"), recompiledNames)
    } finally {
      refreshedRuntime.close()
    }
  }

  /**
   * 验证动态 import 会在执行到对应语句时按需编译，并立即通知业务层新字节码。
   */
  @Test
  fun collectDynamicImportBytecodeDuringEvaluation() = runTest {
    val compiledModules = mutableMapOf<String, ByteArray>()
    val runtime = QuickJsRuntime(
      moduleLoader = object : JsModuleLoader {
        override fun load(name: String): JsModuleContent? {
          return compiledModules[name]?.let(JsModuleContent::Bytecode)
            ?: if (name == "dynamic-answer") {
              JsModuleContent.Source("export const value = 42;")
            } else {
              null
            }
        }

        override fun onCompiled(name: String, bytecode: ByteArray) {
          compiledModules[name] = bytecode
        }
      },
    )
    var captured: Int? = null
    try {
      runtime.bindFunction("capture") { args ->
        captured = (args.single() as Number).toInt()
      }

      runtime.evaluate<Any?>(
        code = """
          const answer = await import("dynamic-answer");
          capture(answer.value);
        """.trimIndent(),
        filename = "dynamic-entry",
        asModule = true,
      )

      assertEquals(42, captured)
      assertEquals(setOf("dynamic-answer"), compiledModules.keys)
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
