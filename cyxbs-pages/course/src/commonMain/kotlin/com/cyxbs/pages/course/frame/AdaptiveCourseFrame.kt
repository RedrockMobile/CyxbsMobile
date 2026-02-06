package com.cyxbs.pages.course.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.pages.course.frame.header.CourseHeader

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
  val decorations = createBaseCoursePageDecorations()
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