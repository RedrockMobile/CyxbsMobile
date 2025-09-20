package com.cyxbs.pages.notification.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Author by OkAndGreat
 * Date on 2022/5/3 9:17.
 * 改变消息已读状态put到服务器的bean类
 */
@Serializable
data class ChangeReadStatusToBean(
    @SerialName("ids")
    val ids: List<String>
)