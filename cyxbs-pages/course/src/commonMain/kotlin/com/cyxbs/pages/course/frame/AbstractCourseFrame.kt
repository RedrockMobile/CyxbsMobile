package com.cyxbs.pages.course.frame

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.config.time.Today
import com.cyxbs.components.config.time.TodayNoEffect
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.compose.rememberDerivedStateOfStructure
import com.cyxbs.pages.course.frame.decoration.AffairDecorationViewModel
import com.cyxbs.pages.course.frame.decoration.LinkLessonDecorationViewModel
import com.cyxbs.pages.course.frame.decoration.SelfLessonDecorationViewModel
import com.cyxbs.pages.course.frame.item.CourseAffairItem
import com.cyxbs.pages.course.frame.item.LinkLessonItem
import com.cyxbs.pages.course.frame.item.SelfLessonItem
import com.cyxbs.pages.course.view.decoration.CoursePageDecoration
import com.cyxbs.pages.course.view.item.CourseItemHierarchy
import com.cyxbs.pages.course.view.item.CourseItemViewModel
import com.cyxbs.pages.course.view.item.affair.CreateAffairDecorationViewModel
import com.cyxbs.pages.course.view.page.CoursePageCompose
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import com.cyxbs.pages.course.view.week.CourseWeekCompose
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.DayOfWeek

/**
 * 课表框架
 *
 * @author 985892345
 * @date 2025/9/22
 */
@Stable
abstract class AbstractCourseFrame {

  companion object {

    internal val LocalAbstractCourseFrame = staticCompositionLocalOf<AbstractCourseFrame> { error("未设置 AbstractCourseFrame") }

    @get:Composable
    val current: AbstractCourseFrame
      get() = LocalAbstractCourseFrame.current
  }

  // 课表时间轴
  open val timeline: CourseTimeline = CourseTimeline()

  // 课表起始日期，如果为 null 则不会显示号数
  open var beginDate: StateFlow<Date?> = SchoolCalendar.observeFirstMonDayNullable()

  // 课表 HorizontalPager 状态
  open val pagerState: PagerState by lazy {
    PagerState(initialPage) { maxPage }
  }

  // 课表最大周数
  open val maxWeak: Int get() = 21

  // 最大显示页数
  open val maxPage: Int get() = maxWeak + 1 // 因为第一页为整学期页，所以加1

  // 课表初始页，按 beginDate 自动计算(如果有值)，超出 maxPage 时默认显示第一页
  open val initialPage: Int
    get() = getPage(TodayNoEffect)

  open fun getPage(date: Date): Int {
    val realBeginDate =
      beginDate.value?.weekBeginDate?.plusDays(timeline.beginDayOfWeek.ordinal) ?: return 0
    val page = (realBeginDate.daysUntil(date) / 7 + 1).coerceAtLeast(0)
    return if (page >= maxPage) 0 else page
  }
}

@Composable
fun AbstractCourseFrame.HomeCoursePageContent(
  page: Int,
  decorations: ImmutableList<CoursePageDecoration>,
) {
  val pageBeginDate = beginDate.collectAsState().value?.plusWeeks(page - 1)
    ?.weekBeginDate?.plusDays(timeline.beginDayOfWeek.ordinal)
  val timelineWidth = 40.dp
  val scrollPaddingValues = PaddingValues(top = 4.dp, bottom = 16.dp)
  val showShadowDayOfWeek by rememberDerivedStateOfStructure(pageBeginDate) {
    if (page == 0) Today.dayOfWeek else {
      pageBeginDate?.daysUntil(Today)?.takeIf { it in 0..6 }?.let { Today.dayOfWeek }
    }
  }
  Column(
    modifier = Modifier.drawTodayShadow(
      showShadowDayOfWeek = showShadowDayOfWeek,
      beginDayOfWeek = timeline.beginDayOfWeek,
      timelineWidth = timelineWidth,
      scrollPaddingValues = scrollPaddingValues
    )
  ) {
    CourseWeekCompose(
      weekBeginDate = if (page == 0) null else pageBeginDate, // 整学期页不显示日期
      beginDayOfWeek = timeline.beginDayOfWeek,
      showShadowDayOfWeek = showShadowDayOfWeek,
      timelineWidth = timelineWidth,
      scrollPaddingValues = scrollPaddingValues,
    )
    CoursePageCompose(
      page = page,
      timeline = timeline,
      timelineWidth = timelineWidth,
      scrollPaddingValues = scrollPaddingValues,
      decorations = decorations,
    )
  }
}

@Composable
internal fun createBaseCoursePageDecorations(): ImmutableList<CoursePageDecoration> {
  val selfLessonHierarchy = remember { CourseItemHierarchy<SelfLessonItem>() }
  val selfLessonDecoration = viewModel { SelfLessonDecorationViewModel(selfLessonHierarchy) }

  val linkLessonHierarchy = remember { CourseItemHierarchy<LinkLessonItem>() }
  val linkLessonDecoration = viewModel { LinkLessonDecorationViewModel(linkLessonHierarchy) }

  val affairHierarchy = remember { CourseItemHierarchy<CourseAffairItem>() }
  val affairDecoration = viewModel { AffairDecorationViewModel(affairHierarchy) }

  val createAffairDecoration = viewModel { CreateAffairDecorationViewModel() }

  val courseItemViewModel = viewModel { CourseItemViewModel(selfLessonHierarchy, affairHierarchy, linkLessonHierarchy) }

  return remember {
    persistentListOf(
      selfLessonDecoration, // 自己的课程
      affairDecoration, // 自己的事务
      linkLessonDecoration, // 关联人的课程
      createAffairDecoration, // 长按创建事务
    )
  }
}

// 绘制课表今日的背景阴影
@Composable
private fun Modifier.drawTodayShadow(
  showShadowDayOfWeek: DayOfWeek?, // 星期几展示今日阴影
  beginDayOfWeek: DayOfWeek,
  timelineWidth: Dp,
  scrollPaddingValues: PaddingValues,
): Modifier {
  showShadowDayOfWeek ?: return this
  val todayIndex = (showShadowDayOfWeek.ordinal - beginDayOfWeek.ordinal + 7) % 7
  val color = 0x93E8F0FC.dark(0x26000101)
  return drawBehind {
    val paddingStart = scrollPaddingValues.calculateStartPadding(layoutDirection).toPx()
    val paddingEnd = scrollPaddingValues.calculateEndPadding(layoutDirection).toPx()
    val width = (size.width - timelineWidth.toPx() - paddingStart - paddingEnd) / 7
    val offsetX = timelineWidth.toPx() + paddingStart + todayIndex * width
    drawRoundRect(
      color = color,
      topLeft = Offset(x = offsetX, y = 0F),
      size = Size(width = width, height = 10.dp.toPx()),
      cornerRadius = CornerRadius(x = 8.dp.toPx(), y = 8.dp.toPx())
    )
    drawRect(
      color = color,
      topLeft = Offset(x = offsetX, y = 10F),
      size = Size(width = width, height = size.height - 10.dp.toPx()),
    )
  }
}