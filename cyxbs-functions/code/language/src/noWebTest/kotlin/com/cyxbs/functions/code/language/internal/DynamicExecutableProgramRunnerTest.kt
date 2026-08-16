package com.cyxbs.functions.code.language.internal

import com.cyxbs.functions.code.language.js.bridge.DynamicExecutableModule
import com.cyxbs.functions.code.language.js.bridge.DynamicExecutableProgram
import com.cyxbs.functions.code.js.runtime.JsRuntime
import com.cyxbs.functions.code.js.runtime.JsRuntimeConfig
import com.cyxbs.functions.code.js.runtime.JsRuntimeFactory
import com.cyxbs.functions.code.js.runtime.JsRuntimeOptions
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 验证通用执行器只使用隔离 Runtime 和纯内存 Module Loader。 */
class DynamicExecutableProgramRunnerTest {

  /** 执行器应解析相对依赖、收集 console、返回 JSON 值并始终关闭 Runtime。 */
  @Test
  fun runsModuleGraphInFreshRuntime() = runTest {
    val factory = FakeRuntimeFactory()
    val runner = DynamicExecutableProgramRunner { factory }
    val program = DynamicExecutableProgram(
      entryModuleName = "lesson/main.mjs",
      entryExportName = "runLesson",
      modules = listOf(
        DynamicExecutableModule(
          name = "lesson/main.mjs",
          source = "import { value } from './value.mjs'; export function runLesson(){return value;}",
        ),
        DynamicExecutableModule(name = "lesson/value.mjs", source = "export const value = 42;"),
      ),
    )

    val result = runner.run(
      program = program,
      arguments = emptyList(),
      config = JsRuntimeConfig(evaluationTimeoutMillis = 1_000),
    )

    assertEquals(JsonPrimitive(42), result.returnValue)
    assertEquals("value 42\n", result.standardOutput)
    assertEquals("", result.standardError)
    assertTrue(factory.runtime.isClosed)
    assertFalse(factory.options.allowBytecodeCache)
    val loader = factory.options.moduleLoader!!
    val dependency = loader.normalizer!!.normalize("lesson/main.mjs", "./value.mjs")
    assertEquals("lesson/value.mjs", dependency)
    assertEquals("export const value = 42;", loader.load(dependency))
    val outsideWorkspace = loader.normalizer!!.normalize("lesson/main.mjs", "../../value.mjs")
    assertEquals(null, loader.load(outsideWorkspace))
  }

  /** 为测试记录执行器传入的 Module Loader 和缓存策略。 */
  private class FakeRuntimeFactory : JsRuntimeFactory {
    lateinit var options: JsRuntimeOptions
    val runtime = FakeRuntime()

    override fun create(options: JsRuntimeOptions): JsRuntime {
      this.options = options
      return runtime
    }
  }

  /** 模拟持久上下文，只实现执行器本测试需要的基础值和 console 行为。 */
  private class FakeRuntime : JsRuntime {
    override var isClosed: Boolean = false
      private set
    private var consoleFunctions: Map<String, (Array<Any?>) -> Any?> = emptyMap()
    private var evaluationCount = 0

    override suspend fun evaluateValue(
      code: String,
      filename: String,
      asModule: Boolean,
    ): Any? {
      evaluationCount += 1
      return if (asModule) {
        consoleFunctions.getValue("log")(arrayOf("value", 42))
        null
      } else {
        42
      }
    }

    override fun interruptEvaluation() = Unit

    override fun bindFunction(name: String, block: (Array<Any?>) -> Any?) = Unit

    override fun bindObjectFunctions(
      name: String,
      functions: Map<String, (Array<Any?>) -> Any?>,
    ) {
      assertEquals("console", name)
      consoleFunctions = functions
    }

    override fun bindAsyncFunction(
      name: String,
      block: suspend (Array<Any?>) -> Any?,
    ) = Unit

    override fun close() {
      isClosed = true
    }
  }
}
