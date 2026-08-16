package com.cyxbs.functions.code.language.internal

import com.cyxbs.functions.code.language.DynamicLanguageExecutionException
import com.cyxbs.functions.code.language.js.bridge.DynamicExecutableProgram
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

/** 用户程序执行器内部的稳定结果。 */
internal data class DynamicProgramExecution(
  val returnValue: JsonElement,
  val standardOutput: String,
  val standardError: String,
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
   * 宿主入口使用顶层 await，同时兼容同步返回值和 Promise；交互式标准输入和线程 Runtime 池
   * 将在对应运行协议扩展后接入，不能在这里隐式猜测语言语义。
   */
  suspend fun run(
    program: DynamicExecutableProgram,
    arguments: List<JsonElement>,
    config: JsRuntimeConfig,
  ): DynamicProgramExecution {
    validateProgram(program)
    val runtimeFactory = runtimeFactoryProvider()
      ?: throw DynamicLanguageExecutionException(
        "No JavaScript Runtime implementation is installed for dynamic program execution.",
      )
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
      return execute(runtime, program, arguments)
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

  /** 在已经创建的 Runtime 中注册最小宿主能力并调用生成入口。 */
  private suspend fun execute(
    runtime: JsRuntime,
    program: DynamicExecutableProgram,
    arguments: List<JsonElement>,
  ): DynamicProgramExecution {
    val standardOutput = StringBuilder()
    val standardError = StringBuilder()
    runtime.bindObjectFunctions(
      name = "console",
      functions = mapOf(
        "log" to { args -> standardOutput.appendConsoleLine(args); null },
        "info" to { args -> standardOutput.appendConsoleLine(args); null },
        "warn" to { args -> standardError.appendConsoleLine(args); null },
        "error" to { args -> standardError.appendConsoleLine(args); null },
      ),
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
      standardOutput = standardOutput.toString(),
      standardError = standardError.toString(),
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

  /** 使用 JavaScript console 的空格连接规则追加一行基础输出。 */
  private fun StringBuilder.appendConsoleLine(args: Array<Any?>) {
    append(args.joinToString(separator = " ") { argument -> argument?.toString() ?: "null" })
    append('\n')
  }

  private companion object {
    val JS_IDENTIFIER = Regex("[A-Za-z_$][A-Za-z0-9_$]*")
    const val INVALID_MODULE_PREFIX = "__cyxbs_invalid_module__/"
  }
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
