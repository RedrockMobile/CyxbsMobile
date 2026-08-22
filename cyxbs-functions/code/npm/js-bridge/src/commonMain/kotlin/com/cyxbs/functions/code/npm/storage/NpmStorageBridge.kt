package com.cyxbs.functions.code.npm.storage

import com.cyxbs.functions.code.npm.js.bridge.NpmJsBridge

/**
 * npm Storage 使用的通用反向桥协议。
 *
 * 公开 Storage API 仍由 [NpmStorage] 提供；该接口只承载一条 JSON 内部协议，使 Settings 与文件
 * 操作共享 KSP 生成的 capabilities、scope 过滤和异常兼容机制。
 */
@NpmJsBridge
interface NpmStorageBridge {
  /** 处理一项 Storage JSON 请求并返回稳定 JSON 响应。 */
  suspend fun invoke(requestJson: String): String
}
