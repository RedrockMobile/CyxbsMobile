package com.cyxbs.pages.sport.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NoticeItem (
    @SerialName("title")
    val title: String,
    @SerialName("content")
    val content: String
)