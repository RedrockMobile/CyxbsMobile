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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.course.frame.item.DefaultCourseAffairItemFactory
import com.cyxbs.pages.course.frame.item.DefaultCourseLessonItemFactory
import com.cyxbs.pages.course.frame.item.DefaultCourseLinkLessonItemFactory
import com.cyxbs.pages.course.view.AbstractCourseFrame
import com.cyxbs.pages.course.view.HomeCoursePageContent
import com.cyxbs.pages.course.view.decoration.CoursePageDecorationManager
import com.cyxbs.pages.course.view.decoration.impl.AffairPageDecoration
import com.cyxbs.pages.course.view.decoration.impl.CourseLessonPageDecoration
import com.cyxbs.pages.course.view.decoration.impl.LinkLessonPageDecoration
import com.cyxbs.pages.course.view.decoration.impl.SelfLessonPageDecoration
import com.cyxbs.pages.course.view.page.CourseFrameHeader

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
    val decorationManager = createCoursePageDecorationManager(this)
    CompositionLocalProvider(
      Local provides this,
      CoursePageDecorationManager.Local provides decorationManager,
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
        )
      },
    )
  }
}

@Composable
private fun createCoursePageDecorationManager(
  frame: AdaptiveCourseFrame
): CoursePageDecorationManager {
  val coroutineScope = rememberCoroutineScope()
  val selfStuNum = IAccountService::class.impl().stuNum
  if (selfStuNum != frame.stuNum) {
    return remember {
      CoursePageDecorationManager(
        courseFrame = frame,
        courseCoroutineScope = coroutineScope,
        CourseLessonPageDecoration(
          stuNum = frame.stuNum,
          platformItemFactory = DefaultCourseLessonItemFactory
        ),
      )
    }
  } else {
    return remember {
      CoursePageDecorationManager(
        courseFrame = frame,
        courseCoroutineScope = coroutineScope,
        SelfLessonPageDecoration(platformItemFactory = DefaultCourseLessonItemFactory), // 自己的课程
        AffairPageDecoration(
          courseFrame = frame,
          platformItemFactory = DefaultCourseAffairItemFactory
        ), // 自己的事务
        LinkLessonPageDecoration(platformItemFactory = DefaultCourseLinkLessonItemFactory), // 关联人的课程
      )
    }
  }
}