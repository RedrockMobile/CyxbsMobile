package com.cyxbs.pages.course.api

import cyxbsmobile.cyxbs_pages.course.api.generated.resources.Res
import cyxbsmobile.cyxbs_pages.course.api.generated.resources.course_ic_item_header_link_double
import cyxbsmobile.cyxbs_pages.course.api.generated.resources.course_ic_item_header_link_single
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.DrawableResource

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/22
 */
interface ILinkService2 {

  val state: StateFlow<LinkStu>

  val enableShow: StateFlow<Boolean>

  fun changeVisible()

  companion object {
    val icon_link_double: DrawableResource
      get() = Res.drawable.course_ic_item_header_link_double

    val icon_link_single: DrawableResource
      get() = Res.drawable.course_ic_item_header_link_single
  }

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