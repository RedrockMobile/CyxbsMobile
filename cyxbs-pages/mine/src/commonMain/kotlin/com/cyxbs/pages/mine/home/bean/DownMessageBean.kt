package com.cyxbs.pages.mine.home.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 通用下发文本接口请求体。
 *
 * 对应 androidMain 的 `com.cyxbs.components.utils.network.DownMessageParams`，
 * 因 commonMain 暂未提供 ktorfit 版本，故在 mine 模块内自带一份。
 */
@Serializable
data class DownMessageParams(
  @SerialName("name") val name: String,
)

/**
 * 通用下发文本接口返回结构。
 */
@Serializable
data class DownMessageBean(
  @SerialName("name") val name: String = "",
  @SerialName("text") val textList: List<DownMessageText> = emptyList(),
) {
  @Serializable
  data class DownMessageText(
    @SerialName("title") val title: String = "",
    @SerialName("content") val content: String = "",
  )
}
