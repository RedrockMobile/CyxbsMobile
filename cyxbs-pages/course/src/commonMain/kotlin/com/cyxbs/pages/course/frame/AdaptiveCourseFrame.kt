package com.cyxbs.pages.course.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.course.frame.item.DefaultPlatformCourseAffairItemFactory
import com.cyxbs.pages.course.frame.item.DefaultPlatformCourseLessonItemFactory
import com.cyxbs.pages.course.frame.item.DefaultPlatformCourseLinkLessonItemFactory
import com.cyxbs.pages.course.frame.item.DefaultPlatformCourseSelfLessonItemFactory
import com.cyxbs.pages.course.view.decoration.CoursePageDecoration
import com.cyxbs.pages.course.view.frame.AbstractCourseFrame
import com.cyxbs.pages.course.view.frame.HomeCoursePageContent
import com.cyxbs.pages.course.view.frame.decoration.AffairDecorationViewModel
import com.cyxbs.pages.course.view.frame.decoration.LessonDecorationViewModel
import com.cyxbs.pages.course.view.frame.decoration.LinkLessonDecorationViewModel
import com.cyxbs.pages.course.view.frame.decoration.SelfLessonDecorationViewModel
import com.cyxbs.pages.course.view.frame.header.CourseFrameHeader
import com.cyxbs.pages.course.view.item.CourseItemHierarchy
import com.cyxbs.pages.course.view.item.CourseItemViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * 支持自适应宽高的课表框架
 *
 * @author 985892345
 * @date 2025/9/22
 */
@Stable
class AdaptiveCourseFrame(
  val stuNum: String,
) : AbstractCourseFrame() {

  @Composable
  fun HomeCourseContent(modifier: Modifier) {
    CompositionLocalProvider(
      LocalAbstractCourseFrame provides this
    ) {
      AdaptiveHomeCourseFrameContent(
        modifier = modifier,
        frame = this,
      )
    }
  }
}

@Composable
private fun AdaptiveHomeCourseFrameContent(
  modifier: Modifier,
  frame: AdaptiveCourseFrame,
) {
  val decorations = createCoursePageDecorations(frame)
  Column(modifier = modifier.background(LocalAppColors.current.topBg)) {
    CourseFrameHeader(
      modifier = Modifier.height(50.dp),
      frame = frame,
      linkBtnVisibility = false,
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
private fun createCoursePageDecorations(
  frame: AdaptiveCourseFrame
): ImmutableList<CoursePageDecoration> {
  val selfStuNum = IAccountService::class.impl().stuNum
  if (selfStuNum != frame.stuNum) {
    val lessonDecoration = viewModel {
      LessonDecorationViewModel(
        stuNum = frame.stuNum,
        hierarchy = CourseItemHierarchy(),
        platformItemFactory = DefaultPlatformCourseLessonItemFactory,
      )
    }
    viewModel {
      CourseItemViewModel(
        lessonDecoration.hierarchy,
      )
    }
    return remember {
      persistentListOf(
        lessonDecoration,
      )
    }
  } else {
    val selfLessonDecoration = viewModel {
      SelfLessonDecorationViewModel(
        hierarchy = CourseItemHierarchy(),
        platformItemFactory = DefaultPlatformCourseSelfLessonItemFactory,
      )
    }

    val linkLessonDecoration = viewModel {
      LinkLessonDecorationViewModel(
        hierarchy = CourseItemHierarchy(),
        platformItemFactory = DefaultPlatformCourseLinkLessonItemFactory,
      )
    }

    val affairDecoration = viewModel {
      AffairDecorationViewModel(
        courseFrame = frame,
        hierarchy = CourseItemHierarchy(),
        platformItemFactory = DefaultPlatformCourseAffairItemFactory,
      )
    }
    viewModel {
      CourseItemViewModel(
        selfLessonDecoration.hierarchy,
        affairDecoration.hierarchy,
        linkLessonDecoration.hierarchy
      )
    }
    return remember {
      persistentListOf(
        selfLessonDecoration, // 自己的课程
        affairDecoration, // 自己的事务
        linkLessonDecoration, // 关联人的课程
      )
    }
  }
}