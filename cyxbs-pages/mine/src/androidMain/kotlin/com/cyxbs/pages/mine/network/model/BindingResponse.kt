package com.cyxbs.pages.mine.network.model

import kotlinx.serialization.Serializable

/**
 *@Date 2020-11-02
 *@Time 21:17
 *@author SpreadWater
 *@description 用于接收是否绑定信息的基类
 */
@Serializable
data class BindingResponse(
        val question_is: Int,
        val email_is: Int,
)

@Serializable
data class BindingCheckResponse(
        val status: Int,
        val data: BindingResponse,
)

/** 默认密码检查接口的最终响应类型，使用静态 serializer，避免 Gson 反射实例化被 R8 优化后的类型。 */
@Serializable
data class DefaultPasswordCheckResponse(
        val status: Int,
)

/** 修改密码流程接口的最终响应类型，避免 Gson 反射实例化 R8 优化后的状态基类。 */
@Serializable
data class PasswordOperationResponse(
        val status: Int,
)
