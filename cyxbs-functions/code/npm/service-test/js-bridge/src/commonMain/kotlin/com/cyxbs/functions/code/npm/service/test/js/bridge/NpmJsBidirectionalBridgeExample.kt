package com.cyxbs.functions.code.npm.service.test.js.bridge

import com.cyxbs.functions.code.npm.js.bridge.NpmJsBridge
import com.cyxbs.functions.code.npm.js.bridge.NpmJsBridgeInstance
import com.cyxbs.functions.code.npm.js.bridge.NpmJsResult
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException

/** JS Service 发给端上 Host Bridge 的示例请求。 */
@Serializable
data class NpmJsHostBridgeExampleRequest(
  val input: String,
  val value: Int,
)

/**
 * 端上 Host Bridge 返回给 JS Service 的示例结果。
 *
 * [entryPackageName] 来自 Runtime 创建时的可信上下文，可用来确认 Host 能识别实际发起调用的入口包。
 */
@Serializable
data class NpmJsHostBridgeExampleResponse(
  val entryPackageName: String,
  val hostMessage: String,
  val hostCalculatedValue: Int,
)

/**
 * JS Service 将 Host Bridge 结果再次返回给端上调用方的完整双向调用结果。
 */
@Serializable
data class NpmJsBidirectionalBridgeExampleResult(
  val bundleMarker: String,
  val hostResponse: NpmJsHostBridgeExampleResponse,
)

/**
 * service-test 的 Host 反向桥示例协议。
 *
 * 该接口位于 js-bridge，JS 实现模块和端上 Host 模块只依赖同一份稳定协议。KSP 会在 JS 侧生成
 * `npmJsServiceTestHostBridge` 强类型代理，并在端上为实现类生成 dispatcher。
 */
@NpmJsBridge
interface NpmJsServiceTestHostBridge : NpmJsBridgeInstance {
  /**
   * 将 JS Service 发出的结构化请求交给端上处理。
   *
   * 桥未安装或入口包无权限时返回 `NpmJsBridgeNotInstalledException`；旧宿主没有本方法时返回
   * `NpmJsBridgeMethodNotImplementedException`；参数协议、宿主实现或返回值编码失败时返回
   * `NpmJsBridgeInvocationException`。协程取消继续抛出，不会包装进 [NpmJsResult]。
   */
  @Throws(CancellationException::class)
  suspend fun execute(
    request: NpmJsHostBridgeExampleRequest,
  ): NpmJsResult<NpmJsHostBridgeExampleResponse>
}
