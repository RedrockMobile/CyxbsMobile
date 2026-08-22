package com.cyxbs.functions.code.language.internal

import com.cyxbs.functions.code.language.js.bridge.DynamicExecutableModule
import com.cyxbs.functions.code.language.js.bridge.DynamicExecutableProgram
import com.cyxbs.functions.code.language.js.bridge.DynamicGeneratedSourceMapping
import com.cyxbs.functions.code.language.js.bridge.DynamicProgramHostAbi
import com.cyxbs.functions.code.language.js.bridge.DynamicSourceLocation
import com.cyxbs.functions.code.language.js.bridge.DynamicTextRange
import com.cyxbs.functions.code.language.DynamicLanguageExecutionException
import com.cyxbs.functions.code.language.DynamicProgramOutputChannel
import com.cyxbs.functions.code.language.DynamicProgramOutputEvent
import com.cyxbs.functions.code.language.DynamicProgramRunResult
import com.cyxbs.functions.code.js.runtime.JsRuntime
import com.cyxbs.functions.code.js.runtime.JsRuntimeConfig
import com.cyxbs.functions.code.js.runtime.JsRuntimeBridge
import com.cyxbs.functions.code.js.runtime.JsRuntimeBridgeBinding
import com.cyxbs.functions.code.js.runtime.JsRuntimeCallback
import com.cyxbs.functions.code.js.runtime.JsRuntimeFactory
import com.cyxbs.functions.code.js.runtime.JsRuntimeErrorKind
import com.cyxbs.functions.code.js.runtime.JsRuntimeException
import com.cyxbs.functions.code.js.runtime.JsRuntimeOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.io.encoding.Base64

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
      maxOutputBytes = 1_024,
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

  /** 原始 stdout/stderr 应保持 print 的无换行语义，并在超量时只通知允许保留的前缀。 */
  @Test
  fun streamsRawHostOutputWithinUtf8Limit() = runTest {
    val factory = FakeRuntimeFactory(
      emitConsole = false,
      hostOutput = listOf(
        DynamicProgramHostAbi.WRITE_STANDARD_OUTPUT to "A",
        DynamicProgramHostAbi.WRITE_STANDARD_OUTPUT to "猫",
        DynamicProgramHostAbi.WRITE_STANDARD_ERROR to "error",
      ),
    )
    val events = mutableListOf<DynamicProgramOutputEvent>()
    val result = DynamicExecutableProgramRunner { factory }.run(
      program = program(),
      arguments = emptyList(),
      config = JsRuntimeConfig(evaluationTimeoutMillis = 1_000),
      outputSink = events::add,
      // A + 猫 正好 4 UTF-8 bytes，stderr 将被完整丢弃。
      maxOutputBytes = 4,
    )

    assertEquals("A猫", result.standardOutput)
    assertEquals("", result.standardError)
    assertTrue(result.outputTruncated)
    assertEquals(5, result.droppedOutputBytes)
    assertEquals(
      listOf(
        DynamicProgramOutputEvent(DynamicProgramOutputChannel.STANDARD_OUTPUT, "A"),
        DynamicProgramOutputEvent(DynamicProgramOutputChannel.STANDARD_OUTPUT, "猫"),
      ),
      events,
    )
  }

  /** 超配额输出必须统计完整 UTF-8 大小，且不能把四字节代理对截成半个字符。 */
  @Test
  fun truncatesLargeOutputWithoutSplittingSurrogatePair() = runTest {
    val oversizedText = "A🐶" + "x".repeat(64 * 1024)
    val factory = FakeRuntimeFactory(
      emitConsole = false,
      hostOutput = listOf(DynamicProgramHostAbi.WRITE_STANDARD_OUTPUT to oversizedText),
    )
    val result = DynamicExecutableProgramRunner { factory }.run(
      program = program(),
      arguments = emptyList(),
      config = JsRuntimeConfig(evaluationTimeoutMillis = 1_000),
      // 只够保存 A，不能把后面的四字节代理对拆开。
      maxOutputBytes = 4,
    )

    assertEquals("A", result.standardOutput)
    assertTrue(result.outputTruncated)
    assertEquals(4L + 64L * 1024L, result.droppedOutputBytes)
  }

  /** 首次截断后不得在下一次宿主调用中越过已丢字符继续接收较小字符。 */
  @Test
  fun keepsAContinuousPrefixAcrossOutputChunks() = runTest {
    val factory = FakeRuntimeFactory(
      emitConsole = false,
      hostOutput = listOf(
        DynamicProgramHostAbi.WRITE_STANDARD_OUTPUT to "A🐶",
        DynamicProgramHostAbi.WRITE_STANDARD_OUTPUT to "B",
      ),
    )
    val result = DynamicExecutableProgramRunner { factory }.run(
      program = program(),
      arguments = emptyList(),
      config = JsRuntimeConfig(evaluationTimeoutMillis = 1_000),
      maxOutputBytes = 4,
    )

    assertEquals("A", result.standardOutput)
    assertTrue(result.outputTruncated)
    assertEquals(5, result.droppedOutputBytes)
  }

  /** 同步输出回调主动抛出的取消异常仍属于宿主边界失败，不能取消调用方协程。 */
  @Test
  fun wrapsCancellationThrownByOutputSink() = runTest {
    val factory = FakeRuntimeFactory(
      emitConsole = false,
      hostOutput = listOf(DynamicProgramHostAbi.WRITE_STANDARD_OUTPUT to "value"),
    )

    val failure = assertFailsWith<DynamicLanguageExecutionException> {
      DynamicExecutableProgramRunner { factory }.run(
        program = program(),
        arguments = emptyList(),
        config = JsRuntimeConfig(evaluationTimeoutMillis = 1_000),
        outputSink = { throw CancellationException("sink") },
        maxOutputBytes = 1_024,
      )
    }

    assertTrue(failure.cause is CancellationException)
    assertEquals("sink", failure.cause?.message)
  }

  /** 默认空输入和 Unicode 输入应分别绑定到各自新建的 Runtime，不能跨运行残留。 */
  @Test
  fun exposesPreloadedInputInEachFreshRuntime() = runTest {
    val factory = FakeRuntimeFactory(emitConsole = false, readStandardInput = true)
    val runner = DynamicExecutableProgramRunner { factory }

    runner.run(
      program = program(),
      arguments = emptyList(),
      config = JsRuntimeConfig(evaluationTimeoutMillis = 1_000),
      maxOutputBytes = 1_024,
    )
    runner.run(
      program = program(),
      arguments = emptyList(),
      config = JsRuntimeConfig(evaluationTimeoutMillis = 1_000),
      maxOutputBytes = 1_024,
      standardInput = "第一行🐶\n第二行",
      maxInputBytes = 64,
    )

    assertEquals(
      listOf("", "第一行🐶\n第二行").map { Base64.encode(it.encodeToByteArray()) },
      factory.runtimes.map { it.observedStandardInputUtf8Base64 },
    )
    assertEquals(2, factory.runtimes.size)
    assertFalse(factory.runtimes[0] === factory.runtimes[1])
    assertTrue(factory.runtimes.all { it.isClosed })
  }

  /** 输入限制按 UTF-8 标量边界计算，代理对超限时应在创建 Runtime 前明确失败。 */
  @Test
  fun rejectsOversizedInputBeforeCreatingRuntime() = runTest {
    val factory = FakeRuntimeFactory(emitConsole = false, readStandardInput = true)

    val failure = assertFailsWith<DynamicLanguageExecutionException> {
      DynamicExecutableProgramRunner { factory }.run(
        program = program(),
        arguments = emptyList(),
        config = JsRuntimeConfig(evaluationTimeoutMillis = 1_000),
        maxOutputBytes = 1_024,
        // A 为一字节，完整代理对为四字节；四字节额度不能容纳整个输入。
        standardInput = "A🐶",
        maxInputBytes = 4,
      )
    }

    assertTrue(failure.message.orEmpty().contains("4 bytes"))
    assertEquals(0, factory.runtimes.size)
  }

  /** 孤立高、低代理都必须在创建 Runtime 前失败，不能依赖平台编码器的 replacement 行为。 */
  @Test
  fun rejectsUnpairedInputSurrogatesBeforeCreatingRuntime() = runTest {
    listOf("\uD83D", "\uDC36").forEach { invalidInput ->
      val factory = FakeRuntimeFactory(emitConsole = false)

      val failure = assertFailsWith<DynamicLanguageExecutionException> {
        DynamicExecutableProgramRunner { factory }.run(
          program = program(),
          arguments = emptyList(),
          config = JsRuntimeConfig(evaluationTimeoutMillis = 1_000),
          maxOutputBytes = 1_024,
          standardInput = invalidInput,
          maxInputBytes = 64,
        )
      }

      assertTrue(failure.message.orEmpty().contains("unpaired UTF-16 surrogate"))
      assertTrue(factory.runtimes.isEmpty())
    }
  }

  /** 私有宿主 ABI 应在解码前拒绝超大或非 canonical Base64，避免额外大块分配。 */
  @Test
  fun rejectsInvalidOutputBase64BeforeDecoding() = runTest {
    val oversizedPayload = "A".repeat(
      ((DynamicProgramHostAbi.MAX_OUTPUT_UTF8_CHUNK_BYTES + 2) / 3) * 4 + 4,
    )
    listOf(oversizedPayload, "AB==", "Zm9=", "/w==").forEach { invalidPayload ->
      val factory = FakeRuntimeFactory(
        emitConsole = false,
        hostBase64Output = listOf(
          DynamicProgramHostAbi.WRITE_STANDARD_OUTPUT_UTF8_BASE64_CHUNK to invalidPayload,
        ),
      )

      assertFailsWith<DynamicLanguageExecutionException> {
        DynamicExecutableProgramRunner { factory }.run(
          program = program(),
          arguments = emptyList(),
          config = JsRuntimeConfig(evaluationTimeoutMillis = 1_000),
          maxOutputBytes = 1_024,
        )
      }
    }
  }

  /** 新增结果字段必须位于旧 diagnostics 之后，保留第五位置参数与 component5 的源码含义。 */
  @Test
  fun preservesRunResultPositionalDiagnosticsCompatibility() {
    val result = DynamicProgramRunResult(false, null, "out", "err", emptyList())
    val (_, _, _, _, diagnostics) = result

    assertEquals(emptyList(), diagnostics)
    assertFalse(result.outputTruncated)
    assertEquals(0, result.droppedOutputBytes)
  }

  /** Runtime 生成位置应沿稀疏 mapping 恢复到最接近的原始源码区间。 */
  @Test
  fun mapsRuntimeFailureBackToDynamicSourceFrames() = runTest {
    val failure = JsRuntimeException(
      kind = JsRuntimeErrorKind.RUNTIME_ERROR,
      message = "boom",
      fileName = "lesson/main.mjs",
      lineNumber = 5,
      columnNumber = 3,
      jsStack = "at run (lesson/main.mjs:5:3)",
    )
    val factory = FakeRuntimeFactory(emitConsole = false, runtimeFailure = failure)
    val sourceLocation = DynamicSourceLocation("Main.java", DynamicTextRange(24, 40))
    val program = DynamicExecutableProgram(
      entryModuleName = "lesson/main.mjs",
      entryExportName = "runLesson",
      modules = listOf(
        DynamicExecutableModule(
          name = "lesson/main.mjs",
          source = "export function runLesson(){ throw new Error('boom'); }",
          sourceMappings = listOf(
            DynamicGeneratedSourceMapping(2, 0, DynamicSourceLocation("Main.java", DynamicTextRange(0, 10))),
            DynamicGeneratedSourceMapping(4, 0, sourceLocation),
          ),
        ),
      ),
    )

    val mapped = assertFailsWith<DynamicLanguageExecutionException> {
      DynamicExecutableProgramRunner { factory }.run(
        program = program,
        arguments = emptyList(),
        config = JsRuntimeConfig(),
        maxOutputBytes = 1_024,
      )
    }

    assertEquals(sourceLocation, mapped.sourceFrames.single().sourceLocation)
    assertTrue(mapped.message.orEmpty().contains("Main.java"))
    assertTrue(mapped.cause === failure)
  }

  /** 生成源码配额必须在创建 Runtime 前拒绝异常 Module 图。 */
  @Test
  fun rejectsOversizedGeneratedProgramBeforeCreatingRuntime() = runTest {
    val factory = FakeRuntimeFactory(emitConsole = false)

    val failure = assertFailsWith<DynamicLanguageExecutionException> {
      DynamicExecutableProgramRunner { factory }.run(
        program = program(),
        arguments = emptyList(),
        config = JsRuntimeConfig(),
        maxOutputBytes = 1_024,
        maxProgramSourceBytes = 8,
      )
    }

    assertTrue(failure.message.orEmpty().contains("oversized"))
    assertTrue(factory.runtimes.isEmpty())
  }

  /** 损坏的动态源码区间必须在进入 Runtime 前拒绝，不能把负偏移传给编辑器导航。 */
  @Test
  fun rejectsInvalidGeneratedSourceMappingBeforeCreatingRuntime() = runTest {
    val factory = FakeRuntimeFactory(emitConsole = false)
    val invalidProgram = program().copy(
      modules = listOf(
        program().modules.single().copy(
          sourceMappings = listOf(
            DynamicGeneratedSourceMapping(
              generatedLine = 1,
              generatedColumn = 0,
              sourceLocation = DynamicSourceLocation("Main.java", DynamicTextRange(-1, 0)),
            ),
          ),
        ),
      ),
    )

    val failure = assertFailsWith<DynamicLanguageExecutionException> {
      DynamicExecutableProgramRunner { factory }.run(
        program = invalidProgram,
        arguments = emptyList(),
        config = JsRuntimeConfig(),
        maxOutputBytes = 1_024,
      )
    }

    assertTrue(failure.message.orEmpty().contains("source mapping"))
    assertTrue(factory.runtimes.isEmpty())
  }

  /** 非法、重复、越界或可注入的 Module 图必须全部在创建 Runtime 前失败。 */
  @Test
  fun rejectsMalformedModuleGraphsBeforeCreatingRuntime() = runTest {
    val valid = program()
    val invalidPrograms = listOf(
      valid.copy(modules = emptyList()),
      valid.copy(modules = valid.modules + valid.modules.single()),
      valid.copy(entryModuleName = "lesson/missing.mjs"),
      valid.copy(
        entryModuleName = "../escape.mjs",
        modules = listOf(valid.modules.single().copy(name = "../escape.mjs")),
      ),
      valid.copy(entryExportName = "runLesson();"),
      valid.copy(
        entryModuleName = "module-0.mjs",
        modules = List(257) { index ->
          DynamicExecutableModule(
            name = "module-$index.mjs",
            source = "export const value$index = $index;",
          )
        },
      ),
    )

    invalidPrograms.forEachIndexed { sample, invalidProgram ->
      val factory = FakeRuntimeFactory(emitConsole = false)
      val failure = assertFailsWith<DynamicLanguageExecutionException>(
        message = "Malformed Module graph sample $sample must be rejected.",
      ) {
        DynamicExecutableProgramRunner { factory }.run(
          program = invalidProgram,
          arguments = emptyList(),
          config = JsRuntimeConfig(),
          maxOutputBytes = 1_024,
        )
      }

      assertTrue(failure.message.orEmpty().contains("invalid executable Module graph"))
      assertTrue(factory.runtimes.isEmpty())
    }
  }

  /** 创建各测试共用的最小合法 Module 图。 */
  private fun program(): DynamicExecutableProgram = DynamicExecutableProgram(
    entryModuleName = "lesson/main.mjs",
    entryExportName = "runLesson",
    modules = listOf(
      DynamicExecutableModule(
        name = "lesson/main.mjs",
        source = "export function runLesson(){return 42;}",
      ),
    ),
  )

  /** 为测试记录执行器传入的 Module Loader 和缓存策略。 */
  private class FakeRuntimeFactory(
    private val emitConsole: Boolean = true,
    private val hostOutput: List<Pair<String, String>> = emptyList(),
    private val hostBase64Output: List<Pair<String, String>> = emptyList(),
    private val readStandardInput: Boolean = false,
    private val runtimeFailure: JsRuntimeException? = null,
  ) : JsRuntimeFactory {
    lateinit var options: JsRuntimeOptions
    val runtimes = mutableListOf<FakeRuntime>()
    val runtime: FakeRuntime get() = runtimes.last()

    override fun create(options: JsRuntimeOptions): JsRuntime {
      this.options = options
      return FakeRuntime(
        emitConsole = emitConsole,
        hostOutput = hostOutput,
        hostBase64Output = hostBase64Output,
        readStandardInput = readStandardInput,
        runtimeFailure = runtimeFailure,
        bridges = options.bridges,
      ).also(runtimes::add)
    }
  }

  /** 模拟持久上下文，只实现执行器本测试需要的基础值和 console 行为。 */
  private class FakeRuntime(
    private val emitConsole: Boolean,
    private val hostOutput: List<Pair<String, String>>,
    private val hostBase64Output: List<Pair<String, String>>,
    private val readStandardInput: Boolean,
    private val runtimeFailure: JsRuntimeException?,
    bridges: List<JsRuntimeBridge>,
  ) : JsRuntime {
    override var isClosed: Boolean = false
      private set
    private val hostFunctions = mutableMapOf<String, (Array<Any?>) -> Any?>()
    private var evaluationCount = 0
    var observedStandardInputUtf8Base64: String? = null
      private set

    init {
      bridges.forEach { bridge ->
        when (val binding = bridge.binding) {
          is JsRuntimeBridgeBinding.SyncFunction -> hostFunctions[bridge.name] = binding.block
          is JsRuntimeBridgeBinding.AsyncFunction ->
            error("Runner test does not expect asynchronous host bridges.")
          is JsRuntimeBridgeBinding.ObjectFunctions ->
            error("Runner must install the JavaScript output wrapper instead of host objects.")
        }
        bridge.onRuntimeReady(
          JsRuntimeCallback { code, filename, asModule -> evaluateValue(code, filename, asModule) },
        )
      }
    }

    override suspend fun evaluateValue(
      code: String,
      filename: String,
      asModule: Boolean,
    ): Any? {
      evaluationCount += 1
      return if (code == DynamicProgramHostAbi.OUTPUT_BRIDGE_SOURCE) {
        null
      } else if (asModule) {
        runtimeFailure?.let { throw it }
        if (emitConsole) {
          emitRawText(DynamicProgramHostAbi.WRITE_STANDARD_OUTPUT_UTF8_BASE64_CHUNK, "value 42\n")
        }
        hostOutput.forEach { (name, text) ->
          val hostName = when (name) {
            DynamicProgramHostAbi.WRITE_STANDARD_OUTPUT ->
              DynamicProgramHostAbi.WRITE_STANDARD_OUTPUT_UTF8_BASE64_CHUNK
            DynamicProgramHostAbi.WRITE_STANDARD_ERROR ->
              DynamicProgramHostAbi.WRITE_STANDARD_ERROR_UTF8_BASE64_CHUNK
            else -> name
          }
          emitRawText(hostName, text)
        }
        hostBase64Output.forEach { (name, payload) ->
          hostFunctions.getValue(name)(arrayOf(payload))
        }
        if (readStandardInput) {
          observedStandardInputUtf8Base64 = hostFunctions.getValue(
            DynamicProgramHostAbi.READ_STANDARD_INPUT_UTF8_BASE64,
          )(emptyArray()) as String
        }
        null
      } else {
        42
      }
    }

    override fun interruptEvaluation() = Unit

    override fun close() {
      isClosed = true
    }

    /** 测试桩模拟 JS 输出桥，以 Unicode 标量边界生成不超过 ABI 限制的 Base64 小块。 */
    private fun emitRawText(hostName: String, text: String) {
      val chunk = StringBuilder()
      var chunkBytes = 0
      var index = 0
      while (index < text.length) {
        val endExclusive = if (
          text[index].isHighSurrogate() &&
          index + 1 < text.length &&
          text[index + 1].isLowSurrogate()
        ) {
          index + 2
        } else {
          index + 1
        }
        val value = text.substring(index, endExclusive)
        val valueBytes = value.encodeToByteArray().size
        if (chunkBytes + valueBytes > DynamicProgramHostAbi.MAX_OUTPUT_UTF8_CHUNK_BYTES) {
          emitChunk(hostName, chunk.toString())
          chunk.clear()
          chunkBytes = 0
        }
        chunk.append(value)
        chunkBytes += valueBytes
        index = endExclusive
      }
      if (chunk.isNotEmpty()) emitChunk(hostName, chunk.toString())
    }

    /** 把一个受控测试文本块编码成实际宿主 ABI 接收的 ASCII payload。 */
    private fun emitChunk(hostName: String, text: String) {
      hostFunctions.getValue(hostName)(arrayOf(Base64.encode(text.encodeToByteArray())))
    }
  }
}
