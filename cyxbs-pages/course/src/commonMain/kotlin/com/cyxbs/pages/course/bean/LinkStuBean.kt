package com.cyxbs.pages.course.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/22
 */
@Serializable
data class LinkStuBean(
  @SerialName("stuNum")
  val linkNum: String, // 关联人的学号，注意这个跟接口的字段名不一样
  @SerialName("major")
  val major: String,
  @SerialName("name")
  val name: String,
  @SerialName("selfNum")
  val selfNum: String, // 自身的学号
) {

  fun isEmpty(): Boolean {
    // 后端在没有关联人时返回空串
    return linkNum.isEmpty()
  }

  fun isNotEmpty(): Boolean {
    return linkNum.isNotEmpty()
  }
}