package com.cyxbs.pages.course.home.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.TodayNoEffect
import com.cyxbs.components.utils.compose.BottomSheetState
import com.cyxbs.pages.course.home.data.HomeAffairDataProvider
import com.cyxbs.pages.course.home.data.HomeLinkLessonDataProvider
import com.cyxbs.pages.course.home.data.HomeSelfLessonDataProvider
import com.cyxbs.pages.course.service.MobileHomeCourseServiceImpl
import com.cyxbs.pages.course.view.data.CourseDataProviderGroup
import com.cyxbs.pages.course.view.frame.CourseBottomSheetFrame
import com.cyxbs.pages.course.view.header.CourseHeader
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * .
 *
 * @author 985892345
 * @date 2025/2/15
 */
@Composable
fun HomeCourseCompose(
  modifier: Modifier,
  bottomBarHeight: Dp,
  outerHeader: @Composable (BottomSheetState) -> Unit,
) {
  HomeCourseFrame.set(
    bottomBarHeight = bottomBarHeight,
    outerHeader = outerHeader,
  )
  Box(modifier) {
    HomeCourseFrame.Content()
  }
}

private object HomeCourseFrame : CourseBottomSheetFrame() {

  private var outerHeader: @Composable (BottomSheetState) -> Unit by mutableStateOf({})

  override val beginDate: Date = TodayNoEffect.weekBeginDate

  override val providerGroup: CourseDataProviderGroup = CourseDataProviderGroup(
    HomeSelfLessonDataProvider(),
    HomeLinkLessonDataProvider(),
    HomeAffairDataProvider(),
  )

  override var peekHeight: Dp by mutableStateOf(super.peekHeight)

  fun set(
    bottomBarHeight: Dp,
    outerHeader: @Composable (BottomSheetState) -> Unit,
  ) {
    peekHeight = super.peekHeight + bottomBarHeight
    HomeCourseFrame.outerHeader = outerHeader
  }

  @Composable
  override fun CourseBottomSheetHeader() {
    Box {
      CourseHeader(controller = this@HomeCourseFrame, modifier = Modifier.graphicsLayer {
        alpha = MobileHomeCourseServiceImpl.headerAlpha
      })
      outerHeader(bottomSheetState)
    }
    LaunchedEffect(Unit) {
      clickBackFlow.onEach {
        pagerState.animateScrollToPage(initialPage)
      }.launchIn(this)
    }
  }

  @Composable
  override fun CourseHorizontalPager(pageContent: @Composable (PagerScope.(page: Int) -> Unit)) {
    HorizontalPager(
      modifier = Modifier.fillMaxSize().background(LocalAppColors.current.topBg).graphicsLayer {
        alpha = MobileHomeCourseServiceImpl.contentAlpha
      },
      state = pagerState,
      pageContent = pageContent,
    )
    OnCourseHorizontalPager()
  }
}