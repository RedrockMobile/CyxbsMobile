package com.cyxbs.pages.schedule.ui.dialog

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cyxbs.components.config.time.TodayNoEffect
import com.cyxbs.components.view.calendar.CalendarCompose
import com.cyxbs.components.view.calendar.state.rememberCalendarState
import com.cyxbs.components.view.wheel.WheelSelectBackground
import com.cyxbs.components.view.wheel.WheelSelectCompose
import kotlinx.collections.immutable.toPersistentList
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Clock

/**
 * 日期 + 时分选择器。
 *
 * 复刻老端 [com.cyxbs.pages.schedule.ui.dialog.CalendarDialog]：
 * - 上部：[CalendarCompose] 日期网格（默认从今天开始，禁止选过去日期）。
 * - 下部：两个 [WheelSelectCompose] 滚轮选择时/分。
 * - 回调：(year, month, day, hour, minute)，hour ∈ 0..23，minute ∈ 0..59。
 */
@Composable
fun ScheduleCalendarPickerDialog(
  show: Boolean,
  onDismiss: () -> Unit,
  onConfirm: (year: Int, month: Int, day: Int, hour: Int, minute: Int) -> Unit,
) {
  val calendarState = rememberCalendarState(
    startDate = TodayNoEffect,
  )
  val currentDateTime = remember {
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
  }
  val hourLine = remember { Animatable(currentDateTime.hour.toFloat()) }
  val minuteLine = remember { Animatable(currentDateTime.minute.toFloat()) }
  val hours = remember {
    (0..23).map { it.toString().padStart(2, '0') }.toPersistentList()
  }
  val minutes = remember {
    (0..59).map { it.toString().padStart(2, '0') }.toPersistentList()
  }

  ScheduleBottomSheet(
    show = show,
    onDismiss = onDismiss,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        text = "选择截止时间",
        style = MaterialTheme.typography.h6,
        modifier = Modifier
          .align(Alignment.CenterHorizontally)
          .padding(top = 16.dp, bottom = 8.dp),
      )

      // 日历网格（折叠态）
      CalendarCompose(
        modifier = Modifier
          .fillMaxWidth()
          .height(260.dp),
        state = calendarState,
      )

      // 时分滚轮
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(120.dp)
          .padding(horizontal = 40.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        WheelSelectBackground(
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        ) {
          WheelSelectCompose(
            selectedLine = hourLine,
            options = hours,
            modifier = Modifier.fillMaxSize(),
          )
        }

        Text(
          text = ":",
          modifier = Modifier.padding(horizontal = 8.dp),
          style = MaterialTheme.typography.h5,
        )

        WheelSelectBackground(
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        ) {
          WheelSelectCompose(
            selectedLine = minuteLine,
            options = minutes,
            modifier = Modifier.fillMaxSize(),
          )
        }
      }

      // 按钮
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        horizontalArrangement = Arrangement.End,
      ) {
        TextButton(onClick = onDismiss) {
          Text(text = "取消")
        }
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(
          onClick = {
            val date = calendarState.clickDate
            onConfirm(
              date.year,
              date.monthNumber,
              date.dayOfMonth,
              hourLine.value.roundToInt().coerceIn(0, 23),
              minuteLine.value.roundToInt().coerceIn(0, 59),
            )
            onDismiss()
          },
        ) {
          Text(text = "确定", color = MaterialTheme.colors.primary)
        }
      }
    }
  }
}
