package com.cyxbs.pages.notification.bean

import androidx.navigation.NavType
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.write
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.pages.notification.api.NoticeNavArgument
import com.eygraber.uri.UriCodec
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * .
 *
 * @author 985892345
 * @date 2025/10/20
 */
@Serializable
data class NoticeDialogBean(
  @SerialName("id")
  val id: String,
  @SerialName("title")
  val title: String,
  @SerialName("content")
  val content: String,
  @SerialName("map")
  val map: Map<String, NoticeNavArgument.TextInfo> = emptyMap(),
  @SerialName("button")
  val button: NoticeNavArgument.ButtonInfo? = null,
)

class NoticeDialogBeanNavType : NavType<NoticeDialogBean>(isNullableAllowed = false) {
  override fun put(
    bundle: SavedState,
    key: String,
    value: NoticeDialogBean
  ) {
    bundle.write {
      val json = runCatching {
        defaultJson.encodeToString(NoticeDialogBean.serializer(), value)
      }.getOrNull()
      if (json != null) {
        putString(key, json)
      }
    }
  }

  override fun get(
    bundle: SavedState,
    key: String
  ): NoticeDialogBean? {
    return bundle.read {
      val json = getStringOrNull(key) ?: return@read null
      return runCatching {
        defaultJson.decodeFromString(NoticeDialogBean.serializer(), json)
      }.getOrNull()
    }
  }

  override fun parseValue(value: String): NoticeDialogBean {
    val decode = UriCodec.decode(value)
    return defaultJson.decodeFromString(NoticeDialogBean.serializer(), decode)
  }
}