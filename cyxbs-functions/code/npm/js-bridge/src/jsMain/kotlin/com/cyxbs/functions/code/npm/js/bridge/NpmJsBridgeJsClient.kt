package com.cyxbs.functions.code.npm.js.bridge

import kotlinx.coroutines.await
import kotlin.js.JSON
import kotlin.js.Promise

/**
 * KSP 生成的桥代理使用的 Runtime 级客户端。
 *
 * Kotlin/JS module 和全局对象都只存在于当前隔离 Runtime，因此 capabilities 缓存天然不会跨
 * npm Runtime 泄漏。缺失桥也会缓存，避免每次调用都重复探测宿主。
 */
object NpmJsBridgeJsClient {
  private val methodNamesByBridgeId = mutableMapOf<String, Set<String>>()
  private val missingBridgeIds = mutableSetOf<String>()

  /**
   * 调用指定宿主桥方法。
   *
   * @param bridgeId 注解接口的稳定限定名。
   * @param methodName 不允许重载的稳定方法名。
   * @param argumentsJson KSP 使用统一 Json 配置编码的参数数组。
   * @return 宿主使用相同配置编码的结果 JSON。
   */
  suspend fun invoke(
    bridgeId: String,
    methodName: String,
    argumentsJson: String,
  ): String {
    val methodNames = describe(bridgeId)
    if (methodName !in methodNames) {
      throw NpmJsBridgeMethodNotImplementedException(bridgeId, methodName)
    }
    val response = callGateway(
      operation = NpmJsBridgeHostAbi.INVOKE,
      bridgeId = bridgeId,
      methodName = methodName,
      argumentsJson = argumentsJson,
    )
    return response.result as? String
      ?: throw NpmJsBridgeInvocationException(
        "npm JavaScript bridge '$bridgeId.$methodName' returned an invalid result.",
      )
  }

  /** 首次访问桥时读取并缓存当前宿主实际提供的方法集合。 */
  private suspend fun describe(bridgeId: String): Set<String> {
    methodNamesByBridgeId[bridgeId]?.let { return it }
    if (bridgeId in missingBridgeIds) throw NpmJsBridgeNotInstalledException(bridgeId)

    val response = callGateway(
      operation = NpmJsBridgeHostAbi.DESCRIBE,
      bridgeId = bridgeId,
      methodName = null,
      argumentsJson = null,
    )
    val methods = (response.methods as? Array<*>)
      ?.map { value -> value as String }
      ?.toSet()
      ?: throw NpmJsBridgeInvocationException(
        "npm JavaScript bridge '$bridgeId' returned invalid capabilities.",
      )
    methodNamesByBridgeId[bridgeId] = methods
    return methods
  }

  /** 调用单一宿主函数，并把结构化错误还原成调用方可判断的异常类型。 */
  private suspend fun callGateway(
    operation: String,
    bridgeId: String,
    methodName: String?,
    argumentsJson: String?,
  ): dynamic {
    val global: dynamic = js("globalThis")
    val gateway = global[NpmJsBridgeHostAbi.GATEWAY]
    if (gateway == undefined) {
      missingBridgeIds += bridgeId
      throw NpmJsBridgeNotInstalledException(bridgeId)
    }

    val responseJson = try {
      val arguments = mutableListOf<Any?>(operation, bridgeId)
      if (methodName != null) arguments += methodName
      if (argumentsJson != null) arguments += argumentsJson
      (gateway.apply(null, arguments.toTypedArray()) as Promise<String>).await()
    } catch (throwable: Throwable) {
      if (throwable is NpmJsBridgeException) throw throwable
      throw NpmJsBridgeInvocationException(
        "Failed to invoke npm JavaScript bridge '$bridgeId'.",
        throwable,
      )
    }

    val response: dynamic = JSON.parse(responseJson)
    if (response.ok == true) return response
    val code = response.code as? String
    val responseMethod = (response.methodName as? String) ?: methodName.orEmpty()
    when (code) {
      NpmJsBridgeHostAbi.ERROR_BRIDGE_NOT_INSTALLED -> {
        missingBridgeIds += bridgeId
        throw NpmJsBridgeNotInstalledException(bridgeId)
      }
      NpmJsBridgeHostAbi.ERROR_METHOD_NOT_IMPLEMENTED ->
        throw NpmJsBridgeMethodNotImplementedException(bridgeId, responseMethod)
      else -> throw NpmJsBridgeInvocationException(
        (response.message as? String)
          ?: "npm JavaScript bridge '$bridgeId' rejected the invocation.",
      )
    }
  }
}
