package com.cyxbs.components.utils.network.plugin

import com.cyxbs.components.account.api.ITokenService
import com.cyxbs.components.config.service.impl
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.bearerAuth

/**
 * .
 *
 * @author 985892345
 * @date 2025/1/19
 */
internal val TokenPlugin = createClientPlugin(
  "TokenPlugin",
) {
  val tokenService = ITokenService::class.impl()
  on(Send) { request ->
    val token = tokenService.getOrRequestToken()
    if (token != null) {
      request.bearerAuth(token) // 添加 token
      // 未登录时也允许请求，端上不好判断该请求是否强依赖登录状态，需要依靠 server 进行拦截
    }
    proceed(request)
  }
}