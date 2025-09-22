package com.cyxbs.pages.course.home.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.config.time.Today
import com.cyxbs.components.config.time.TodayNoEffect
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.compose.rememberDerivedStateOfStructure
import com.cyxbs.components.view.ui.BottomSheetState
import com.cyxbs.pages.course.api.IMobileHomeCourseFrame
import com.cyxbs.pages.course.home.data.HomeAffairDataProvider
import com.cyxbs.pages.course.home.data.HomeLinkLessonDataProvider
import com.cyxbs.pages.course.home.data.HomeSelfLessonDataProvider
import com.cyxbs.pages.course.view.data.CourseDataProviderGroup
import com.cyxbs.pages.course.view.item.touch.LongPressCreate
import com.cyxbs.pages.course.view.page.CoursePageCompose
import com.cyxbs.pages.course.view.page.CoursePageDecoration
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import com.cyxbs.pages.course.view.week.CourseWeekCompose
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.collections.immutable.persistentListOf

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
@ImplProvider(clazz = IMobileHomeCourseFrame::class)
class MobileHomeCourseFrame : IMobileHomeCourseFrame {

  // 课表时间轴
  val timeline: CourseTimeline = CourseTimeline()

  // 底部抽屉状态
  override val bottomSheetState by lazy {
    BottomSheetState()
  }

  // 课表 HorizontalPager 状态
  override val pagerState: PagerState by lazy {
    PagerState(initialPage) { maxPage }
  }

  // 课表起始日期，如果为 null 则不会显示号数
  var beginDate: Date? by mutableStateOf(SchoolCalendar.getFirstMonDay())

  // 课表数据
  val providerGroup: CourseDataProviderGroup = CourseDataProviderGroup(
    HomeSelfLessonDataProvider,
//    HomeAffairDataProvider, // todo 课表事务还未完成
    HomeLinkLessonDataProvider,
  )

  var peekHeight: Dp by mutableStateOf(70.dp)

  // 最大显示页数，添加整学期页
  val maxPage: Int get() = 22

  // 课表初始页，按 beginDate 自动计算(如果有值)，超出 maxPage 时默认显示第一页
  val initialPage: Int
    get() = getPage(TodayNoEffect)

  fun getPage(date: Date): Int {
    val realBeginDate = beginDate?.weekBeginDate?.plusDays(timeline.beginDayOfWeek.ordinal) ?: return 0
    val page = (realBeginDate.daysUntil(date) / 7 + 1).coerceAtLeast(0)
    return if (page >= maxPage) 0 else page
  }

  fun set(
    bottomBarHeight: Dp,
  ) {
    peekHeight = 70.dp + bottomBarHeight
  }

  @Composable
  override fun HomeCourseContent(modifier: Modifier, bottomBarHeight: Dp) {
    MobileHomeCourseFrameContent(
      modifier = modifier,
      frame = this,
    )
    SideEffect {
      set(bottomBarHeight)
    }
  }
}

@Composable
private fun MobileHomeCourseFrameContent(
  modifier: Modifier,
  frame: MobileHomeCourseFrame,
) {
  MobileHomeBottomSheet(
    modifier = modifier,
    frame = frame,
    header = { MobileHomeCourseHeader(modifier = Modifier, frame = frame) },
  ) {
    HorizontalPager(
      modifier = Modifier.fillMaxSize().background(LocalAppColors.current.topBg).graphicsLayer {
        alpha = frame.bottomSheetState.fraction
      },
      state = frame.pagerState,
      pageContent = { page ->
        MobileHomeCoursePageContent(
          frame = frame,
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

@Composable
private fun MobileHomeCoursePageContent(
  frame: MobileHomeCourseFrame,
  page: Int,
) {
  val date = frame.beginDate?.plusWeeks(page - 1)
    ?.weekBeginDate?.plusDays(frame.timeline.beginDayOfWeek.ordinal)
  Column {
    CourseWeekCompose(
      weekBeginDate = if (page == 0) null else date, // 整学期页不显示日期
      beginDayOfWeek = frame.timeline.beginDayOfWeek,
    )
    CoursePageCompose(
      timeline = frame.timeline,
      weekDataPool = frame.providerGroup.getWeekDataPool(page),
      decorations = persistentListOf(
        TodayDecoration(weekBeginDate = if (page == 0) null else date),
        LongPressCreate(),
      ),
    )
  }
}

@Stable
class TodayDecoration(
  val weekBeginDate: Date?,
) : CoursePageDecoration {
  @Composable
  override fun OuterCoursePage(
    scrollPaddingValues: PaddingValues,
    timelineWidth: Dp,
    content: @Composable () -> Unit
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      val todayIndex by rememberDerivedStateOfStructure(weekBeginDate) {
        weekBeginDate?.daysUntil(Today) ?: Today.dayOfWeek.ordinal
      }
      if (todayIndex in 0..6) {
        Spacer(modifier = Modifier.layout { measurable, constraints ->
          val startPadding = timelineWidth.roundToPx() + scrollPaddingValues.calculateStartPadding(layoutDirection).roundToPx()
          val endPadding = scrollPaddingValues.calculateEndPadding(layoutDirection).roundToPx()
          val placeable = measurable.measure(
            Constraints.fixed(
              width = (constraints.maxWidth - startPadding - endPadding) / 7,
              height = constraints.maxHeight,
            )
          )
          layout(placeable.width, placeable.height) {
            placeable.placeRelative(todayIndex * placeable.width + startPadding, 0)
          }
        }.background(0x93E8F0FC.dark(0x26000101)))
      }
      content()
    }
  }
}