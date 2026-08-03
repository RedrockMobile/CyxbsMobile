package com.cyxbs.functions.code.js.quickjs

import com.cyxbs.functions.code.js.runtime.JsRuntime
import com.cyxbs.functions.code.js.runtime.JsRuntimeErrorKind
import com.cyxbs.functions.code.js.runtime.JsRuntimeException
import com.cyxbs.functions.code.js.runtime.JsRuntimeConfig
import com.cyxbs.functions.code.js.runtime.JsModuleLoader
import com.cyxbs.functions.code.js.runtime.JsRuntimeOptions
import com.cyxbs.functions.code.js.quickjs.internal.QuickJsModuleContent
import com.cyxbs.functions.code.js.quickjs.internal.QuickJsModuleLoader
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.QuickJsInterruptedException
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import com.dokar.quickjs.ModuleContent as EngineModuleContent
import com.dokar.quickjs.ModuleLoader as EngineModuleLoader
import com.dokar.quickjs.ModuleNormalizer as EngineModuleNormalizer

/**
 * quickjs-kt 的内部 Runtime 适配器。
 *
 * 该类型不会暴露给业务；它负责把公共 Runtime 契约、Module 内容和稳定异常转换到 QuickJS API。
 */
internal class QuickJsRuntime @Throws(JsRuntimeException::class) constructor(
  options: JsRuntimeOptions,
  internalModuleLoader: QuickJsModuleLoader? = null,
) : JsRuntime {

  /** 供实现模块内部流程与测试直接创建启用字节码能力的 Runtime。 */
  internal constructor(
    jobDispatcher: CoroutineDispatcher = Dispatchers.Default,
    config: JsRuntimeConfig = JsRuntimeConfig(),
    moduleLoader: JsModuleLoader? = null,
  ) : this(
    JsRuntimeOptions(
      jobDispatcher = jobDispatcher,
      config = config,
      moduleLoader = moduleLoader,
    ),
  )

  /** 供 QuickJS Module 缓存流程和对应测试注入内部混合 Loader。 */
  internal constructor(
    internalModuleLoader: QuickJsModuleLoader,
    jobDispatcher: CoroutineDispatcher = Dispatchers.Default,
    config: JsRuntimeConfig = JsRuntimeConfig(),
  ) : this(
    options = JsRuntimeOptions(
      jobDispatcher = jobDispatcher,
      config = config,
    ),
    internalModuleLoader = internalModuleLoader,
  )

  private val engine: QuickJs = runQuickJsOperation {
    val moduleLoader = internalModuleLoader ?: options.moduleLoader?.let { sourceLoader ->
      object : QuickJsModuleLoader {
        override val normalizer = sourceLoader.normalizer

        override fun load(name: String): QuickJsModuleContent? {
          return sourceLoader.load(name)?.let(QuickJsModuleContent::Source)
        }
      }
    }
    if (moduleLoader == null) {
      QuickJs.create(options.jobDispatcher)
    } else {
      QuickJs.create(
        jobDispatcher = options.jobDispatcher,
        moduleLoader = object : EngineModuleLoader {
          override val normalizer: EngineModuleNormalizer? =
            moduleLoader.normalizer?.let { normalizer ->
              EngineModuleNormalizer { baseName, requestedName ->
                normalizer.normalize(baseName = baseName, requestedName = requestedName)
              }
            }

          override fun load(name: String): EngineModuleContent? {
            return when (val content = moduleLoader.load(name)) {
              is QuickJsModuleContent.Source -> EngineModuleContent.Source(content.code)
              is QuickJsModuleContent.Bytecode -> EngineModuleContent.Bytecode(content.bytes)
              null -> null
            }
          }

          override fun onCompiled(name: String, bytecode: ByteArray) {
            moduleLoader.onCompiled(name = name, bytecode = bytecode)
          }

          override fun onLoadFailed(name: String) {
            moduleLoader.onLoadFailed(name)
          }
        },
      )
    }.apply {
      memoryLimit = options.config.memoryLimitBytes
      maxStackSize = options.config.maxStackSizeBytes
      evaluationTimeoutMillis = options.config.evaluationTimeoutMillis
    }
  }

  internal val engineVersion: String
    get() = engine.version

  override val isClosed: Boolean
    get() = engine.isClosed

  /** 使用 QuickJS 执行源码，并在模块边界内完成异常转换。 */
  @Throws(JsRuntimeException::class, CancellationException::class)
  override suspend fun evaluateValue(
    code: String,
    filename: String,
    asModule: Boolean,
  ): Any? {
    return runQuickJsOperation {
      engine.evaluate<Any?>(code = code, filename = filename, asModule = asModule)
    }
  }

  /** 从任意线程向 QuickJS 请求中断当前执行。 */
  @Throws(JsRuntimeException::class)
  override fun interruptEvaluation() {
    runQuickJsOperation { engine.interruptEvaluation() }
  }

  /** 注册同步顶层函数。 */
  @Throws(JsRuntimeException::class)
  override fun bindFunction(name: String, block: (args: Array<Any?>) -> Any?) {
    runQuickJsOperation { engine.function(name = name, block = block) }
  }

  /** 注册由同步函数组成的命名空间对象。 */
  @Throws(IllegalArgumentException::class, JsRuntimeException::class)
  override fun bindObjectFunctions(
    name: String,
    functions: Map<String, (args: Array<Any?>) -> Any?>,
  ) {
    require(name.isNotBlank()) { "Object binding name must not be blank." }
    require(functions.isNotEmpty()) { "Object binding functions must not be empty." }
    require(functions.keys.all { it.isNotBlank() }) {
      "Object binding function names must not be blank."
    }
    runQuickJsOperation {
      engine.define(name) {
        functions.forEach { (functionName, block) ->
          function(functionName) { args -> block(args) }
        }
      }
    }
  }

  /** 注册返回 Promise 的异步宿主函数。 */
  @Throws(JsRuntimeException::class)
  override fun bindAsyncFunction(
    name: String,
    block: suspend (args: Array<Any?>) -> Any?,
  ) {
    runQuickJsOperation { engine.asyncFunction(name = name, block = block) }
  }

  /** 把源码编译为当前 QuickJS 版本的实现相关字节码。 */
  @Throws(JsRuntimeException::class)
  internal fun compile(
    code: String,
    filename: String = JsRuntime.DEFAULT_FILENAME,
    asModule: Boolean = false,
  ): ByteArray {
    return runQuickJsOperation {
      engine.compile(code = code, filename = filename, asModule = asModule)
    }
  }

  /** 在不执行顶层代码的情况下解析并编译静态 Module 依赖。 */
  @Throws(JsRuntimeException::class)
  internal fun resolveModuleGraph(entryBytecode: ByteArray): Set<String> {
    return runQuickJsOperation { engine.resolveModuleGraph(entryBytecode) }
  }

  /** 执行当前 QuickJS 版本生成的字节码。 */
  @Throws(JsRuntimeException::class, CancellationException::class)
  internal suspend fun evaluateBytecodeValue(bytecode: ByteArray): Any? {
    return runQuickJsOperation { engine.evaluate<Any?>(bytecode) }
  }

  /** 仅供实现模块测试保留 quickjs-kt 的具体目标类型转换能力。 */
  internal suspend inline fun <reified T> evaluate(bytecode: ByteArray): T {
    return runQuickJsOperation { engine.evaluate<T>(bytecode) }
  }

  /** 释放 QuickJS Runtime、Context、Module 与未完成异步任务。 */
  @Throws(JsRuntimeException::class)
  override fun close() {
    runQuickJsOperation { engine.close() }
  }

  private companion object {
    const val ENGINE_ID = "quickjs"
  }
}

/** 把 QuickJS 异常转换为稳定 Runtime 异常，同时保留宿主回调抛出的原始异常。 */
private inline fun <T> runQuickJsOperation(block: () -> T): T {
  return try {
    block()
  } catch (exception: QuickJsException) {
    throw exception.toJsRuntimeException()
  }
}

/** 复制可供业务判断的稳定字段，QuickJS 异常只保留在 cause 链中。 */
private fun QuickJsException.toJsRuntimeException(): JsRuntimeException {
  val rawStack = stack
  val stableMessage = if (rawStack.isNullOrEmpty()) {
    message.orEmpty()
  } else {
    message.orEmpty().removeSuffix("\n$rawStack")
  }.ifBlank { "JavaScript execution failed." }
  val kind = when {
    this is QuickJsInterruptedException -> JsRuntimeErrorKind.INTERRUPTED
    stableMessage.substringBefore(':') == "SyntaxError" -> JsRuntimeErrorKind.SYNTAX_ERROR
    stableMessage.contains("could not load module", ignoreCase = true) ->
      JsRuntimeErrorKind.MODULE_RESOLUTION_ERROR
    else -> JsRuntimeErrorKind.RUNTIME_ERROR
  }
  return JsRuntimeException(
    kind = kind,
    message = stableMessage,
    fileName = fileName,
    lineNumber = lineNumber,
    columnNumber = columnNumber,
    jsStack = rawStack,
    cause = this,
  )
}
