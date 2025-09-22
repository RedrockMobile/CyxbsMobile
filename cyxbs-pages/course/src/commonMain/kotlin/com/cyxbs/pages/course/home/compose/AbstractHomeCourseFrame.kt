package com.cyxbs.pages.course.home.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.config.time.TodayNoEffect
import com.cyxbs.pages.course.home.compose.decoration.TodayDecoration
import com.cyxbs.pages.course.home.data.HomeLinkLessonDataProvider
import com.cyxbs.pages.course.home.data.HomeSelfLessonDataProvider
import com.cyxbs.pages.course.view.data.CourseDataProviderGroup
import com.cyxbs.pages.course.view.item.touch.LongPressCreate
import com.cyxbs.pages.course.view.page.CoursePageCompose
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import com.cyxbs.pages.course.view.week.CourseWeekCompose
import kotlinx.collections.immutable.persistentListOf

/**
 * 课表框架
 *
 * @author 985892345
 * @date 2025/9/22
 */
@Stable
abstract class AbstractHomeCourseFrame {

  // 课表时间轴
  open val timeline: CourseTimeline = CourseTimeline()

  // 课表起始日期，如果为 null 则不会显示号数
  open var beginDate: Date? by mutableStateOf(SchoolCalendar.getFirstMonDay())

  // 课表数据
  open val providerGroup: CourseDataProviderGroup = CourseDataProviderGroup(
    HomeSelfLessonDataProvider,
//    HomeAffairDataProvider, // todo 课表事务还未完成
    HomeLinkLessonDataProvider,
  )

  // 课表 HorizontalPager 状态
  open val pagerState: PagerState by lazy {
    PagerState(initialPage) { maxPage }
  }

  // 最大显示页数，添加整学期页
  open val maxPage: Int get() = 22

  // 课表初始页，按 beginDate 自动计算(如果有值)，超出 maxPage 时默认显示第一页
  open val initialPage: Int
    get() = getPage(TodayNoEffect)

  open fun getPage(date: Date): Int {
    val realBeginDate = beginDate?.weekBeginDate?.plusDays(timeline.beginDayOfWeek.ordinal) ?: return 0
    val page = (realBeginDate.daysUntil(date) / 7 + 1).coerceAtLeast(0)
    return if (page >= maxPage) 0 else page
  }
}

@Composable
fun AbstractHomeCourseFrame.HomeCoursePageContent(
  page: Int,
) {
  val date = beginDate?.plusWeeks(page - 1)
    ?.weekBeginDate?.plusDays(timeline.beginDayOfWeek.ordinal)
  Column {
    CourseWeekCompose(
      weekBeginDate = if (page == 0) null else date, // 整学期页不显示日期
      beginDayOfWeek = timeline.beginDayOfWeek,
    )
    CoursePageCompose(
      timeline = timeline,
      weekDataPool = providerGroup.getWeekDataPool(page),
      decorations = persistentListOf(
        TodayDecoration(weekBeginDate = if (page == 0) null else date),
        LongPressCreate(),
      ),
    )
  }
}