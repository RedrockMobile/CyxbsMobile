package com.cyxbs.pages.course.view

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.course.view.header.CourseHeaderCompose
import com.cyxbs.pages.course.view.header.CourseHeaderController
import com.cyxbs.pages.course.view.timeline.Content
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import kotlinx.datetime.DayOfWeek

/**
 * .
 *
 * @author 985892345
 * @date 2025/2/10
 */

@Stable
interface CourseController : CourseHeaderController {

  val courseBeginDate: Date

  val maxWeekPage: Int
    get() = 21

  /**
   * 初试显示的周数，如果传入 0 则显示整学期课表
   */
  val initialWeek: Int

  /**
   * 用于绘制一周内的课程
   * @param pagerState 课表 HorizontalPager 状态
   * @param scrollState 课表上下滚轴状态
   * @param beginDate 当周的开始日期，如果为 null 则为学期课表
   */
  @Composable
  fun WeekItemContent(
    pagerState: PagerState,
    scrollState: ScrollState,
    beginDate: Date?,
  )
}

@Composable
fun CourseCompose(
  controller: CourseController,
  modifier: Modifier = Modifier.fillMaxSize(),
  timeline: CourseTimeline = remember { CourseTimeline() },
) {
  val horizontalPagerState = rememberPagerState(
    initialPage = controller.initialWeek,
  ) { controller.maxWeekPage + 1 }
  Column(modifier = modifier.background(LocalAppColors.current.topBg)) {
    CourseHeaderCompose(
      controller = controller,
    )
    HorizontalPager(
      modifier = Modifier.fillMaxSize(),
      state = horizontalPagerState,
      key = { controller.courseBeginDate.plusWeeks(it).toString() },
    ) {
      val timelineWidth = 40.dp
      val date = controller.courseBeginDate.plusWeeks(it)
      Column {
        CourseWeekCompose(
          timelineWidth = timelineWidth,
          date = date,
          isSemester = it == 0,
        )
        timeline.Content(
          timelineWidth = timelineWidth,
        ) { scrollState ->
          controller.WeekItemContent(
            pagerState = horizontalPagerState,
            scrollState = scrollState,
            beginDate = if (it == 0) null else date,
          )
        }
      }
    }
  }
}

@Composable
private fun CourseWeekCompose(
  timelineWidth: Dp,
  date: Date,
  isSemester: Boolean,
) {
  Row(modifier = Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(
      modifier = Modifier.width(timelineWidth),
      text = if (!isSemester) "${date.monthNumber}月" else "",
      fontSize = 16.sp,
      color = LocalAppColors.current.tvLv1,
      textAlign = TextAlign.Center,
    )
    Row(modifier = Modifier.fillMaxSize()) {
      repeat(7) {
        Column(
          modifier = Modifier.weight(1F).fillMaxHeight(),
          verticalArrangement = Arrangement.SpaceEvenly,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            modifier = Modifier,
            text = getWeekStr(weekIndex = it, beginDayOfWeek = date.dayOfWeek),
            fontSize = 12.sp,
            color = LocalAppColors.current.tvLv1,
            textAlign = TextAlign.Center,
          )
          if (!isSemester) {
            Text(
              modifier = Modifier,
              text = "${date.plusDays(it).dayOfWeekNumber}日",
              fontSize = 11.sp,
              color = LocalAppColors.current.tvLv1,
              textAlign = TextAlign.Center,
            )
          }
        }
      }
    }
  }
}

private fun getWeekStr(weekIndex: Int, beginDayOfWeek: DayOfWeek): String {
  return when ((weekIndex + beginDayOfWeek.ordinal) % 7) {
    0 -> "周一"
    1 -> "周二"
    2 -> "周三"
    3 -> "周四"
    4 -> "周五"
    5 -> "周六"
    6 -> "周天"
    else -> error("不存在的 weekIndex=$weekIndex")
  }
}
