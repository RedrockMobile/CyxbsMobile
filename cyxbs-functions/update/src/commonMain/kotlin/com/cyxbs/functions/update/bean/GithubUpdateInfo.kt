package com.cyxbs.functions.update.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ...
 * @author RQ527 (Ran Sixiang)
 * @email 1799796122@qq.com
 * @date 2023/10/28
 * @Description:
 */
@Serializable
data class GithubUpdateInfo(
    @SerialName("assets")
    val assets: List<Asset>,
    @SerialName("body")
    val body: String,
    @SerialName("tag_name")
    val tag: String
) {
    @Serializable
    data class Asset(
        @SerialName("browser_download_url")
        val downloadUrl: String,
    )
}