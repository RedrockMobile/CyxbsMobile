package com.cyxbs.pages.course.api

import com.cyxbs.components.navigation.AppNavArgument
import kotlinx.serialization.Serializable

/** 课表单页导航参数；不传学号时进入当前登录账号的课表。 */
@Serializable
data class CourseNavArgument(
  /**
   * 目标学号；null 或空字符串表示当前登录账号。
   *
   * 可空类型让协议生成器把该字段识别为可省略参数，使 `cyxbs://course` 成为合法入口；
   * 显式学号仍用于查找他人课表。
   */
  val stuNum: String? = null,
  // 自定义稳定 contentKey，用于宽屏 ListDetailSceneStrategy 复用同一个 NavEntry：
  // 设置后多次以相同 stableKey 跳转只更新 stuNum，不重建页面与 ViewModel
  val stableKey: String? = null,
) : AppNavArgument
