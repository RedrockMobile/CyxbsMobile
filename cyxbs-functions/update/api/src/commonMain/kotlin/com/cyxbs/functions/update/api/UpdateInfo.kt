package com.cyxbs.functions.update.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateInfo(
    // 保留 Android JSON 字段；iOS 使用 App Store 产品页链接。
    @SerialName("apk_url")
    val apkUrl: String = "",
    @SerialName("update_content")
    val updateContent: String = "",
    @SerialName("version_code")
    val versionCode: Long = 0,
    @SerialName("version_name")
    val versionName: String = ""
)
