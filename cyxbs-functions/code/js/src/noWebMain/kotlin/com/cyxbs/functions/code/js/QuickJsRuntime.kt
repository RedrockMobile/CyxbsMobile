package com.cyxbs.functions.code.js

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import com.dokar.quickjs.ModuleContent as QuickJsModuleContent
import com.dokar.quickjs.ModuleLoader as QuickJsModuleLoader

/**
 * QuickJS 运行时的资源与执行限制。
 *
 * @param memoryLimitBytes 单个运行时允许使用的最大内存，非正数表示不限制。
 * @param maxStackSizeBytes JavaScript 栈空间上限，必须大于 0。
 * @param evaluationTimeoutMillis 单次 JavaScript 执行超时；设为 0 表示不限制。
 */
data class QuickJsRuntimeConfig(
  val memoryLimitBytes: Long = 32L * 1024L * 1024L,
  val maxStackSizeBytes: Long = 256L * 1024L,
  val evaluationTimeoutMillis: Long = 5_000L,
) {

  init {
    require(maxStackSizeBytes > 0) { "maxStackSizeBytes must be greater than 0." }
    require(evaluationTimeoutMillis >= 0) {
      "evaluationTimeoutMillis must not be negative."
    }
  }
}

/**
 * 面向 Android、iOS 与 Desktop 的 QuickJS 运行时封装。
 *
 * 该类维护一个长生命周期的 JavaScript 上下文，适合连续执行代码、注册宿主函数和加载 ES Module。
 * 实例不再使用时必须调用 [close]，关闭后不可继续执行或注册能力。当前依赖版本不会自动解包入口
 * 表达式直接返回的 Promise，调用方应在源码中使用顶层 `await` 获取最终值。单次执行受
 * [QuickJsRuntimeConfig.evaluationTimeoutMillis] 限制，也可以通过 [interruptEvaluation] 主动中断。
 *
 * @param jobDispatcher 执行异步宿主函数的协程调度器。
 * @param config 运行时资源与执行限制。
 * @param moduleLoader 当前 Runtime 使用的 ES Module 加载器；不使用 Module 时可以为空。
 */
class QuickJsRuntime(
  jobDispatcher: CoroutineDispatcher = Dispatchers.Default,
  config: QuickJsRuntimeConfig = QuickJsRuntimeConfig(),
  moduleLoader: JsModuleLoader? = null,
) {

  /**
   * 供内联泛型执行方法访问的底层实例；模块外调用方不应直接依赖该对象。
   */
  @PublishedApi
  internal val engine: QuickJs = if (moduleLoader == null) {
    QuickJs.create(jobDispatcher)
  } else {
    QuickJs.create(
      jobDispatcher = jobDispatcher,
      moduleLoader = object : QuickJsModuleLoader {
        override fun load(name: String): QuickJsModuleContent? {
          return when (val content = moduleLoader.load(name)) {
            is JsModuleContent.Source -> QuickJsModuleContent.Source(content.code)
            is JsModuleContent.Bytecode -> QuickJsModuleContent.Bytecode(content.bytes)
            null -> null
          }
        }

        override fun onCompiled(name: String, bytecode: ByteArray) {
          moduleLoader.onCompiled(name = name, bytecode = bytecode)
        }
      },
    )
  }.apply {
    memoryLimit = config.memoryLimitBytes
    maxStackSize = config.maxStackSizeBytes
    evaluationTimeoutMillis = config.evaluationTimeoutMillis
  }

  /**
   * 当前嵌入的 QuickJS 版本，用于记录运行环境和定位动态代码兼容问题。
   */
  val engineVersion: String
    get() = engine.version

  /**
   * 运行时是否已经释放。
   */
  val isClosed: Boolean
    get() = engine.isClosed

  /**
   * 主动中断正在执行的 JavaScript。
   *
   * 可从其他线程安全调用；没有执行任务时调用不会产生副作用。被中断的执行会抛出
   * `QuickJsInterruptedException`，调用方可将其映射为超时或用户取消状态。
   */
  fun interruptEvaluation() {
    engine.interruptEvaluation()
  }

  /**
   * 执行 JavaScript 源码并把结果转换为 [T]。
   *
   * @param code 待执行的 JavaScript 源码。
   * @param filename 用于异常堆栈、相对 import 和后续 Source Map 映射的逻辑文件名。
   * @param asModule 是否按 ES Module 解析；Module 依赖由构造时传入的 [JsModuleLoader] 提供。
   * 如果入口表达式返回 Promise，源码必须显式使用顶层 `await`，否则无法直接映射为 [T]。
   * @return JavaScript 表达式的结果，或显式 `await` 后的 Promise 完成值。
   */
  suspend inline fun <reified T> evaluate(
    code: String,
    filename: String = DEFAULT_FILENAME,
    asModule: Boolean = false,
  ): T {
    return engine.evaluate(code = code, filename = filename, asModule = asModule)
  }

  /**
   * 将 ES Module 源码加入旧版预执行队列。
   *
   * 新代码应在创建 Runtime 时传入 [JsModuleLoader]，从而支持按需加载、字节码缓存与动态 import。
   *
   * @param name 模块标识。
   * @param code 模块源码。
   */
  @Deprecated("创建 QuickJsRuntime 时传入 JsModuleLoader")
  @Suppress("DEPRECATION")
  fun addModule(name: String, code: String) {
    engine.addModule(name = name, code = code)
  }

  /**
   * 将已编译的 ES Module 加入旧版预执行队列。
   *
   * 新代码应通过 [JsModuleContent.Bytecode] 返回缓存；字节码必须来自兼容的 QuickJS 版本。
   *
   * @param bytecode 由同版本 QuickJS 生成的 ES Module 字节码。
   */
  @Deprecated("创建 QuickJsRuntime 时传入 JsModuleLoader")
  @Suppress("DEPRECATION")
  fun addModule(bytecode: ByteArray) {
    engine.addModule(bytecode)
  }

  /**
   * 把 JavaScript 源码编译为当前 QuickJS 版本的字节码。
   *
   * 字节码不保证跨 QuickJS 版本兼容，只应在版本一致的运行时中使用。按 Module 编译时该方法只
   * 生成入口字节码；如需提前解析静态依赖并收集各 Module 字节码，应继续调用
   * [resolveModuleGraph]。
   *
   * @param code 待编译源码。
   * @param filename 逻辑文件名，也是 Module 字节码记录的入口名称。
   * @param asModule 是否按 ES Module 编译。
   * @return QuickJS 字节码。
   */
  fun compile(
    code: String,
    filename: String = DEFAULT_FILENAME,
    asModule: Boolean = false,
  ): ByteArray {
    return engine.compile(code = code, filename = filename, asModule = asModule)
  }

  /**
   * 解析入口字节码可静态到达的 ES Module 图，但不执行任何顶层代码。
   *
   * 解析使用临时 Context，并通过构造时传入的 [JsModuleLoader] 按需取得源码或缓存字节码。
   * 源码编译结果会立即通知 [JsModuleLoader.onCompiled]；为避免随后 [evaluate] 再次编译，
   * loader 应让刚收到的字节码能够被后续 [JsModuleLoader.load] 立即读取。动态 import 只会在
   * 实际执行到对应语句时加载。
   *
   * @param entryBytecode 由 [compile] 生成的入口 ES Module 字节码。
   * @return 入口及本次静态解析请求过的依赖 Module 标准化名称。
   */
  fun resolveModuleGraph(entryBytecode: ByteArray): Set<String> {
    return engine.resolveModuleGraph(entryBytecode)
  }

  /**
   * 执行由同版本 QuickJS 生成的字节码。
   *
   * Module 字节码的静态与动态依赖会通过构造时传入的 [JsModuleLoader] 解析。
   *
   * @param bytecode [compile] 生成的字节码。
   * @return 字节码执行结果。
   */
  suspend inline fun <reified T> evaluate(bytecode: ByteArray): T {
    return engine.evaluate(bytecode)
  }

  /**
   * 向 JavaScript 全局对象注册同步宿主函数。
   *
   * @param name JavaScript 中使用的函数名。
   * @param block 接收 JavaScript 参数并返回可映射结果的同步实现。
   */
  fun bindFunction(
    name: String,
    block: (args: Array<Any?>) -> Any?,
  ) {
    engine.function(name = name, block = block)
  }

  /**
   * 向 JavaScript 全局对象注册异步宿主函数。
   *
   * JavaScript 调用后得到 Promise，可通过 `await` 获取 [block] 的结果；异常会转为 Promise rejection。
   *
   * @param name JavaScript 中使用的函数名。
   * @param block 可调用其他 suspend API 的宿主实现。
   */
  fun bindAsyncFunction(
    name: String,
    block: suspend (args: Array<Any?>) -> Any?,
  ) {
    engine.asyncFunction(name = name, block = block)
  }

  /**
   * 主动触发 QuickJS 垃圾回收。
   */
  fun gc() {
    engine.gc()
  }

  /**
   * 释放 QuickJS Runtime、Context、Module 与未完成异步任务。
   *
   * 重复调用安全，但关闭后所有其他操作都会失败。
   */
  fun close() {
    engine.close()
  }

  companion object {
    const val DEFAULT_FILENAME: String = "main.js"
  }
}
