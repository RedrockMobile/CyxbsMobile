package com.cyxbs.functions.code.language.internal

import com.cyxbs.functions.code.language.js.bridge.DynamicExecutableModule
import com.cyxbs.functions.code.language.js.bridge.DynamicExecutableProgram
import com.cyxbs.functions.code.js.quickjs.QuickJsRuntimeFactory
import com.cyxbs.functions.code.js.runtime.JsRuntimeConfig
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/** 使用真实 QuickJS 验证统一 Module 图、宿主入口和 console bridge。 */
class DynamicExecutableProgramRunnerQuickJsTest {

  /** 多模块程序应在独立 QuickJS Runtime 中执行并返回 JSON 基础值。 */
  @Test
  fun executesMultiModuleProgramWithQuickJs() = runTest {
    val runner = DynamicExecutableProgramRunner { QuickJsRuntimeFactory }
    val result = runner.run(
      program = DynamicExecutableProgram(
        entryModuleName = "lesson/main.mjs",
        entryExportName = "runLesson",
        modules = listOf(
          DynamicExecutableModule(
            name = "lesson/main.mjs",
            source = """
              import { bonus } from "./bonus.mjs";
              export async function runLesson(value) {
                const result = await Promise.resolve(value + bonus);
                console.log("result", result);
                return result;
              }
            """.trimIndent(),
          ),
          DynamicExecutableModule(
            name = "lesson/bonus.mjs",
            source = "export const bonus = 7;",
          ),
        ),
      ),
      arguments = listOf(JsonPrimitive(5)),
      config = JsRuntimeConfig(evaluationTimeoutMillis = 2_000),
    )

    assertEquals(JsonPrimitive(12), result.returnValue)
    assertEquals("result 12\n", result.standardOutput)
    assertEquals("", result.standardError)
  }
}
