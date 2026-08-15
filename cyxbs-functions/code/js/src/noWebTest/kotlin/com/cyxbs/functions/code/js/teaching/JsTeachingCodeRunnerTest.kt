package com.cyxbs.functions.code.js.teaching

import com.cyxbs.functions.code.js.bundle.teaching.JsConsoleLevel
import com.cyxbs.functions.code.js.quickjs.QuickJsRuntimeFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** [DefaultJsTeachingCodeRunner] 的无缓存教学入口集成测试。 */
class JsTeachingCodeRunnerTest {

  /** 验证表达式结果和控制台输出可以在一次运行后完整返回。 */
  @Test
  fun executeCodeAndCollectConsoleOutput() = runTest {
    val runner = JsTeachingCodeRunner.create(QuickJsRuntimeFactory)

    val result = runner.execute(
      """
        const values = [20, 22];
        console.log("result", values);
        values[0] + values[1];
      """.trimIndent(),
    )

    assertEquals(42L, result.value)
    assertEquals(1, result.consoleMessages.size)
    assertEquals(JsConsoleLevel.LOG, result.consoleMessages.single().level)
    assertEquals("result [20,22]", result.consoleMessages.single().text)
  }

  /** 验证同一 Runner 连续执行修改后的源码时始终使用当前源码。 */
  @Test
  fun executeChangedCodeWithoutPersistentCache() = runTest {
    val runner = JsTeachingCodeRunner.create(QuickJsRuntimeFactory)

    assertEquals(42L, runner.execute("40 + 2").value)
    assertEquals(43L, runner.execute("40 + 3").value)
  }

  /** 验证教学工作区可以从入口 Module 加载相对依赖并收集其控制台输出。 */
  @Test
  fun executeModuleWorkspaceWithRelativeImport() = runTest {
    val runner = JsTeachingCodeRunner.create(QuickJsRuntimeFactory)

    val result = runner.executeModule(
      files = mapOf(
        "main.js" to """
          import { answer } from "./shared/answer.js";
          console.log("module answer", answer);
        """.trimIndent(),
        "shared/answer.js" to "export const answer = 42;",
      ),
      entryFile = "main.js",
    )

    assertEquals(null, result.value)
    assertEquals(1, result.consoleMessages.size)
    assertEquals(JsConsoleLevel.LOG, result.consoleMessages.single().level)
    assertEquals("module answer 42", result.consoleMessages.single().text)
  }
}
