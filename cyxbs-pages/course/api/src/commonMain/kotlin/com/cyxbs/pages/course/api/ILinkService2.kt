package com.cyxbs.pages.course.api

import kotlinx.coroutines.flow.StateFlow

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/22
 */
interface ILinkService2 {

  val state: StateFlow<LinkStu>

  data class LinkStu(
    val selfNum: String, // 自己的学号
    val linkNum: String, // 关联人的学号
    val linkMajor: String, // 关联人的专业
    val linkName: String, // 关联人的姓名
  ) {

    fun isNull(): Boolean {
      return linkNum.isBlank() || selfNum.isBlank()
    }

    fun isNotNull(): Boolean {
      return !isNull()
    }
  }
}