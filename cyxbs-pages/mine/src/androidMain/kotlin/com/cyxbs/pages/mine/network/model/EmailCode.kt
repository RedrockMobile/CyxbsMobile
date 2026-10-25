package com.cyxbs.pages.mine.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 接收的邮箱验证码。
 */
@Serializable
data class EmailCode(
        @SerialName("expired_time")
        val expiredTime: Int,
)
