package com.cyxbs.pages.course.api

import com.cyxbs.components.navigation.AppNavArgument
import kotlinx.serialization.Serializable

/**
 * 课表单页
 *
 * @author 985892345
 * @date 2025/11/16
 */
@Serializable
data class CourseNavArgument(
  val stuNum: String,
) : AppNavArgument