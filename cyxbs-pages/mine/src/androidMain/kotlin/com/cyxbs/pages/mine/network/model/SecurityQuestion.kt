package com.cyxbs.pages.mine.network.model

import kotlinx.serialization.Serializable

/**
 * 密保问题的网络响应模型。
 */
@Serializable
data class SecurityQuestion(
        val id: Int,
        val content: String,
)
