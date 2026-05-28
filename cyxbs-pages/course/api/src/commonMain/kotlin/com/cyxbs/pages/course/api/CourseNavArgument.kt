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
  // 自定义稳定 contentKey，用于宽屏 ListDetailSceneStrategy 复用同一个 NavEntry：
  // 设置后多次以相同 stableKey 跳转只更新 stuNum，不重建页面与 ViewModel
  val stableKey: String? = null,
) : AppNavArgument