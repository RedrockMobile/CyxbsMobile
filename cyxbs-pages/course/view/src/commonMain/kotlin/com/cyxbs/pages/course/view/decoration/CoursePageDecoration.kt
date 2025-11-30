package com.cyxbs.pages.course.view.decoration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import com.cyxbs.pages.course.view.page.LocalCoursePage
import com.cyxbs.pages.course.view.page.LocalCoursePageContext
import com.cyxbs.pages.course.view.timeline.LocalCourseScroll
import com.cyxbs.pages.course.view.timeline.LocalCourseScrollContext

/**
 * 绘制在课表上的装饰物，同时也能拦截触摸事件
 *
 * @author 985892345
 * @date 2025/3/19
 */
@Stable
interface CoursePageDecoration {

  val coursePage: LocalCoursePageContext
    @Composable
    get() = LocalCoursePage.current

  /**
   * 绘制在课表 scroll 内层
   *
   * 使用 [LocalCoursePage]、[LocalCourseScroll] 获取更多参数
   */
  @Composable
  fun CoursePage(nextContent: @Composable () -> Unit)
}