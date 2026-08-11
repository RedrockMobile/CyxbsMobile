package com.cyxbs.functions.code.npm.service.test.js.impl

import com.cyxbs.functions.code.npm.service.test.js.bridge.NpmJsServiceLoaderTestPayload
import com.cyxbs.functions.code.npm.service.test.js.bridge.NpmJsServiceLoaderTestResult
import com.cyxbs.functions.code.npm.service.test.js.bridge.NpmJsServiceLoaderTestService

/**
 * npm 测试包中的 Kotlin/JS Service 实现。
 *
 * 手动验证热更新时可修改 [BUNDLE_MARKER]，再执行本模块的 installDebugNpmBundle 任务；测试页面
 * 应在 App 重启后展示新的标识。
 */
object NpmJsServiceLoaderTestServiceJs : NpmJsServiceLoaderTestService {

  override suspend fun execute(input: String, value: Int): NpmJsServiceLoaderTestResult {
    return NpmJsServiceLoaderTestResult(
      bundleMarker = BUNDLE_MARKER,
      input = input,
      inputLength = input.length,
      multipliedValue = value * MULTIPLIER,
    )
  }

  /** 测试协议只需原样返回参数，使断言聚焦于 KSP 生成的 JSON 编解码行为。 */
  override suspend fun echoPayload(
    payload: NpmJsServiceLoaderTestPayload,
  ): NpmJsServiceLoaderTestPayload = payload

  private const val BUNDLE_MARKER = "local-js-service-v1"
  private const val MULTIPLIER = 7
}
