package com.cyxbs.pages.course.home.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.config.time.next
import com.cyxbs.components.config.time.toMinuteTimeDate
import com.cyxbs.components.utils.compose.BottomSheetValueState
import com.cyxbs.pages.course.home.data.HomeAffairDataProvider
import com.cyxbs.pages.course.home.data.HomeLinkLessonDataProvider
import com.cyxbs.pages.course.home.data.HomeSelfLessonDataProvider
import com.cyxbs.pages.course.view.data.CourseDataProviderGroup
import com.cyxbs.pages.course.view.frame.CourseBottomSheetFrame
import com.cyxbs.pages.course.view.header.CourseHeader
import com.cyxbs.pages.course.view.item.BottomSheetItemHeader
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.item.HintBottomSheetItemHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.max
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 移动端主页课表框架
 *
 * 展开时：
 * 课表主体:     0.0 --------> 1.0
 * 课表头部:     0.0 -> 0.0 -> 1.0
 * 主界面头部:   1.0 -> 0.0 -> 0.0
 * 折叠时：
 * 课表主体:     1.0 --------> 0.0
 * 课表头部:     1.0 -> 0.0 -> 0.0
 * 主界面头部:   0.0 -> 0.0 -> 1.0
 *
 * @author 985892345
 * @date 2025/2/15
 */
@Stable
class MobileHomeCourseFrame : CourseBottomSheetFrame() {

  override var beginDate: Date? by mutableStateOf(SchoolCalendar.getFirstMonDay())

  override val providerGroup: CourseDataProviderGroup = CourseDataProviderGroup(
    HomeSelfLessonDataProvider(),
    HomeAffairDataProvider(),
    HomeLinkLessonDataProvider(),
  )

  override var peekHeight: Dp by mutableStateOf(super.peekHeight)

  fun set(
    bottomBarHeight: Dp,
  ) {
    peekHeight = super.peekHeight + bottomBarHeight
  }

  @Composable
  override fun CourseBottomSheetHeader() {
    Box {
      CourseHeader(
        controller = this@MobileHomeCourseFrame,
        modifier = Modifier.graphicsLayer {
          alpha = max(bottomSheetState.fraction * 2 - 1, 0F)
        }
      )
      // 主页课表外层 header
      MobileHomeCourseHeader(
        frame = this@MobileHomeCourseFrame,
        modifier = Modifier.graphicsLayer {
          alpha = max(1 - bottomSheetState.fraction * 2, 0F)
        },
      )
    }
    LaunchedEffect(Unit) {
      clickBackFlow.onEach {
        pagerState.animateScrollToPage(initialPage)
      }.launchIn(this)
    }
    LaunchedEffect(Unit) {
      if (beginDate == null) {
        // beginDate 未初始化，则进行等待
        val beginDateJob = launch {
          beginDate = SchoolCalendar.observeFirstMonDay().first()
        }
        val selectPageJon = launch {
          beginDateJob.join() // beginDate 初始化后跳到 initialPage
          pagerState.scrollToPage(initialPage)
        }
        launch {
          bottomSheetState.stateFlow.first { it == BottomSheetValueState.Expanded }
          selectPageJon.cancel() // 如果触发一次展开，则取消回到 initialPage
        }
      }
    }
  }

  @Composable
  override fun CourseHorizontalPager(pageContent: @Composable (PagerScope.(page: Int) -> Unit)) {
    HorizontalPager(
      modifier = Modifier.fillMaxSize().background(LocalAppColors.current.topBg).graphicsLayer {
        alpha = bottomSheetState.fraction
      },
      state = pagerState,
      pageContent = pageContent,
    )
    OnCourseHorizontalPager()
  }
}