package com.cyxbs.functions.code.npm.service.test.js.bridge

import com.cyxbs.functions.code.npm.js.bridge.NpmJsService
import com.cyxbs.functions.code.npm.js.bridge.NpmJsServiceInstance
import com.cyxbs.functions.code.npm.js.bridge.NpmJsResult
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException

/**
 * 独立 npm 测试包返回给端上的结果。
 *
 * [bundleMarker] 用于人工确认当前运行的是线上稳定包还是刚通过 ADB 注入的本地包。
 */
@Serializable
data class NpmJsServiceLoaderTestResult(
  val bundleMarker: String,
  val input: String,
  val inputLength: Int,
  val multipliedValue: Int,
)

/** 用于验证生成桥接代码 JSON 前向兼容配置的结构化参数。 */
@Serializable
data class NpmJsServiceLoaderTestPayload(
  val value: String,
  val nullableValue: String?,
  val optionalValue: String? = null,
)

/**
 * 专门用于验证 `NpmJsServiceLoader` 完整调用链路的测试协议。
 *
 * 接口留在独立 js-bridge 的 commonMain，端上和 Kotlin/JS 实现模块共同依赖这份稳定协议。
 * 当前 bundle 缺少方法时失败值为 `NpmJsServiceMethodNotImplementedException`；实现异常、显式失败、
 * JSON 或结果信封不兼容时失败值为 `NpmJsServiceInvocationException`。bundle 获取失败发生在
 * 实例创建前，由 Loader 直接抛出；只有 [CancellationException] 会绕过 [NpmJsResult]。
 */
@NpmJsService
interface NpmJsServiceLoaderTestService : NpmJsServiceInstance {

  /**
   * 执行一次包含字符串和数字参数的 suspend 调用。
   *
   * @param input 页面传入的测试文本。
   * @param value 用于验证参数传递和返回值反序列化的数字。
   * @return 当前 bundle 标识与 JavaScript 侧计算结果。
   */
  suspend fun execute(input: String, value: Int): NpmJsResult<NpmJsServiceLoaderTestResult>

  /** 原样返回结构化参数，供测试验证未知字段、默认值和显式 `null` 的桥接行为。 */
  suspend fun echoPayload(
    payload: NpmJsServiceLoaderTestPayload,
  ): NpmJsResult<NpmJsServiceLoaderTestPayload>

  /**
   * 演示端上调用 JS Service 后，JS 再通过 [NpmJsServiceTestHostBridge] 回调端上。
   */
  suspend fun executeBidirectional(
    input: String,
    value: Int,
  ): NpmJsResult<NpmJsBidirectionalBridgeExampleResult>
}
