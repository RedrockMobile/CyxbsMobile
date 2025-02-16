package com.cyxbs.pages.course.view.frame

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.TodayNoEffect
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.page.CoursePageCompose
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.DayOfWeek

/**
 * 课表 UI 框架
 *
 * @author 985892345
 * @date 2025/2/16
 */
@Stable
abstract class CourseFrame {

  // 课表起始日期，如果为 null 则不会显示号数
  abstract val beginDate: Date?

  // 获取单天的 items，如果 week 为 0 则表示是整学期的 item
  abstract fun getDayItems(week: Int, dayOfWeek: DayOfWeek): ImmutableList<CourseItem>

  // 课表时间轴
  open val timeline: CourseTimeline = CourseTimeline()

  // 课表最大显示页数
  open val maxPage: Int
    get() = 21

  // 课表初始页，按 beginDate 自动计算(如果有值)，超出 maxPage 时默认显示第一页
  open val initialPage: Int
    get() {
      val page = beginDate?.daysUntil(TodayNoEffect)?.div(7)?.coerceAtLeast(0) ?: 0
      return if (page >= maxPage) 0 else page
    }

  // 课表 HorizontalPager 状态
  val pagerState by lazy {
    PagerState(initialPage) { maxPage }
  }

  // HorizontalPager Modifier
  open val horizontalPagerModifier: Modifier
    @Composable
    get() = Modifier.fillMaxSize()

  @Composable
  open fun CourseCompose() {
    CourseHorizontalPager {
      CoursePageContent(it)
    }
  }

  @Composable
  open fun CourseHorizontalPager(pageContent: @Composable PagerScope.(page: Int) -> Unit) {
    HorizontalPager(
      modifier = horizontalPagerModifier,
      state = pagerState,
      pageContent = pageContent,
    )
  }

  @Composable
  open fun PagerScope.CoursePageContent(page: Int) {
    val scrollState = rememberScrollState()
    CoursePageCompose(
      timeline = timeline,
      beginDayOfWeek = beginDate?.dayOfWeek ?: DayOfWeek.MONDAY,
      verticalScrollState = scrollState,
      items = {
        getDayItems(page, it)
      }
    )
  }
}