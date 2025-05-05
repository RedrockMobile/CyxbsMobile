package com.cyxbs.pages.course.view.page

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.cyxbs.pages.course.view.timeline.LocalCourseScroll

/**
 * 绘制在课表上的装饰物，同时也能拦截触摸事件
 *
 * @author 985892345
 * @date 2025/3/19
 */
interface CoursePageDecoration {

  /**
   * 绘制在课表 scroll 外层
   *
   * 使用 [LocalCoursePage] 获取更多参数
   *
   * @param scrollPaddingValues 滚轴内部 padding
   * @param timelineWidth 课表时间轴宽度
   */
  @Composable
  fun OuterCoursePage(
    scrollPaddingValues: PaddingValues,
    timelineWidth: Dp,
    content: @Composable () -> Unit,
  ) {
    content.invoke()
  }

  /**
   * 绘制在课表 scroll 内层
   *
   * 使用 [LocalCoursePage]、[LocalCourseScroll] 获取更多参数
   */
  @Composable
  fun InnerCoursePage(content: @Composable () -> Unit) {
    content.invoke()
  }
}