package com.cyxbs.functions.code.js.bundle

import com.cyxbs.functions.code.js.runtime.QuickJsRuntime

/**
 * 可安装到 QuickJS Runtime 的单项宿主能力。
 *
 * 每项能力必须拥有稳定 ID，执行策略会按 ID 做白名单校验。实现中可以注册一个或多个函数，
 * 但修改函数语义或名称后必须提升所属 [JsRuntimeBundle.version]。
 */
interface JsHostCapability {
  val id: String

  /**
   * 将能力注册到 [runtime]。
   *
   * 该方法每次执行脚本都会在新的 Runtime 上调用，不能假设多个脚本共享全局 JS 状态。
   */
  fun install(runtime: QuickJsRuntime)
}

/**
 * 把单个同步 Kotlin 函数包装成宿主能力。
 *
 * @param id 用于权限控制的稳定能力 ID。
 * @param functionName JavaScript 全局函数名。
 * @param block 同步宿主实现。
 */
class JsSyncFunctionCapability(
  override val id: String,
  val functionName: String,
  private val block: (args: Array<Any?>) -> Any?,
) : JsHostCapability {

  override fun install(runtime: QuickJsRuntime) {
    runtime.bindFunction(name = functionName, block = block)
  }
}

/**
 * 把单个 suspend Kotlin 函数包装成返回 Promise 的宿主能力。
 *
 * @param id 用于权限控制的稳定能力 ID。
 * @param functionName JavaScript 全局函数名。
 * @param block 可调用协程 API 的宿主实现。
 */
class JsAsyncFunctionCapability(
  override val id: String,
  val functionName: String,
  private val block: suspend (args: Array<Any?>) -> Any?,
) : JsHostCapability {

  override fun install(runtime: QuickJsRuntime) {
    runtime.bindAsyncFunction(name = functionName, block = block)
  }
}

/**
 * 一组可被多个业务脚本复用的 JS 模块与宿主能力。
 *
 * Bundle 本身不可变且可以安全复用；每次执行仍会创建独立 Runtime，从而避免不同脚本共享
 * globalThis、Promise 队列或可变对象。
 *
 * @param id Bundle 稳定标识。
 * @param version Bundle 内容或宿主实现变化时必须递增的版本。
 * @param hostApiVersion 对外宿主桥协议版本。
 * @param modules 预置 ES Module，键必须与脚本 import 标识一致。
 * @param capabilities 可用宿主能力。
 */
class JsRuntimeBundle(
  val id: String,
  val version: Int,
  val hostApiVersion: Int,
  modules: Map<String, String> = emptyMap(),
  capabilities: List<JsHostCapability> = emptyList(),
) {

  val modules: Map<String, String> = modules.toMap()
  val capabilities: List<JsHostCapability> = capabilities.toList()

  init {
    require(ID_REGEX.matches(id)) { "Bundle id must match ${ID_REGEX.pattern}." }
    require(version > 0) { "Bundle version must be greater than 0." }
    require(hostApiVersion > 0) { "hostApiVersion must be greater than 0." }
    this.modules.keys.forEach { moduleName ->
      require(moduleName.isNotBlank() && '\u0000' !in moduleName) {
        "Bundle module names must not be blank or contain NUL characters."
      }
    }
    this.capabilities.forEach { capability ->
      require(ID_REGEX.matches(capability.id)) {
        "Capability id must match ${ID_REGEX.pattern}."
      }
    }
    require(this.capabilities.map { it.id }.distinct().size == this.capabilities.size) {
      "Capability ids must be unique inside a bundle."
    }
  }

  /**
   * Bundle 中所有预置模块源码的 UTF-8 字节数。
   */
  val sourceSizeBytes: Long
    get() = modules.values.sumOf { it.encodeToByteArray().size.toLong() }

  /**
   * Bundle 暴露的能力 ID。
   */
  val capabilityIds: Set<String>
    get() = capabilities.mapTo(linkedSetOf()) { it.id }

  /**
   * 将 Bundle 的宿主能力安装到独立 Runtime。
   *
   * [modules] 必须在 Runtime 创建前合并进 [com.cyxbs.functions.code.js.runtime.JsModuleLoader]，因为新的 Module 加载流程属于
   * Runtime 级配置，不能在 Context 创建后追加。
   *
   * @param runtime 已配置 Module loader 的独立 Runtime。
   */
  internal fun install(runtime: QuickJsRuntime) {
    capabilities.forEach { it.install(runtime) }
  }

  companion object {
    private val ID_REGEX = Regex("[A-Za-z0-9._-]{1,128}")

    /**
     * 不提供模块和宿主桥的最小 Bundle，适合纯 JavaScript 教学。
     */
    val EMPTY = JsRuntimeBundle(
      id = "empty",
      version = 1,
      hostApiVersion = 1,
    )
  }
}
