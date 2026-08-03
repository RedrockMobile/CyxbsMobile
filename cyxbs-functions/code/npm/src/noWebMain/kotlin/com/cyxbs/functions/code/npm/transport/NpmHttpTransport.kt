package com.cyxbs.functions.code.npm.transport

import com.cyxbs.functions.code.npm.NpmDownloadException
import kotlinx.coroutines.CancellationException

/**
 * npm 下载器使用的最小 HTTP 边界。
 *
 * 该接口便于业务复用既有 HttpClient，也便于测试验证请求顺序。包体资源约束由可信的后端发布
 * 流程保证，因此下载端不额外限制响应大小。
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
}
