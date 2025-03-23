package com.cyxbs.pages.course.view.page

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.cyxbs.pages.course.view.data.CourseWeekDataPool
import com.cyxbs.pages.course.view.timeline.CourseTimeline

/**
 * 绘制在课表上的装饰物，同时也能拦截触摸事件
 *
 * @author 985892345
 * @date 2025/3/19
 */
interface CoursePageDecoration {

  /**
   * 绘制在课表外层底部
   * @param timeline 课表时间轴
   * @param verticalScrollState 垂直滚轴状态
   * @param weekDataPool 当前周课程数据
   * @param scrollPaddingValues 滚轴内部 padding
   * @param timelineWidth 课表时间轴宽度
   */
  @Composable
  fun OuterCoursePageBottom(
    timeline: CourseTimeline,
    verticalScrollState: ScrollState,
    weekDataPool: CourseWeekDataPool,
    scrollPaddingValues: PaddingValues,
    timelineWidth: Dp,
  ) {
  }

  /**
   * 绘制在课表外层顶部
   * @param timeline 课表时间轴
   * @param verticalScrollState 垂直滚轴状态
   * @param weekDataPool 当前周课程数据
   * @param scrollPaddingValues 滚轴内部 padding
   * @param timelineWidth 课表时间轴宽度
   */
  @Composable
  fun OuterCoursePageTop(
    timeline: CourseTimeline,
    verticalScrollState: ScrollState,
    weekDataPool: CourseWeekDataPool,
    scrollPaddingValues: PaddingValues,
    timelineWidth: Dp,
  ) {
  }

  /**
   * 绘制在课表内层底部
   * @param timeline 课表时间轴
   * @param verticalScrollState 垂直滚轴状态
   * @param weekDataPool 当前周课程数据
   */
  @Composable
  fun InnerCoursePageBottom(
    timeline: CourseTimeline,
    verticalScrollState: ScrollState,
    weekDataPool: CourseWeekDataPool,
  ) {
  }

  /**
   * 绘制在课表内层顶部
   * @param timeline 课表时间轴
   * @param verticalScrollState 垂直滚轴状态
   * @param weekDataPool 当前周课程数据
   */
  @Composable
  fun InnerCoursePageTop(
    timeline: CourseTimeline,
    verticalScrollState: ScrollState,
    weekDataPool: CourseWeekDataPool,
  ) {
  }
}