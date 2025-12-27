package com.cyxbs.pages.course.view.week

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.Today
import com.cyxbs.components.config.time.add
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.compose.plusDsl
import com.cyxbs.components.utils.compose.rememberDerivedStateOfStructure
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
  showShadowDayOfWeek: DayOfWeek?, // 星期几展示今日阴影
  timelineWidth: Dp, // 与 timeline 一致
  scrollPaddingValues: PaddingValues,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(50.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      modifier = Modifier.width(timelineWidth),
      text = if (weekBeginDate != null) "${weekBeginDate.monthNumber}月" else "",
      fontSize = 16.sp,
      color = LocalAppColors.current.tvLv1,
      textAlign = TextAlign.Center,
    )
    val layoutDirection = LocalLayoutDirection.current
    Row(
      modifier = Modifier.fillMaxSize()
        .padding(
          start = scrollPaddingValues.calculateLeftPadding(layoutDirection),
          end = scrollPaddingValues.calculateRightPadding(layoutDirection),
        )
    ) {
      repeat(7) {
        val showToday = showShadowDayOfWeek == beginDayOfWeek.add(it)
        Column(
          modifier = Modifier
            .weight(1F)
            .fillMaxHeight()
            .plusDsl {
              if (showToday) {
                background(LocalAppColors.current.tvLv4, RoundedCornerShape(8.dp))
              }
            },
          verticalArrangement = Arrangement.SpaceEvenly,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            modifier = Modifier,
            text = getWeekStr(beginDayOfWeek.add(it)),
            fontSize = if (weekBeginDate != null) 12.sp else 13.sp,
            color = if (showToday) LocalAppColors.current.whiteBlack else LocalAppColors.current.tvLv1,
            textAlign = TextAlign.Center,
          )
          if (weekBeginDate != null) {
            Text(
              modifier = Modifier,
              text = "${weekBeginDate.plusDays(it).dayOfMonth}日",
              fontSize = 11.sp,
              color = if (showToday) LocalAppColors.current.whiteBlack else LocalAppColors.current.tvDefault,
              textAlign = TextAlign.Center,
            )
          }
        }
      }
    }
  }
}

private fun getWeekStr(dayOfWeek: DayOfWeek): String {
  return when (dayOfWeek) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周天"
    else -> error("???")
  }
}
