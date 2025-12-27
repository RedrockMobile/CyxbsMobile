package com.cyxbs.pages.course.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.pages.course.frame.header.CourseHeader
import com.cyxbs.pages.course.frame.decoration.AffairDecorationViewModel
import com.cyxbs.pages.course.frame.decoration.LinkLessonDecorationViewModel
import com.cyxbs.pages.course.frame.decoration.SelfLessonDecorationViewModel
import com.cyxbs.pages.course.view.decoration.CoursePageDecoration
import com.cyxbs.pages.course.view.item.CourseItemViewModel
import com.cyxbs.pages.course.view.item.affair.CreateAffairDecorationViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * 支持自适应宽高的课表框架
 *
 * @author 985892345
 * @date 2025/9/22
 */
@Stable
class AdaptiveCourseFrame : AbstractCourseFrame() {

  @Composable
  fun HomeCourseContent(modifier: Modifier) {
    AdaptiveHomeCourseFrameContent(
      modifier = modifier,
      frame = this,
    )
  }
}

@Composable
private fun AdaptiveHomeCourseFrameContent(
  modifier: Modifier,
  frame: AdaptiveCourseFrame,
) {
  val decorations = getDecoration()
  Column(modifier = modifier.background(LocalAppColors.current.topBg)) {
    CourseHeader(
      modifier = Modifier.height(50.dp),
      frame = frame,
    )
    HorizontalPager(
      modifier = Modifier.fillMaxSize(),
      state = frame.pagerState,
      pageContent = { page ->
        frame.HomeCoursePageContent(
          page = page,
          decorations = decorations,
        )
      },
    )
  }
}

@Composable
private fun getDecoration(): ImmutableList<CoursePageDecoration> {
  val courseItemViewModel = viewModel { CourseItemViewModel() }
  val selfLessonDecoration = viewModel {
    SelfLessonDecorationViewModel(
      hierarchy = courseItemViewModel.createOverlay()
    )
  }
  val linkLessonDecoration = viewModel {
    LinkLessonDecorationViewModel(
      hierarchy = courseItemViewModel.createOverlay()
    )
  }
  val affairDecoration = viewModel {
    AffairDecorationViewModel(
      hierarchy = courseItemViewModel.createOverlay()
    )
  }
  val createAffairDecoration = viewModel { CreateAffairDecorationViewModel() }
  return remember {
    persistentListOf(
      selfLessonDecoration, // 自己的课程
      affairDecoration, // 自己的事务
      linkLessonDecoration, // 关联人的课程
      createAffairDecoration, // 长按创建事务
    )
  }
}