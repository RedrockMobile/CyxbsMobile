package com.cyxbs.pages.course.home.compose

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
import com.cyxbs.pages.course.api.IAdaptiveHomeCourseFrame
import com.cyxbs.pages.course.home.item.decoration.AffairDecorationViewModel
import com.cyxbs.pages.course.home.item.decoration.LinkLessonDecorationViewModel
import com.cyxbs.pages.course.home.item.decoration.SelfLessonDecorationViewModel
import com.cyxbs.pages.course.view.decoration.CoursePageDecoration
import com.cyxbs.pages.course.view.item.CourseItemViewModel
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * 支持自适应宽高的课表框架
 *
 * @author 985892345
 * @date 2025/9/22
 */
@Stable
@ImplProvider(clazz = IAdaptiveHomeCourseFrame::class)
class AdaptiveHomeCourseFrame : AbstractHomeCourseFrame(), IAdaptiveHomeCourseFrame {

  @Composable
  override fun HomeCourseContent(modifier: Modifier) {
    AdaptiveHomeCourseFrameContent(
      modifier = modifier,
      frame = this,
    )
  }
}

@Composable
private fun AdaptiveHomeCourseFrameContent(
  modifier: Modifier,
  frame: AdaptiveHomeCourseFrame,
) {
  val decorations = getDecoration()
  Column(modifier = modifier.background(LocalAppColors.current.topBg)) {
    HomeCourseHeader(
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
      hierarchy = courseItemViewModel.createOverlay(
        comparator = SelfLessonDecorationViewModel.Comparable
      )
    )
  }
  val linkLessonDecoration = viewModel {
    LinkLessonDecorationViewModel(
      hierarchy = courseItemViewModel.createOverlay(
        comparator = LinkLessonDecorationViewModel.Comparable
      )
    )
  }
  val affairDecoration = viewModel {
    AffairDecorationViewModel(
      hierarchy = courseItemViewModel.createOverlay(
        comparator = AffairDecorationViewModel.Comparable
      )
    )
  }
  return remember(
    selfLessonDecoration,
    linkLessonDecoration,
    affairDecoration,
  ) {
    persistentListOf(
      selfLessonDecoration,
      affairDecoration,
      linkLessonDecoration,
    )
  }
}