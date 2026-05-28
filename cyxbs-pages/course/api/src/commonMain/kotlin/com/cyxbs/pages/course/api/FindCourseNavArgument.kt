package com.cyxbs.pages.course.api

import com.cyxbs.components.navigation.AppNavArgument
import kotlinx.serialization.Serializable

/**
 * 查找他人课表页
 *
 * 参数语义：
 * - [initialQuery] 不空：预填搜索框并自动查询，兼容老 startByStuName 行为
 * - [directStuNum] 不空：直接打开该学号课表（窄屏 push、宽屏右侧 detail），兼容老 startByStuNum 行为
 * - 两者都为空：纯查找入口
 *
 * @author 985892345
 * @date 2026/5/27
 */
@Serializable
data class FindCourseNavArgument(
  val initialQuery: String = "",
  val directStuNum: String = "",
) : AppNavArgument
