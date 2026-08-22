package com.cyxbs.functions.code.npm.service.test

import com.cyxbs.functions.code.npm.bridge.NpmJsBridgeContext
import com.cyxbs.functions.code.npm.js.bridge.NpmJsBridgeImpl
import com.cyxbs.functions.code.npm.js.bridge.NpmJsBridgePackageScope
import com.cyxbs.functions.code.npm.js.bridge.NpmJsResult
import com.cyxbs.functions.code.npm.js.bridge.npmJsCatching
import com.cyxbs.functions.code.npm.service.test.js.bridge.NpmJsHostBridgeExampleRequest
import com.cyxbs.functions.code.npm.service.test.js.bridge.NpmJsHostBridgeExampleResponse
import com.cyxbs.functions.code.npm.service.test.js.bridge.NpmJsServiceTestHostBridge

/**
 * service-test 的端上 Host Bridge 示例实现。
 *
 * KSP 会为该类生成 Host factory 与 dispatcher。构造参数 [context] 由 npm Runtime 提供，用于获取
 * 可信入口包身份；实现本身不注册 Runtime 函数，也不手写 JSON 分发逻辑。
 */
@NpmJsBridgeImpl(
  packageScope = NpmJsBridgePackageScope.SPECIFIED_PACKAGES,
  packageNames = ["@cyxbs-mobile/cyxbs-functions-code-npm-service-test-js-impl"],
)
internal class NpmJsServiceTestHostBridgeImpl(
  private val context: NpmJsBridgeContext,
) : NpmJsServiceTestHostBridge {

  /** 处理 JS 回调，并把宿主上下文与计算结果返回给 JS Service。 */
  override suspend fun execute(
    request: NpmJsHostBridgeExampleRequest,
  ): NpmJsResult<NpmJsHostBridgeExampleResponse> = npmJsCatching {
    NpmJsHostBridgeExampleResponse(
      entryPackageName = context.entryPackageName,
      hostMessage = "Host 已收到 JS 消息：${request.input}",
      hostCalculatedValue = request.value * HOST_MULTIPLIER,
    )
  }

  private companion object {
    const val HOST_MULTIPLIER = 11
  }
}
