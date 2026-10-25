package com.cyxbs.pages.mine.network.model

import kotlinx.serialization.Serializable

/**
 * Author: RayleighZ
 * Time: 2020-11-03 21:54
 * Describe: 邮箱验证时返回的认证码
 */
@Serializable
data class ConfirmCode(
        val code: Int,
        val expired_time: Int,
)
