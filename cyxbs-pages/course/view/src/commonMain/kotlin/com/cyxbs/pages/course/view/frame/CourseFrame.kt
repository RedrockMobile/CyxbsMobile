package com.cyxbs.pages.course.view.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.pages.course.view.data.CourseDataProviderGroup
import com.cyxbs.pages.course.view.page.CoursePageCompose
import com.cyxbs.pages.course.view.timeline.CourseTimeline

/**
 * 课表 UI 框架
 *
 * @author 985892345
 * @date 2025/2/16
 */
@Stable
abstract class CourseFrame {

  // 课表数据，子类应该重写为一个常量
  abstract val providerGroup: CourseDataProviderGroup

  // 课表能展示的最大页数
  abstract val maxPage: Int

  // 课表时间轴
  open val timeline: CourseTimeline = CourseTimeline()

  // 课表初始页
  open val initialPage: Int
    get() = 0

  // 课表 HorizontalPager 状态
  val pagerState: PagerState by lazy {
    PagerState(initialPage) { maxPage }
  }

  @Composable
  fun Content() {
    CourseCompose()
    OnCourseCompose()
  }

  @Composable
  open fun CourseCompose() {
    CourseHorizontalPager {
      CoursePageContent(this, it)
    }
  }

  @Composable
  open fun OnCourseCompose() {
    DisposableEffect(Unit) {
      providerGroup.onBindCourseCompose(timeline)
      onDispose {
        providerGroup.onUnbindCourseCompose()
      }
    }
  }

  @Composable
  open fun CourseHorizontalPager(pageContent: @Composable PagerScope.(page: Int) -> Unit) {
    HorizontalPager(
      modifier = Modifier.fillMaxSize().background(LocalAppColors.current.topBg),
      state = pagerState,
      pageContent = pageContent,
    )
  }

  @Composable
  open fun CoursePageContent(pagerScope: PagerScope, page: Int) {
    CoursePageCompose(
      timeline = timeline,
      weekDataPools = providerGroup.getWeekDataPool(page)
    )
  }
}