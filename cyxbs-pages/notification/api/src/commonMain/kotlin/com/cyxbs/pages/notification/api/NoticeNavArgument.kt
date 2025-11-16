package com.cyxbs.pages.notification.api

import androidx.compose.ui.graphics.Color
import com.cyxbs.components.init.MainNavController
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * 通知弹窗，支持对文本字体、颜色自定义，使用 [navigate] 打开
 *
 * 其中 [map] 参数用于对 [content] 中的占位符进行文本替换，key 为占位符，value 为替换后的文本
 * ```
 * 示例：
 * content = "这是{0}\n这是{1}"
 * map = {
 *     "{0}": {
 *         "text": "标题"
 *     },
 *     "{1}": {
 *         "text": "内容"
 *     }
 * }
 *
 * 最后 content 结果为 "这是标题\n这是内容"
 * ```
 *
 * @author 985892345
 * @date 2025/11/2
 */
@Serializable
class NoticeNavArgument(
  @SerialName("title")
  val title: String,
  @SerialName("content")
  val content: String,
  @SerialName("map")
  val map: Map<String, TextInfo> = emptyMap(), // 用于对 [content] 中的占位符进行文本替换
  @SerialName("button")
  val button: ButtonInfo? = null,
) {
  fun navigate() {
    MainNavController.navigate(this) {
      launchSingleTop = true
    }
  }

  @Serializable
  class TextInfo(
    @SerialName("text")
    val text: String,
    @SerialName("isBold")
    val isBold: Boolean = false,
    @SerialName("textSize")
    val textSize: Int = 16,
    @SerialName("textColor")
    val textColorStr: String? = null, // 文本颜色，格式为：#AARRGGBB-#AARRGGBB，后者是黑夜模式颜色，可不填
    @SerialName("action")
    val action: String? = null
  ) {

    @Transient
    val textColor: Color? = textColorStr?.let {
      runCatching {
        Color(it.substringAfter("#").substringBefore("-").toLong(16))
      }.onFailure {
        it.printStackTrace()
      }.getOrNull()
    }

    @Transient
    val textDarkColor: Color? = textColorStr?.let {
      runCatching {
        Color(it.substringAfter("-#").toLong(16))
      }.getOrNull()
    }
  }

  @Serializable
  class ButtonInfo(
    @SerialName("text")
    val text: String,
    @SerialName("action")
    val action: String? = null,
  )
}