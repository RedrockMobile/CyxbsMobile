package com.cyxbs.components.account.bean

import com.cyxbs.components.config.isDebug
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.utils.extensions.toast
import io.ktor.util.decodeBase64String
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * .
 *
 * @author 985892345
 * @date 2025/1/11
 */
@Serializable
class TokenBean(
  @SerialName("token")
  val token: String,
  @SerialName("refreshToken")
  val refreshToken: String,
) {
  // 从 token 中解码的数据
  @Transient
  val info: TokenInfo = kotlin.runCatching {
    val str = token.substringBefore(".").decodeBase64String()
    defaultJson.decodeFromString<TokenInfo>(str)
  }.onFailure {
    if (isDebug()) {
      toast("【!!!】token 解析失败，请联系 server 解决")
    }
  }.getOrThrow()
}

@Serializable
class TokenInfo(
  @SerialName("Data")
  val data: TokenInfoData,
  @SerialName("exp")
  val exp: String, // token 过期时间戳
) {
  @Serializable
  class TokenInfoData(
    @SerialName("gender")
    val gender: String,
    @SerialName("stu_num")
    val stuNum: String,
  )
}