package com.cyxbs.pages.course.view.decoration

import androidx.compose.runtime.Composable
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.item.CourseItemHierarchy
import com.cyxbs.pages.course.view.page.LocalCoursePage
import com.cyxbs.pages.course.view.page.LocalCoursePageContext

/**
 * 绘制在课表上的装饰物，同时也能拦截触摸事件
 *
 * @author 985892345
 * @date 2026/4/19
 */
abstract class CoursePageDecoration<Item: CourseItem> {

  val coursePage: LocalCoursePageContext
    @Composable
    get() = LocalCoursePage.current

  /**
   * 当前层级的 item 列表
   */
  val itemHierarchy: CourseItemHierarchy<Item> = CourseItemHierarchy()

  /**
   * 绘制在课表 scroll 内层
   *
   * 使用 [coursePage] 获取更多参数
   */
  @Composable
  open fun CoursePageContent() {
    itemHierarchy.CoursePageItemListContent()
  }
}