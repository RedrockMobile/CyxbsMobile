package com.cyxbs.functions.code.npm.storage

import com.cyxbs.functions.code.npm.bridge.NpmJsBridgeContext
import com.cyxbs.functions.code.npm.js.bridge.NpmJsBridgeImpl
import com.cyxbs.functions.code.npm.js.bridge.NpmJsBridgePackageScope
import com.cyxbs.functions.code.npm.js.bridge.NpmJsResult
import com.cyxbs.functions.code.npm.js.bridge.npmJsCatching

/**
 * npm Storage 的端上桥实现。
 *
 * 包身份来自 Loader 创建的 [context]，JavaScript 无法伪造；Storage 是所有动态包都需要的基础
 * 能力，因此显式选择 [NpmJsBridgePackageScope.ALL_PACKAGES]。
 */
@NpmJsBridgeImpl(packageScope = NpmJsBridgePackageScope.ALL_PACKAGES)
internal class NpmStorageBridgeImpl(
  private val context: NpmJsBridgeContext,
) : NpmStorageBridge {
  private val host: NpmStorageHost = NpmStorageHost.Default

  /** 将生成 dispatcher 解码后的请求转交共享宿主。 */
  override suspend fun invoke(requestJson: String): NpmJsResult<String> = npmJsCatching {
    host.handle(context.entryPackageName, requestJson)
  }
}
