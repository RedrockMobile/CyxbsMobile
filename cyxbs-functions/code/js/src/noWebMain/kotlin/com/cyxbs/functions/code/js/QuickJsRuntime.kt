package com.cyxbs.functions.code.js

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

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
 * 实例不再使用时必须调用 [close]，关闭后不可继续执行或注册能力。
 * 当前依赖版本不会自动解包入口表达式直接返回的 Promise，调用方应在源码中使用顶层 `await` 获取最终值。
 * 单次执行受 [QuickJsRuntimeConfig.evaluationTimeoutMillis] 限制，也可以通过 [interruptEvaluation] 主动中断。
 *
 * @param jobDispatcher 执行异步宿主函数的协程调度器。
 * @param config 运行时资源与执行限制。
 */
class QuickJsRuntime(
  jobDispatcher: CoroutineDispatcher = Dispatchers.Default,
  config: QuickJsRuntimeConfig = QuickJsRuntimeConfig(),
) {

  /**
   * 供内联泛型执行方法访问的底层实例；模块外调用方不应直接依赖该对象。
   */
  @PublishedApi
  internal val engine: QuickJs = QuickJs.create(jobDispatcher).apply {
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
   * @param filename 用于异常堆栈和后续 Source Map 映射的逻辑文件名。
   * @param asModule 是否按 ES Module 解析；模块执行结果通常为空，应通过导出入口或宿主绑定传值。
   * 如果入口表达式返回 Promise，源码必须显式使用顶层 `await`，否则无法直接映射为 [T]。
   *
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
   * 将 ES Module 源码加入当前运行时。
   *
   * 模块会在下一次 [evaluate] 时加载；[name] 必须与入口代码中的 import 标识一致。
   *
   * @param name 模块标识。
   * @param code 模块源码。
   */
  fun addModule(name: String, code: String) {
    engine.addModule(name = name, code = code)
  }

  /**
   * 把 JavaScript 源码编译为当前 QuickJS 版本的字节码。
   *
   * 字节码不保证跨 QuickJS 版本兼容，只应在版本一致的运行时中使用。
   *
   * @param code 待编译源码。
   * @param filename 逻辑文件名。
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
   * 执行由同版本 QuickJS 生成的字节码。
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
   * 释放 QuickJS Runtime、Context、模块与未完成异步任务。
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
