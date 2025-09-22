package com.cyxbs.components.account.provider

import com.cyxbs.components.config.service.implOrNull

/**
 * 暴露给 token 和 userInfo 加密的功能
 * 主要是为了兼容安卓端加密的实现
 *
 * @author 985892345
 * @date 2025/4/4
 */
internal interface SecretTransformer {
  fun secretEncrypt(input: String): String
  fun secretDecrypt(input: String): String

  companion object {

    val impl: SecretTransformer =
      SecretTransformer::class.implOrNull() ?: object : SecretTransformer {
        override fun secretEncrypt(input: String): String = input
        override fun secretDecrypt(input: String): String = input
      }
  }
}