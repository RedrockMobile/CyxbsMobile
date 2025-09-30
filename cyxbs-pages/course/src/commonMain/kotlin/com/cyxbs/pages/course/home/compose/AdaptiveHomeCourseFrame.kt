package com.cyxbs.pages.course.home.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.pages.course.api.IAdaptiveHomeCourseFrame
import com.g985892345.provider.api.annotation.ImplProvider

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
        )
      },
    )
  }
  DisposableEffect(frame) {
    frame.providerGroup.onBindCourseCompose(frame.timeline)
    onDispose {
      frame.providerGroup.onUnbindCourseCompose()
    }
  }
}