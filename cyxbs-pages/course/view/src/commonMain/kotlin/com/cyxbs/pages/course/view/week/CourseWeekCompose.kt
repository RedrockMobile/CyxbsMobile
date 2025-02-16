package com.cyxbs.pages.course.view.week

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.Date
import kotlinx.datetime.DayOfWeek

/**
 * .
 *
 * @author 985892345
 * @date 2025/2/15
 */
@Composable
fun CourseWeekCompose(
  weekBeginDate: Date?, // 传递 nul 将不显示号数
  beginDayOfWeek: DayOfWeek,
  timelineWidth: Dp = 40.dp, // 与 timeline 一致
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(50.dp)
      .background(LocalAppColors.current.topBg),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      modifier = Modifier.width(timelineWidth),
      text = if (weekBeginDate != null) "${weekBeginDate.monthNumber}月" else "",
      fontSize = 16.sp,
      color = LocalAppColors.current.tvLv1,
      textAlign = TextAlign.Center,
    )
    Row(modifier = Modifier.fillMaxSize()) {
      repeat(7) {
        Column(
          modifier = Modifier
            .weight(1F)
            .fillMaxHeight(),
          verticalArrangement = Arrangement.SpaceEvenly,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            modifier = Modifier,
            text = getWeekStr(
              weekIndex = it,
              beginDayOfWeek = beginDayOfWeek
            ),
            fontSize = 12.sp,
            color = LocalAppColors.current.tvLv1,
            textAlign = TextAlign.Center,
          )
          if (weekBeginDate != null) {
            Text(
              modifier = Modifier,
              text = "${weekBeginDate.plusDays(it).dayOfMonth}日",
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
