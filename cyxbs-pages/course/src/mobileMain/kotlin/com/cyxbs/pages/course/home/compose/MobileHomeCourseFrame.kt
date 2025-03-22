package com.cyxbs.pages.course.home.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.pages.course.home.data.HomeAffairDataProvider
import com.cyxbs.pages.course.home.data.HomeLinkLessonDataProvider
import com.cyxbs.pages.course.home.data.HomeSelfLessonDataProvider
import com.cyxbs.pages.course.view.data.CourseDataProviderGroup
import com.cyxbs.pages.course.view.frame.CourseBottomSheetFrame

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
    HomeSelfLessonDataProvider,
    HomeAffairDataProvider(),
    HomeLinkLessonDataProvider,
  )

  override var peekHeight: Dp by mutableStateOf(super.peekHeight)

  fun set(
    bottomBarHeight: Dp,
  ) {
    peekHeight = super.peekHeight + bottomBarHeight
  }

  @Composable
  override fun CourseHeader(modifier: Modifier) {
    MobileHomeCourseHeader(modifier= modifier, frame = this)
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
  }
}