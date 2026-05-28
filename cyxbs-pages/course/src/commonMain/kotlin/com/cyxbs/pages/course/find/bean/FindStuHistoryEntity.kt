package com.cyxbs.pages.course.find.bean

import kotlinx.serialization.Serializable

/**
 * 查找学生历史记录精简实体（只持久化 name + stuNum）
 *
 * @author 985892345
 * @date 2026/5/27
 */
@Serializable
data class FindStuHistoryEntity(
  val name: String,
  val stuNum: String,
)
