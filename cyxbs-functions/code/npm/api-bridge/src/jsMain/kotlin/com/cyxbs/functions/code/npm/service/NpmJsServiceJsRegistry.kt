package com.cyxbs.functions.code.npm.service

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.promise

/**
 * 仅供 KSP 生成代码实现的 Kotlin/JS Service 分发协议。
 *
 * [argumentsJson] 与返回值均为 JSON 文本，具体参数类型由相同协议摘要的生成代码负责转换。
 */
interface NpmJsServiceJsDispatcher {
  val serviceId: String
  val schemaHash: String

  /** 根据稳定方法名调用 Kotlin/JS 实现并返回 JSON 文本。 */
  suspend fun invoke(method: String, argumentsJson: String): String

  /** 释放 Kotlin/JS 实现持有的业务资源。 */
  suspend fun close()
}

/**
 * Kotlin/JS 包内的 Service 注册中心。
 *
 * 生成代码在模块初始化阶段注册分发器；端上 Runtime 只通过 `globalThis.CyxbsNpmJsService` 的
 * `describe` 和 `invoke` 两个稳定函数访问，不依赖 Kotlin/JS 编译后的符号名称。
 */
object NpmJsServiceJsRegistry {
  private val dispatchers = mutableMapOf<String, NpmJsServiceJsDispatcher>()
  private val scope = MainScope()

  /**
   * 注册一个生成分发器。
   *
   * 同一 Service 只允许一个实现，重复注册同一实例可安全忽略，注册不同实现会立即失败。
   */
  fun register(dispatcher: NpmJsServiceJsDispatcher): Boolean {
    val previous = dispatchers[dispatcher.serviceId]
    require(previous == null || previous === dispatcher) {
      "Duplicate npm JavaScript Service '${dispatcher.serviceId}'."
    }
    dispatchers[dispatcher.serviceId] = dispatcher
    installBridgeIfNeeded()
    return true
  }

  /** 首次注册时把稳定桥接对象安装到当前 JavaScript 全局环境。 */
  private fun installBridgeIfNeeded() {
    val global = globalThis()
    if (global.CyxbsNpmJsService != undefined) return
    val bridge: dynamic = js("({})")
    bridge.describe = { serviceId: String ->
      dispatchers[serviceId]?.schemaHash
    }
    bridge.invoke = { serviceId: String, method: String, argumentsJson: String ->
      scope.promise {
        val dispatcher = dispatchers[serviceId]
          ?: error("Unknown npm JavaScript Service '$serviceId'.")
        if (method == CLOSE_METHOD) {
          dispatcher.close()
          NULL_JSON
        } else {
          dispatcher.invoke(method, argumentsJson)
        }
      }
    }
    global.CyxbsNpmJsService = bridge
  }

  private fun globalThis(): dynamic = js("globalThis")

  private const val CLOSE_METHOD = "\$close"
  private const val NULL_JSON = "null"
}
