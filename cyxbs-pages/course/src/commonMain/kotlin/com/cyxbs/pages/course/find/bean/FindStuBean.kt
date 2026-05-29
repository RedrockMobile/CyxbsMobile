package com.cyxbs.pages.course.find.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 查找学生接口返回的单条结果
 *
 * @author 985892345
 * @date 2026/5/27
 */
@Serializable
data class FindStuBean(
  @SerialName("stunum")
  val stuNum: String,
  @SerialName("name")
  val name: String,
  @SerialName("classnum")
  val classNum: String,
  @SerialName("depart")
  val depart: String,
  @SerialName("gender")
  val gender: String,
  @SerialName("grade")
  val grade: String,
  @SerialName("major")
  val major: String,
)
