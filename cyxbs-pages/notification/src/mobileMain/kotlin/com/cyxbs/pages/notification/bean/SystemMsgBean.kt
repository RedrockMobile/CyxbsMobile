package com.cyxbs.pages.notification.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Author by OkAndGreat
 * Date on 2022/5/1 11:01.
 *
 */
@Serializable
data class MsgBeanData(
    @SerialName("system_msg")
    val system_msg: List<SystemMsgBean>
)

@Serializable
data class SystemMsgBean(
    @SerialName("content")
    val content: String,
    @SerialName("has_read")
    var has_read: Boolean,
    @SerialName("id")
    val id: Int,
    @SerialName("publish_time")
    val publish_time: Long,
    @SerialName("redirect_url")
    val redirect_url: String,
    @SerialName("title")
    val title: String,
)