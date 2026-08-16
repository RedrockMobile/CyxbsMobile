package com.cyxbs.functions.code.language.internal

import com.cyxbs.functions.code.language.DynamicLanguageExecutionException
import com.cyxbs.functions.code.language.DynamicProgramOutputChannel
import com.cyxbs.functions.code.language.DynamicProgramOutputEvent
import com.cyxbs.functions.code.language.DynamicProgramOutputSink
import com.cyxbs.functions.code.language.DynamicProgramRunOptions
import com.cyxbs.functions.code.language.js.bridge.DynamicExecutableProgram
import com.cyxbs.functions.code.language.js.bridge.DynamicProgramHostAbi
import com.cyxbs.functions.code.js.runtime.JsModuleLoader
import com.cyxbs.functions.code.js.runtime.JsModuleNormalizer
import com.cyxbs.functions.code.js.runtime.JsRuntime
import com.cyxbs.functions.code.js.runtime.JsRuntimeConfig
import com.cyxbs.functions.code.js.runtime.JsRuntimeException
import com.cyxbs.functions.code.js.runtime.JsRuntimeFactory
import com.cyxbs.functions.code.js.runtime.create
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64

/** 用户程序执行器内部的稳定结果。 */
internal data class DynamicProgramExecution(
  val returnValue: JsonElement,
  val standardOutput: String,
  val standardError: String,
  val outputTruncated: Boolean,
  val droppedOutputBytes: Long,
)

/**
 * 在短生命周期 JavaScript Runtime 中执行语言包生成的 Module 图。
 *
 * 本类只接受内存中的完整源码，不在 Module Loader 回调期间访问网络或磁盘。每次调用 [run] 都
 * 创建并释放 Runtime，分析和编译 Service 因此不会接触用户代码的全局状态。
 */
internal class DynamicExecutableProgramRunner(
  private val runtimeFactoryProvider: () -> JsRuntimeFactory?,
) {

  /**
   * 加载程序、调用统一入口并收集 JSON 返回值与 console 输出。
   *
   * 宿主入口使用顶层 await，同时兼容同步返回值和 Promise。[standardInput] 只提供运行前预加载的
   * 同步快照；程序执行期间等待用户继续输入的交互模式尚未接入，不能在这里阻塞 Runtime 线程。
   */
  suspend fun run(
    program: DynamicExecutableProgram,
    arguments: List<JsonElement>,
    config: JsRuntimeConfig,
    outputSink: DynamicProgramOutputSink? = null,
    maxOutputBytes: Long,
    standardInput: String = "",
    maxInputBytes: Long = DynamicProgramRunOptions.DEFAULT_MAX_INPUT_BYTES,
  ): DynamicProgramExecution {
    validateProgram(program)
    validateStandardInput(standardInput, maxInputBytes)
    val runtimeFactory = runtimeFactoryProvider()
      ?: throw DynamicLanguageExecutionException(
        "No JavaScript Runtime implementation is installed for dynamic program execution.",
      )
    // 先确认引擎可用；输入已通过配额校验，之后的临时 ByteArray/Base64 才是有界且必要的。
    val standardInputUtf8Base64 = Base64.encode(standardInput.encodeToByteArray())
    val loader = InMemoryProgramModuleLoader(program)
    val runtime = try {
      runtimeFactory.create(
        config = config,
        moduleLoader = loader,
        // 教学程序每次使用新 Runtime，跨次字节码缓存不会复用运行状态且会增加失效复杂度。
        allowBytecodeCache = false,
      )
    } catch (exception: JsRuntimeException) {
      throw DynamicLanguageExecutionException(
        message = "Unable to create the isolated dynamic program Runtime.",
        cause = exception,
      )
    }

    var primaryFailure: Throwable? = null
    try {
      return execute(
        runtime,
        program,
        arguments,
        outputSink,
        maxOutputBytes,
        standardInputUtf8Base64,
      )
    } catch (exception: CancellationException) {
      primaryFailure = exception
      throw exception
    } catch (exception: DynamicLanguageExecutionException) {
      primaryFailure = exception
      throw exception
    } catch (exception: JsRuntimeException) {
      primaryFailure = exception
      throw DynamicLanguageExecutionException(
        message = "Dynamic program execution failed.",
        cause = exception,
      )
    } catch (exception: Throwable) {
      primaryFailure = exception
      throw DynamicLanguageExecutionException(
        message = "Dynamic program execution failed at the host value boundary.",
        cause = exception,
      )
    } finally {
      try {
        runtime.close()
      } catch (closeFailure: JsRuntimeException) {
        // 已有执行异常时保留原始失败；成功路径上的释放失败仍需要明确通知调用方。
        if (primaryFailure == null) {
          throw DynamicLanguageExecutionException(
            message = "Dynamic program Runtime could not be released cleanly.",
            cause = closeFailure,
          )
        }
      }
    }
  }

  /** 在已经创建的 Runtime 中注册最小宿主能力、安装 ASCII 安全桥并调用生成入口。 */
  private suspend fun execute(
    runtime: JsRuntime,
    program: DynamicExecutableProgram,
    arguments: List<JsonElement>,
    outputSink: DynamicProgramOutputSink?,
    maxOutputBytes: Long,
    standardInputUtf8Base64: String,
  ): DynamicProgramExecution {
    val output = DynamicProgramOutputCollector(outputSink, maxOutputBytes)
    runtime.bindFunction(DynamicProgramHostAbi.WRITE_STANDARD_OUTPUT_UTF8_BASE64_CHUNK) { args ->
      output.appendUtf8Base64Chunk(DynamicProgramOutputChannel.STANDARD_OUTPUT, args)
      null
    }
    runtime.bindFunction(DynamicProgramHostAbi.WRITE_STANDARD_ERROR_UTF8_BASE64_CHUNK) { args ->
      output.appendUtf8Base64Chunk(DynamicProgramOutputChannel.STANDARD_ERROR, args)
      null
    }
    runtime.bindFunction(DynamicProgramHostAbi.READ_STANDARD_INPUT_UTF8_BASE64) { args ->
      require(args.isEmpty()) {
        "Dynamic program standard input bridge does not accept arguments."
      }
      standardInputUtf8Base64
    }
    // 原始 Unicode 只在 JS 内分块编码，宿主 binding 永远只接收受控大小的 ASCII Base64。
    runtime.evaluateValue(
      code = DynamicProgramHostAbi.OUTPUT_BRIDGE_SOURCE,
      filename = "__cyxbs_dynamic_output_bridge__.js",
      asModule = false,
    )

    val bootstrapName = uniqueBootstrapName(program)
    val resultKey = "__cyxbs_dynamic_program_result__"
    val entryModule = JsonPrimitive(program.entryModuleName).toString()
    val argumentSource = JsonArray(arguments).joinToString(separator = ",")
    val bootstrapSource = """
      import { ${program.entryExportName} as __cyxbs_entry__ } from $entryModule;
      globalThis.$resultKey = await __cyxbs_entry__($argumentSource);
    """.trimIndent()
    runtime.evaluateValue(
      code = bootstrapSource,
      filename = bootstrapName,
      asModule = true,
    )
    val value = runtime.evaluateValue(
      code = "globalThis.$resultKey",
      filename = "__cyxbs_dynamic_result__.js",
      asModule = false,
    )
    return DynamicProgramExecution(
      returnValue = value.toJsonElement(),
      standardOutput = output.standardOutput,
      standardError = output.standardError,
      outputTruncated = output.isTruncated,
      droppedOutputBytes = output.droppedBytes,
    )
  }

  /** 在进入具体引擎前拒绝损坏或带注入风险的语言包产物。 */
  private fun validateProgram(program: DynamicExecutableProgram) {
    val names = program.modules.map { module -> module.name }
    if (
      names.isEmpty() ||
      names.distinct().size != names.size ||
      program.entryModuleName !in names ||
      names.any { name -> !name.isSafeModuleName() } ||
      !program.entryExportName.matches(JS_IDENTIFIER)
    ) {
      throw DynamicLanguageExecutionException(
        "The language package returned an invalid executable Module graph.",
      )
    }
  }

  /**
   * 在创建隔离 Runtime 前验证预加载输入大小，超限时明确失败而不改变输入内容。
   *
   * 扫描 UTF-16 code unit 直接计算 UTF-8 大小，达到上限后立即停止，避免为可能很大的输入再分配
   * 一份完整 ByteArray。孤立代理会在不同平台编码为不同 replacement，因此在进入 Runtime 前拒绝。
   */
  private fun validateStandardInput(standardInput: String, maxInputBytes: Long) {
    require(maxInputBytes >= 0) { "maxInputBytes must not be negative." }
    when (standardInput.validateUtf8WithinLimit(maxInputBytes)) {
      StandardInputValidation.VALID -> Unit
      StandardInputValidation.UNPAIRED_SURROGATE -> throw DynamicLanguageExecutionException(
        "Dynamic program standard input contains an unpaired UTF-16 surrogate.",
      )
      StandardInputValidation.TOO_LARGE -> throw DynamicLanguageExecutionException(
        "Dynamic program standard input exceeds the configured UTF-8 byte limit of $maxInputBytes bytes.",
      )
    }
  }

  /** Module 名称必须是非空、正斜杠分隔且不会越出内存图根目录的逻辑路径。 */
  private fun String.isSafeModuleName(): Boolean {
    if (isEmpty() || startsWith('/') || contains('\\') || startsWith(INVALID_MODULE_PREFIX)) {
      return false
    }
    return split('/').all { segment -> segment.isNotEmpty() && segment != "." && segment != ".." }
  }

  /** 生成不会覆盖语言包 Module 的宿主入口名称。 */
  private fun uniqueBootstrapName(program: DynamicExecutableProgram): String {
    val names = program.modules.mapTo(mutableSetOf()) { module -> module.name }
    return generateSequence("__cyxbs_dynamic_bootstrap__.mjs") { current -> "_" + current }
      .first { candidate -> candidate !in names }
  }

  /** 把 Runtime 返回的稳定基础值递归转换为跨平台 JSON 值。 */
  private fun Any?.toJsonElement(): JsonElement = when (this) {
    null, Unit -> JsonNull
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is String -> JsonPrimitive(this)
    is List<*> -> JsonArray(map { value -> value.toJsonElement() })
    is Array<*> -> JsonArray(map { value -> value.toJsonElement() })
    is Map<*, *> -> JsonObject(
      entries.associate { (key, value) ->
        val stringKey = key as? String ?: throw DynamicLanguageExecutionException(
          "Dynamic program returned an object with a non-string key.",
        )
        stringKey to value.toJsonElement()
      },
    )
    else -> throw DynamicLanguageExecutionException(
      "Dynamic program returned an unsupported host value: ${this::class.simpleName}.",
    )
  }

  private companion object {
    val JS_IDENTIFIER = Regex("[A-Za-z_$][A-Za-z0-9_$]*")
    const val INVALID_MODULE_PREFIX = "__cyxbs_invalid_module__/"
  }
}

/**
 * 在单次执行内聚合控制台和语言 intrinsic 输出，并限制其占用的 UTF-8 字节数。
 *
 * 输出超过上限时仅丢弃后续文本、不在同步 binding 内抛出异常，避免把资源限制错误伪装成 JavaScript
 * 异常。只有已被完整或部分接受的文本才会通知 [sink]，从而使 UI 的流式显示与最终结果保持一致。
 */
private class DynamicProgramOutputCollector(
  private val sink: DynamicProgramOutputSink?,
  private val maxBytes: Long,
) {
  private val output = StringBuilder()
  private val error = StringBuilder()
  private var acceptedBytes: Long = 0

  /**
   * 是否仍允许接收后续输出。
   *
   * 首次遇到无法完整保留的 Unicode 标量后必须永久关闭接收，否则下一次宿主调用中的较小字符
   * 可能越过已丢弃字符被保存，最终结果就不再是完整输出流的连续前缀。
   */
  private var isAcceptingOutput: Boolean = true

  /** 是否至少有一个输出片段因总上限被截断。 */
  var isTruncated: Boolean = false
    private set

  /** 被截断、未保存且未通知 UI 的 UTF-8 字节数。 */
  var droppedBytes: Long = 0
    private set

  val standardOutput: String
    get() = output.toString()

  val standardError: String
    get() = error.toString()

  /**
   * 严格解码一个 JS 输出桥生成的 UTF-8 Base64 小块并追加到对应标准流。
   *
   * 长度、字符集、padding 和 decoded 大小均在分配 ByteArray 前验证；恶意脚本即使绕过 JS 包装器
   * 直接调用私有 ABI，也不能迫使宿主为超大 payload 再创建同量级副本。
   */
  fun appendUtf8Base64Chunk(channel: DynamicProgramOutputChannel, args: Array<Any?>) {
    require(args.size == 1) {
      "Dynamic program output chunk bridge accepts exactly one argument."
    }
    val payload = args.single() as? String ?: throw IllegalArgumentException(
      "Dynamic program output chunk bridge accepts only an ASCII Base64 string.",
    )
    payload.validateOutputBase64Chunk()
    val text = Base64.decode(payload).decodeToString(throwOnInvalidSequence = true)
    appendRaw(channel, text)
  }

  /** 追加语言后端已完成格式化的一段原始标准流文本。 */
  fun appendRaw(channel: DynamicProgramOutputChannel, text: String) {
    if (text.isEmpty()) return
    val remainingBytes = if (isAcceptingOutput) {
      (maxBytes - acceptedBytes).coerceAtLeast(0)
    } else {
      0
    }
    val measurement = text.measureUtf8(remainingBytes)
    if (measurement.acceptedBytes < measurement.totalBytes) {
      isTruncated = true
      isAcceptingOutput = false
      droppedBytes += measurement.totalBytes - measurement.acceptedBytes
    }
    val acceptedText = text.substring(0, measurement.acceptedEndExclusive)
    if (acceptedText.isEmpty()) return

    acceptedBytes += measurement.acceptedBytes
    when (channel) {
      DynamicProgramOutputChannel.STANDARD_OUTPUT -> output.append(acceptedText)
      DynamicProgramOutputChannel.STANDARD_ERROR -> error.append(acceptedText)
    }
    try {
      sink?.write(DynamicProgramOutputEvent(channel = channel, text = acceptedText))
    } catch (exception: Throwable) {
      // outputSink 是同步宿主回调；它主动抛出的 CancellationException 也不是执行协程的取消信号。
      throw DynamicLanguageExecutionException(
        message = "Dynamic program output callback failed.",
        cause = exception,
      )
    }
  }

  /**
   * 一次扫描同时统计完整文本的 UTF-8 字节数和可保留前缀。
   *
   * 不能对完整 [String] 调用 `encodeToByteArray()`：脚本即使只有很小的输出额度，仍可能传入超大
   * 文本，额外分配同等大小 ByteArray 会绕过输出配额造成 OOM。孤立高/低代理按 UTF-8 编码器的
   * replacement character 规则计为 3 字节并原样保留，合法代理对始终作为 4 字节整体处理。
   */
  private fun String.measureUtf8(maxAcceptedBytes: Long): Utf8Measurement {
    var index = 0
    var acceptedEndExclusive = 0
    var acceptedBytes = 0L
    var totalBytes = 0L
    while (index < length) {
      val endExclusive = if (
        this[index].isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()
      ) {
        index + 2
      } else {
        index + 1
      }
      val characterBytes = utf8ByteCount(index, endExclusive)
      totalBytes += characterBytes
      // 前缀一旦遇到放不下的字符，后续即使较小也不能越过它被接受。
      if (acceptedEndExclusive == index && acceptedBytes + characterBytes <= maxAcceptedBytes) {
        acceptedBytes += characterBytes
        acceptedEndExclusive = endExclusive
      }
      index = endExclusive
    }
    return Utf8Measurement(
      totalBytes = totalBytes,
      acceptedBytes = acceptedBytes,
      acceptedEndExclusive = acceptedEndExclusive,
    )
  }

  /** 返回一个完整 Unicode 标量或孤立 UTF-16 代理在 Kotlin UTF-8 编码中的字节数。 */
  private fun String.utf8ByteCount(start: Int, endExclusive: Int): Long {
    if (endExclusive - start == 2) return 4L
    val codeUnit = this[start].code
    return when {
      codeUnit <= 0x7F -> 1L
      codeUnit <= 0x7FF -> 2L
      // 孤立代理会被 Kotlin UTF-8 编码替换为 U+FFFD，和普通三字节字符一致。
      else -> 3L
    }
  }

  /** 一段文本的总 UTF-8 大小及不超额度的 UTF-16 前缀位置。 */
  private data class Utf8Measurement(
    val totalBytes: Long,
    val acceptedBytes: Long,
    val acceptedEndExclusive: Int,
  )
}

/**
 * 在分配解码 ByteArray 前验证输出块的严格 Base64 表达。
 *
 * 除字符集和 padding 位置外还验证尾部未使用 bit 为 0，拒绝同一字节序列的非 canonical 编码。
 */
private fun String.validateOutputBase64Chunk() {
  val maxDecodedBytes = DynamicProgramHostAbi.MAX_OUTPUT_UTF8_CHUNK_BYTES
  val maxEncodedChars = ((maxDecodedBytes + 2) / 3) * 4
  require(length <= maxEncodedChars && length % 4 == 0) {
    "Dynamic program output chunk exceeds the Base64 bridge limit."
  }
  if (isEmpty()) return

  val padding = when {
    endsWith("==") -> 2
    endsWith('=') -> 1
    else -> 0
  }
  val decodedByteCount = length / 4 * 3 - padding
  require(decodedByteCount <= maxDecodedBytes) {
    "Dynamic program output chunk exceeds the decoded bridge limit."
  }
  forEachIndexed { index, character ->
    val isPadding = character == '='
    require(
      if (isPadding) index >= length - padding else character.base64Value() >= 0,
    ) { "Dynamic program output chunk is not strict Base64." }
  }
  if (padding == 2) {
    require(this[length - 3].base64Value() and 0x0F == 0) {
      "Dynamic program output chunk has non-zero unused Base64 bits."
    }
  } else if (padding == 1) {
    require(this[length - 2].base64Value() and 0x03 == 0) {
      "Dynamic program output chunk has non-zero unused Base64 bits."
    }
  }
}

/** 返回严格 Base64 字符的六位值；非 Base64 字符返回 -1。 */
private fun Char.base64Value(): Int = when (this) {
  in 'A'..'Z' -> code - 'A'.code
  in 'a'..'z' -> code - 'a'.code + 26
  in '0'..'9' -> code - '0'.code + 52
  '+' -> 62
  '/' -> 63
  else -> -1
}

/**
 * 同时验证 UTF-16 标量并判断 UTF-8 编码是否不超过 [maxBytes]。
 *
 * 扫描遇到超限便立即返回，不创建与输入等大的临时字节数组；孤立代理会单独报告，避免平台编码器
 * replacement 行为不一致。
 */
private fun String.validateUtf8WithinLimit(maxBytes: Long): StandardInputValidation {
  var byteCount = 0L
  var index = 0
  while (index < length) {
    val characterBytes = when {
      this[index].isHighSurrogate() -> {
        if (index + 1 >= length || !this[index + 1].isLowSurrogate()) {
          return StandardInputValidation.UNPAIRED_SURROGATE
        }
        index += 2
        4L
      }
      this[index].isLowSurrogate() -> return StandardInputValidation.UNPAIRED_SURROGATE
      this[index].code <= 0x7F -> {
        index += 1
        1L
      }
      this[index].code <= 0x7FF -> {
        index += 1
        2L
      }
      else -> {
        index += 1
        3L
      }
    }
    if (byteCount > maxBytes - characterBytes) return StandardInputValidation.TOO_LARGE
    byteCount += characterBytes
  }
  return StandardInputValidation.VALID
}

/** 预加载输入在创建 Runtime 前的无分配校验结果。 */
private enum class StandardInputValidation {
  VALID,
  TOO_LARGE,
  UNPAIRED_SURROGATE,
}

/**
 * 为单次执行提供纯内存 ES Module 加载与相对路径解析。
 *
 * Loader 回调可能持有引擎锁，因此这里只做 Map 查询和路径折叠，不进入协程、网络或文件系统。
 */
private class InMemoryProgramModuleLoader(
  program: DynamicExecutableProgram,
) : JsModuleLoader {
  private val modules = program.modules.associate { module -> module.name to module.source }

  override val normalizer = JsModuleNormalizer { baseName, requestedName ->
    normalizeModuleName(baseName, requestedName)
  }

  /** 返回标准化名称对应的完整源码，缺失依赖交给 Runtime 报告 Module 错误。 */
  override fun load(name: String): String? = modules[name]

  /** 解析 `./` 与 `../`，裸名称和语言包生成的稳定名称保持原样。 */
  private fun normalizeModuleName(baseName: String, requestedName: String): String {
    if (!requestedName.startsWith("./") && !requestedName.startsWith("../")) {
      return requestedName
    }
    val baseDirectory = baseName.substringBeforeLast('/', missingDelimiterValue = "")
    val segments = ArrayDeque<String>()
    var escapedRoot = false
    (baseDirectory + "/" + requestedName).split('/').forEach { segment ->
      when (segment) {
        "", "." -> Unit
        ".." -> if (segments.isNotEmpty()) {
          segments.removeLast()
        } else {
          escapedRoot = true
        }
        else -> segments.addLast(segment)
      }
    }
    return if (escapedRoot) {
      "__cyxbs_invalid_module__/outside-workspace"
    } else {
      segments.joinToString("/")
    }
  }
}
