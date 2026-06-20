package com.cyxbs.pages.course.api

import com.cyxbs.components.navigation.AppNavArgument
import kotlinx.serialization.Serializable

/**
 * 查找他人课表页
 *
 * 参数语义：
 * - [initialQuery] 不空：预填搜索框并自动查询
 *
 * @author 985892345
 * @date 2026/5/27
 */
@Serializable
data class FindCourseNavArgument(
  val initialQuery: String = "",
) : AppNavArgument
