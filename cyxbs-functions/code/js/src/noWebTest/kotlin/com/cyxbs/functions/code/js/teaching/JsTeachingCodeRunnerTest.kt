package com.cyxbs.functions.code.js.teaching

import com.cyxbs.functions.code.js.JsExecutableOrigin
import com.cyxbs.functions.code.js.JsTeachingCodeRunner
import com.cyxbs.functions.code.js.bundle.teaching.JsConsoleLevel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [DefaultJsTeachingCodeRunner] 的教学入口集成测试。
 */
class JsTeachingCodeRunnerTest {

  /** 验证表达式结果和控制台输出可以在一次运行后完整返回。 */
  @Test
  fun executeCodeAndCollectConsoleOutput() = runTest {
    val runner = JsTeachingCodeRunner.create()

    val result = runner.execute(
      """
        const values = [20, 22];
        console.log("result", values);
        values[0] + values[1];
      """.trimIndent(),
    )

    assertEquals(42L, result.value)
    assertEquals(JsExecutableOrigin.COMPILED_SOURCE, result.executableOrigin)
    assertEquals(1, result.consoleMessages.size)
    assertEquals(JsConsoleLevel.LOG, result.consoleMessages.single().level)
    assertEquals("result [20,22]", result.consoleMessages.single().text)
  }

  /** 验证相同源码再次运行时可以复用当前 Runner 的进程内字节码缓存。 */
  @Test
  fun reuseBytecodeForUnchangedCode() = runTest {
    val runner = JsTeachingCodeRunner.create()
    val code = "6 * 7"

    val first = runner.execute(code)
    val second = runner.execute(code)

    assertEquals(JsExecutableOrigin.COMPILED_SOURCE, first.executableOrigin)
    assertEquals(JsExecutableOrigin.BYTECODE_CACHE, second.executableOrigin)
    assertEquals(42L, second.value)
  }

  /** 验证用户修改源码后不会误用上一版入口字节码。 */
  @Test
  fun recompileChangedCode() = runTest {
    val runner = JsTeachingCodeRunner.create()

    val first = runner.execute("40 + 2")
    val changed = runner.execute("40 + 3")

    assertEquals(42L, first.value)
    assertEquals(43L, changed.value)
    assertEquals(JsExecutableOrigin.COMPILED_SOURCE, changed.executableOrigin)
  }
}
