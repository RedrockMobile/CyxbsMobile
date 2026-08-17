package com.cyxbs.functions.code.language.internal

import com.cyxbs.functions.code.js.quickjs.QuickJsRuntimeFactory
import com.cyxbs.functions.code.js.runtime.JsRuntimeConfig
import com.cyxbs.functions.code.language.DynamicLanguageExecutionException
import com.cyxbs.functions.code.language.DynamicProgramOutputChannel
import com.cyxbs.functions.code.language.DynamicProgramOutputEvent
import com.cyxbs.functions.code.language.js.bridge.DynamicExecutableModule
import com.cyxbs.functions.code.language.js.bridge.DynamicExecutableProgram
import com.cyxbs.functions.code.language.js.bridge.DynamicGeneratedSourceMapping
import com.cyxbs.functions.code.language.js.bridge.DynamicProgramHostAbi
import com.cyxbs.functions.code.language.js.bridge.DynamicSourceLocation
import com.cyxbs.functions.code.language.js.bridge.DynamicTextRange
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 使用真实 QuickJS 验证统一 Module 图、宿主入口和 console bridge。 */
class DynamicExecutableProgramRunnerQuickJsTest {

  /** 多模块程序应在独立 QuickJS Runtime 中执行并返回 JSON 基础值。 */
  @Test
  fun executesMultiModuleProgramWithQuickJs() = runTest {
    val runner = DynamicExecutableProgramRunner { QuickJsRuntimeFactory }
    val events = mutableListOf<DynamicProgramOutputEvent>()
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
                if (typeof globalThis.${DynamicProgramHostAbi.WRITE_STANDARD_OUTPUT_UTF8_BASE64_CHUNK} !== "undefined" ||
                    typeof globalThis.${DynamicProgramHostAbi.WRITE_STANDARD_ERROR_UTF8_BASE64_CHUNK} !== "undefined") {
                  throw new Error("private output consumers must be hidden");
                }
                globalThis.${DynamicProgramHostAbi.WRITE_STANDARD_OUTPUT_UTF8_BASE64_CHUNK} = function () {
                  throw new Error("overwritten stdout consumer must not be called");
                };
                const result = await Promise.resolve(value + bonus);
                ${DynamicProgramHostAbi.WRITE_STANDARD_OUTPUT}("raw:猫🐶");
                ${DynamicProgramHostAbi.WRITE_STANDARD_OUTPUT}(String.fromCharCode(0xD83D));
                console.log("result🐶", result);
                ${DynamicProgramHostAbi.WRITE_STANDARD_ERROR}("warning🐶");
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
      outputSink = events::add,
      maxOutputBytes = 1_024,
    )

    assertEquals(JsonPrimitive(12), result.returnValue)
    assertEquals("raw:猫🐶�result🐶 12\n", result.standardOutput)
    assertEquals("warning🐶", result.standardError)
    assertEquals(
      listOf(
        DynamicProgramOutputEvent(DynamicProgramOutputChannel.STANDARD_OUTPUT, "raw:猫🐶"),
        // JS 输出的孤立代理统一编码为 U+FFFD，避免引擎/平台 replacement 行为不一致。
        DynamicProgramOutputEvent(DynamicProgramOutputChannel.STANDARD_OUTPUT, "�"),
        DynamicProgramOutputEvent(DynamicProgramOutputChannel.STANDARD_OUTPUT, "result🐶 12\n"),
        DynamicProgramOutputEvent(DynamicProgramOutputChannel.STANDARD_ERROR, "warning🐶"),
      ),
      events,
    )
  }

  /** 真实 QuickJS 的模块栈应通过稀疏映射还原为动态语言源码位置。 */
  @Test
  fun mapsQuickJsFailureBackToDynamicSource() = runTest {
    val failure = assertFailsWith<DynamicLanguageExecutionException> {
      DynamicExecutableProgramRunner { QuickJsRuntimeFactory }.run(
        program = DynamicExecutableProgram(
          entryModuleName = "lesson/failure.mjs",
          entryExportName = "runLesson",
          modules = listOf(
            DynamicExecutableModule(
              name = "lesson/failure.mjs",
              source = """
                export function runLesson() {
                  const value = 1;
                  throw new Error("mapped failure: " + value);
                }
              """.trimIndent(),
              sourceMappings = listOf(
                DynamicGeneratedSourceMapping(
                  generatedLine = 3,
                  generatedColumn = 2,
                  sourceLocation = DynamicSourceLocation(
                    filePath = "src/Main.java",
                    range = DynamicTextRange(from = 48, to = 62),
                  ),
                ),
              ),
            ),
          ),
        ),
        arguments = emptyList(),
        config = JsRuntimeConfig(evaluationTimeoutMillis = 2_000),
        maxOutputBytes = 1_024,
      )
    }

    assertEquals("src/Main.java", failure.sourceFrames.first().sourceLocation.filePath)
    assertEquals(48, failure.sourceFrames.first().sourceLocation.range.from)
  }

  /**
   * 真实 QuickJS 应在 JS 内完整恢复 supplementary 输入，同时保持入口 JSON 参数原有含义。
   *
   * 旧 quickjs-kt 的 JS→Kotlin String 边界会损坏 supplementary 字符，因此这里不能直接把原文
   * return 给 Kotlin；改为在 JS 内返回纯 ASCII 的长度、UTF-16 code unit 和 code point 证据，避免
   * 把返回边界缺陷误判为标准输入解码失败。
   */
  @Test
  fun readsDifferentPreloadedInputFromEachQuickJsRuntime() = runTest {
    val runner = DynamicExecutableProgramRunner { QuickJsRuntimeFactory }
    val program = DynamicExecutableProgram(
      entryModuleName = "lesson/input.mjs",
      entryExportName = "runLesson",
      modules = listOf(
        DynamicExecutableModule(
          name = "lesson/input.mjs",
          source = """
            ${DynamicProgramHostAbi.STANDARD_INPUT_READER_SOURCE}

            export function runLesson(prefix) {
              if (globalThis.${DynamicProgramHostAbi.READ_STANDARD_INPUT_UTF8_BASE64}.length !== 0) {
                throw new Error("standard input wrapper must not expose host arguments");
              }
              const ignoredArgumentResult = globalThis.${DynamicProgramHostAbi.READ_STANDARD_INPUT_UTF8_BASE64}(
                "x".repeat(100000),
              );
              const input = ${DynamicProgramHostAbi.READ_STANDARD_INPUT_TEXT}();
              const codeUnits = [];
              for (let index = 0; index < input.length; index++) {
                codeUnits.push(input.charCodeAt(index));
              }
              const codePoints = Array.from(input, character => character.codePointAt(0));
              return prefix + ":base64=" + ignoredArgumentResult.length + ":length=" + input.length +
                ":units=" + codeUnits.join(",") +
                ":points=" + codePoints.join(",");
            }
          """.trimIndent(),
        ),
      ),
    )

    val first = runner.run(
      program = program,
      arguments = listOf(JsonPrimitive("first")),
      config = JsRuntimeConfig(evaluationTimeoutMillis = 2_000),
      maxOutputBytes = 1_024,
      standardInput = "猫🐶",
      maxInputBytes = 16,
    )
    val second = runner.run(
      program = program,
      arguments = listOf(JsonPrimitive("second")),
      config = JsRuntimeConfig(evaluationTimeoutMillis = 2_000),
      maxOutputBytes = 1_024,
      standardInput = "next",
      maxInputBytes = 16,
    )

    assertEquals(
      JsonPrimitive("first:base64=12:length=3:units=29483,55357,56374:points=29483,128054"),
      first.returnValue,
    )
    assertEquals(
      JsonPrimitive("second:base64=8:length=4:units=110,101,120,116:points=110,101,120,116"),
      second.returnValue,
    )
  }

  /**
   * Java compiler 的 Scanner 产物形态应直接消费 Runner 安装的 Base64 host getter。
   *
   * Java compiler 仅构建于 Kotlin/JS，而本测试运行于 desktop QuickJS，无法在同一 source set
   * 现场调用 compiler；这里固定验证它们的真实 ABI 接缝：产物内先声明标准输入 reader，再由
   * Scanner helper 读取预加载 token，全程不手工安装解码后的文本函数。
   */
  @Test
  fun executesJavaScannerArtifactShapeWithQuickJsRunnerInput() = runTest {
    val result = DynamicExecutableProgramRunner { QuickJsRuntimeFactory }.run(
      program = DynamicExecutableProgram(
        entryModuleName = "lesson/java-scanner.mjs",
        entryExportName = "runLesson",
        modules = listOf(
          DynamicExecutableModule(
            name = "lesson/java-scanner.mjs",
            source = """
              ${DynamicProgramHostAbi.STANDARD_INPUT_READER_SOURCE}

              let ${'$'}__j_scanner_input_state = null;
              function ${'$'}__j_scanner_input() {
                if (${'$'}__j_scanner_input_state === null) {
                  ${'$'}__j_scanner_input_state = {
                    text: ${DynamicProgramHostAbi.READ_STANDARD_INPUT_TEXT}(),
                    cursor: 0,
                  };
                }
                return ${'$'}__j_scanner_input_state;
              }
              function ${'$'}__j_scanner_new(stream) {
                if (stream !== 2) throw new Error("Scanner only supports System.in");
                ${'$'}__j_scanner_input();
                return { ${'$'}__j_scanner: true };
              }
              function ${'$'}__j_scanner_next(scanner) {
                if (scanner.${'$'}__j_scanner !== true) throw new Error("invalid scanner");
                const state = ${'$'}__j_scanner_input();
                let start = state.cursor;
                while (start < state.text.length && /\s/.test(state.text[start])) start++;
                let end = start;
                while (end < state.text.length && !/\s/.test(state.text[end])) end++;
                if (start === end) throw new Error("java.util.NoSuchElementException");
                state.cursor = end;
                return state.text.slice(start, end);
              }

              export function runLesson() {
                const scanner = ${'$'}__j_scanner_new(2);
                const token = ${'$'}__j_scanner_next(scanner);
                return "length=" + token.length + ":points=" +
                  Array.from(token, character => character.codePointAt(0)).join(",");
              }
            """.trimIndent(),
          ),
        ),
      ),
      arguments = emptyList(),
      config = JsRuntimeConfig(evaluationTimeoutMillis = 2_000),
      maxOutputBytes = 1_024,
      standardInput = "猫🐶 remaining",
      maxInputBytes = 32,
    )

    assertEquals(JsonPrimitive("length=3:points=29483,128054"), result.returnValue)
  }

  /** 接近默认上限的预加载输入应线性解码，不能因逐字符拼接退化为平方复杂度。 */
  @Test
  fun decodesNearLimitPreloadedInputWithQuickJs() = runTest {
    val input = "a".repeat(64 * 1024)
    val result = DynamicExecutableProgramRunner { QuickJsRuntimeFactory }.run(
      program = DynamicExecutableProgram(
        entryModuleName = "lesson/large-input.mjs",
        entryExportName = "runLesson",
        modules = listOf(
          DynamicExecutableModule(
            name = "lesson/large-input.mjs",
            source = """
              ${DynamicProgramHostAbi.STANDARD_INPUT_READER_SOURCE}

              export function runLesson() {
                const input = ${DynamicProgramHostAbi.READ_STANDARD_INPUT_TEXT}();
                return "length=" + input.length + ":first=" + input.charCodeAt(0) +
                  ":last=" + input.charCodeAt(input.length - 1);
              }
            """.trimIndent(),
          ),
        ),
      ),
      arguments = emptyList(),
      config = JsRuntimeConfig(evaluationTimeoutMillis = 2_000),
      maxOutputBytes = 1_024,
      standardInput = input,
      maxInputBytes = 64L * 1024L,
    )

    // 返回纯 ASCII，避免把 quickjs-kt 已知的 supplementary 返回边界问题混入输入桥测试。
    assertEquals(JsonPrimitive("length=65536:first=97:last=97"), result.returnValue)
  }

  /** 标准输入 helper 必须拒绝 padding 未使用 bit 非零的非 canonical Base64。 */
  @Test
  fun rejectsNonCanonicalInputBase64WithQuickJs() = runTest {
    val result = DynamicExecutableProgramRunner { QuickJsRuntimeFactory }.run(
      program = DynamicExecutableProgram(
        entryModuleName = "lesson/base64.mjs",
        entryExportName = "runLesson",
        modules = listOf(
          DynamicExecutableModule(
            name = "lesson/base64.mjs",
            source = """
              ${DynamicProgramHostAbi.STANDARD_INPUT_READER_SOURCE}

              export function runLesson() {
                const rejected = [];
                for (const payload of ["AB==", "Zm9="]) {
                  try {
                    ${DynamicProgramHostAbi.DECODE_UTF8_BASE64}(payload);
                    rejected.push(false);
                  } catch (error) {
                    rejected.push(true);
                  }
                }
                return rejected.join(",");
              }
            """.trimIndent(),
          ),
        ),
      ),
      arguments = emptyList(),
      config = JsRuntimeConfig(evaluationTimeoutMillis = 2_000),
      maxOutputBytes = 1_024,
    )

    assertEquals(JsonPrimitive("true,true"), result.returnValue)
  }

  /** 大输出应在 JS 内分块，宿主只保留连续 UTF-8 前缀并精确统计后续丢弃字节。 */
  @Test
  fun chunksLargeOutputBeforeCrossingQuickJsHostBoundary() = runTest {
    val events = mutableListOf<DynamicProgramOutputEvent>()
    val result = DynamicExecutableProgramRunner { QuickJsRuntimeFactory }.run(
      program = DynamicExecutableProgram(
        entryModuleName = "lesson/output.mjs",
        entryExportName = "runLesson",
        modules = listOf(
          DynamicExecutableModule(
            name = "lesson/output.mjs",
            source = """
              export function runLesson() {
                ${DynamicProgramHostAbi.WRITE_STANDARD_OUTPUT}("🐶" + "x".repeat(100000));
                return 1;
              }
            """.trimIndent(),
          ),
        ),
      ),
      arguments = emptyList(),
      config = JsRuntimeConfig(evaluationTimeoutMillis = 2_000),
      outputSink = events::add,
      maxOutputBytes = 5,
    )

    assertEquals("🐶x", result.standardOutput)
    assertEquals("", result.standardError)
    assertEquals(true, result.outputTruncated)
    assertEquals(99_999, result.droppedOutputBytes)
    assertEquals(
      listOf(DynamicProgramOutputEvent(DynamicProgramOutputChannel.STANDARD_OUTPUT, "🐶x")),
      events,
    )
  }

  /** JS helper 初始化后即使常用原型被用户污染，预加载输入和输出仍只使用捕获的原生函数。 */
  @Test
  fun isolatesInputAndOutputFromPrototypePollutionWithQuickJs() = runTest {
    val events = mutableListOf<DynamicProgramOutputEvent>()
    val result = DynamicExecutableProgramRunner { QuickJsRuntimeFactory }.run(
      program = DynamicExecutableProgram(
        entryModuleName = "lesson/prototype.mjs",
        entryExportName = "runLesson",
        modules = listOf(
          DynamicExecutableModule(
            name = "lesson/prototype.mjs",
            source = """
              ${DynamicProgramHostAbi.STANDARD_INPUT_READER_SOURCE}

              const safeReflectApply = Reflect.apply;
              const safeCharCodeAt = String.prototype.charCodeAt;
              export function runLesson() {
                Array.prototype.push = function () { throw new Error("polluted push"); };
                Array.prototype.join = function () { throw new Error("polluted join"); };
                String.prototype.charCodeAt = function () { throw new Error("polluted charCodeAt"); };
                String.fromCharCode = function () { throw new Error("polluted fromCharCode"); };
                Reflect.apply = function () { throw new Error("polluted Reflect.apply"); };

                const input = ${DynamicProgramHostAbi.READ_STANDARD_INPUT_TEXT}();
                ${DynamicProgramHostAbi.WRITE_STANDARD_OUTPUT}("ok🐶");
                console.log("console🐶");
                return "length=" + input.length +
                  ":first=" + safeReflectApply(safeCharCodeAt, input, [0]);
              }
            """.trimIndent(),
          ),
        ),
      ),
      arguments = emptyList(),
      config = JsRuntimeConfig(evaluationTimeoutMillis = 2_000),
      outputSink = events::add,
      maxOutputBytes = 1_024,
      standardInput = "猫🐶",
      maxInputBytes = 16,
    )

    assertEquals(JsonPrimitive("length=3:first=29483"), result.returnValue)
    assertEquals("ok🐶console🐶\n", result.standardOutput)
    assertEquals(
      listOf(
        DynamicProgramOutputEvent(DynamicProgramOutputChannel.STANDARD_OUTPUT, "ok🐶"),
        DynamicProgramOutputEvent(DynamicProgramOutputChannel.STANDARD_OUTPUT, "console🐶\n"),
      ),
      events,
    )
  }
}
