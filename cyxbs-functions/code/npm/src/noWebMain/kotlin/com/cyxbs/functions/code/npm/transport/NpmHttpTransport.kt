package com.cyxbs.functions.code.npm.transport

import com.cyxbs.functions.code.npm.NpmDownloadException
import kotlinx.coroutines.CancellationException

/**
 * npm 下载器使用的最小 HTTP 边界。
 *
 * 该接口便于业务复用既有 HttpClient，也便于测试验证请求顺序。包体资源约束由 npm 包发布流程
 * 保证，因此下载端不额外限制响应大小。
 */
fun interface NpmHttpTransport {

  /**
   * 获取一个完整响应。
   *
   * @throws NpmDownloadException HTTP 状态异常或网络失败。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(NpmDownloadException::class, CancellationException::class)
  suspend fun get(url: String): ByteArray

  /**
   * 获取带请求头的完整响应。
   *
   * 默认实现保持已有自定义 Transport 的二进制和源码接入方式；需要使用 npm 精简元数据协议的实现可
   * 覆盖该方法并转发 [headers]。
   */
  @Throws(NpmDownloadException::class, CancellationException::class)
  suspend fun get(url: String, headers: Map<String, String>): ByteArray = get(url)
}
