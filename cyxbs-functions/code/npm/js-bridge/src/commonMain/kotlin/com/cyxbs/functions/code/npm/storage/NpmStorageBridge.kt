package com.cyxbs.functions.code.npm.storage

import com.cyxbs.functions.code.npm.js.bridge.NpmJsBridge
import com.cyxbs.functions.code.npm.js.bridge.NpmJsBridgeInstance
import com.cyxbs.functions.code.npm.js.bridge.NpmJsResult

/**
 * npm Storage 使用的通用反向桥协议。
 *
 * 公开 Storage API 仍由 [NpmStorage] 提供；该接口只承载一条 JSON 内部协议，使 Settings 与文件
 * 操作共享 KSP 生成的 capabilities、scope 过滤和异常兼容机制。
 */
@NpmJsBridge
interface NpmStorageBridge : NpmJsBridgeInstance {
  /**
   * 处理一项 Storage JSON 请求并返回稳定 JSON 响应。
   *
   * 桥未安装/无权限、旧宿主缺少本方法、请求协议损坏、Settings 或文件读写失败都通过
   * [NpmJsResult.failure] 返回对应稳定桥异常；协程取消仍直接抛出。
   */
  suspend fun invoke(requestJson: String): NpmJsResult<String>
}
