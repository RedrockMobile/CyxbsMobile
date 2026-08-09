package com.cyxbs.functions.code.npm.service.test.js.bridge

import com.cyxbs.functions.code.npm.js.bridge.NpmJsService
import com.cyxbs.functions.code.npm.js.bridge.NpmJsServiceInstance
import kotlinx.serialization.Serializable

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

/**
 * 专门用于验证 `NpmJsServiceLoader` 完整调用链路的测试协议。
 *
 * 接口留在独立 js-bridge 的 commonMain，端上和 Kotlin/JS 实现模块共同依赖这份稳定协议。
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
  suspend fun execute(input: String, value: Int): NpmJsServiceLoaderTestResult
}
