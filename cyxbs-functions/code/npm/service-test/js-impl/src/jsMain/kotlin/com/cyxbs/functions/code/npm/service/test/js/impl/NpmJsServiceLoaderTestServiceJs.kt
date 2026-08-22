package com.cyxbs.functions.code.npm.service.test.js.impl

import com.cyxbs.functions.code.npm.js.bridge.NpmJsResult
import com.cyxbs.functions.code.npm.js.bridge.npmJsCatching
import com.cyxbs.functions.code.npm.service.test.js.bridge.NpmJsBidirectionalBridgeExampleResult
import com.cyxbs.functions.code.npm.service.test.js.bridge.NpmJsHostBridgeExampleRequest
import com.cyxbs.functions.code.npm.service.test.js.bridge.NpmJsServiceLoaderTestPayload
import com.cyxbs.functions.code.npm.service.test.js.bridge.NpmJsServiceLoaderTestResult
import com.cyxbs.functions.code.npm.service.test.js.bridge.NpmJsServiceLoaderTestService
import com.cyxbs.functions.code.npm.service.test.js.bridge.npmJsServiceTestHostBridge

/**
 * npm 测试包中的 Kotlin/JS Service 实现。
 *
 * 手动验证热更新时可修改 [BUNDLE_MARKER]，再执行本模块的 installAndroidDebugNpmBundle 任务；测试页面
 * 应在 App 重启后展示新的标识。
 */
object NpmJsServiceLoaderTestServiceJs : NpmJsServiceLoaderTestService {

  override suspend fun execute(
    input: String,
    value: Int,
  ): NpmJsResult<NpmJsServiceLoaderTestResult> = npmJsCatching {
    NpmJsServiceLoaderTestResult(
      bundleMarker = BUNDLE_MARKER,
      input = input,
      inputLength = input.length,
      multipliedValue = value * MULTIPLIER,
    )
  }

  /** 测试协议只需原样返回参数，使断言聚焦于 KSP 生成的 JSON 编解码行为。 */
  override suspend fun echoPayload(
    payload: NpmJsServiceLoaderTestPayload,
  ): NpmJsResult<NpmJsServiceLoaderTestPayload> = NpmJsResult.success(payload)

  /**
   * 演示 JS Service 使用 KSP 生成的代理回调端上 Host Bridge。
   *
   * 业务实现无需接触 Runtime、JSON 或 bridgeId，只需像调用普通 suspend 接口一样调用代理。
   */
  override suspend fun executeBidirectional(
    input: String,
    value: Int,
  ): NpmJsResult<NpmJsBidirectionalBridgeExampleResult> = npmJsCatching {
    val hostResponse = npmJsServiceTestHostBridge.execute(
      NpmJsHostBridgeExampleRequest(input = input, value = value),
    ).getOrThrow()
    NpmJsBidirectionalBridgeExampleResult(
      bundleMarker = BUNDLE_MARKER,
      hostResponse = hostResponse,
    )
  }

  private const val BUNDLE_MARKER = "local-js-service-v1"
  private const val MULTIPLIER = 7

  /** 当前实现不持有独立资源；Runtime 仍由宿主代理在本方法返回后统一释放。 */
  override suspend fun close(): NpmJsResult<Unit> = NpmJsResult.success(Unit)
}
