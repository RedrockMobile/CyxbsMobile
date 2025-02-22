package com.cyxbs.pages.course.page.course.ui.home.compose

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
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.TodayNoEffect
import com.cyxbs.components.utils.compose.BottomSheetState
import com.cyxbs.pages.course.service.HomeCourseServiceImpl
import com.cyxbs.pages.course.view.frame.CourseBottomSheetFrame
import com.cyxbs.pages.course.view.header.CourseHeader
import com.cyxbs.pages.course.view.item.CourseItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.DayOfWeek

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
    HomeCourseFrame.CourseCompose()
  }
}

private object HomeCourseFrame : CourseBottomSheetFrame() {

  private var outerHeader: @Composable (BottomSheetState) -> Unit by mutableStateOf({})

  override val beginDate: Date = TodayNoEffect.weekBeginDate

  override var peekHeight: Dp by mutableStateOf(super.peekHeight)

  fun set(
    bottomBarHeight: Dp,
    outerHeader: @Composable (BottomSheetState) -> Unit,
  ) {
    Snapshot.withoutReadObservation {
      peekHeight = super.peekHeight + bottomBarHeight
      this.outerHeader = outerHeader
    }
  }

  override fun getDayItems(week: Int, dayOfWeek: DayOfWeek): ImmutableList<CourseItem> {
    return persistentListOf()
  }

  @Composable
  override fun CourseBottomSheetHeader() {
    Box {
      CourseHeader(controller = this@HomeCourseFrame, modifier = Modifier.graphicsLayer {
        alpha = HomeCourseServiceImpl.headerAlpha
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
        alpha = HomeCourseServiceImpl.contentAlpha
      },
      state = pagerState,
      pageContent = pageContent,
    )
    OnCourseHorizontalPager()
  }
}