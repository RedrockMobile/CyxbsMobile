package com.cyxbs.components.view.calendar

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.view.calendar.month.CalendarMonthCompose
import com.cyxbs.components.view.calendar.scroll.CalendarNestedScroll
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.Festival
import com.cyxbs.components.config.time.SolarTerms
import com.cyxbs.components.config.time.toChineseCalendar
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.view.calendar.state.CalendarState
import com.cyxbs.components.view.calendar.state.rememberCalendarState
import com.cyxbs.components.utils.compose.px2dpCompose
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 日历视图，支持展开和折叠
 *
 * @author 985892345
 * @date 2024/1/23 10:16
 */
@Composable
fun CalendarCompose(
  modifier: Modifier = Modifier,
  state: CalendarState = rememberCalendarState(),
  calendar: @Composable ColumnScope.() -> Unit = {
    Row {
      state.MonthTextCompose(modifier = Modifier.width(36.dp))
      Column(modifier = Modifier.weight(1F)) {
        state.WeekTextCompose()
        state.CalendarMonthCompose { date, show ->
          state.CalendarDateCompose(date, show)
        }
      }
    }
  },
  content: @Composable ColumnScope.() -> Unit = {},
) {
  Column(
    modifier = Modifier.fillMaxSize()
      .nestedScroll(remember(state) { CalendarNestedScroll(state) })
      .then(modifier)
  ) {
    calendar()
    content()
  }
}

@Composable
fun CalendarState.MonthTextCompose(
  modifier: Modifier = Modifier
) {
  val pagerState = rememberPagerState(
    initialPage = Snapshot.withoutReadObservation {
      (clickDate.year - startDateState.value.year) * 12 +
          clickDate.monthNumber - startDateState.value.monthNumber
    }
  ) {
    (endDateState.value.year - startDateState.value.year) * 12 +
        endDateState.value.monthNumber - startDateState.value.monthNumber + 1
  }
  VerticalPager(
    modifier = modifier.padding(top = 12.dp)
      .height(lineHeightState.value.px2dpCompose.coerceAtLeast(1.dp)),
    state = pagerState,
    key = { startDateState.value.plusMonths(it).copy(dayOfMonth = 1).value },
  ) {
    val date = startDateState.value.plusMonths(it)
    Box(modifier = Modifier.fillMaxSize().width(IntrinsicSize.Min)) {
      Text(
        modifier = Modifier.align(Alignment.Center),
        text = "${date.year % 100}年\n${date.monthNumber}月",
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        lineHeight = 20.sp
      )
    }
  }
  LaunchedEffect(this) {
    launch {
      snapshotFlow { clickDate }.map {
        (it.year - startDateState.value.year) * 12 + it.monthNumber - startDateState.value.monthNumber
      }.collect {
        if (it != pagerState.currentPage) {
          launch {
            pagerState.animateScrollToPage(it, animationSpec = tween(easing = LinearEasing))
          }
        }
      }
    }
    launch {
      snapshotFlow { pagerState.settledPage }.map {
        val nowPage = (clickDate.year - startDateState.value.year) * 12 +
            clickDate.monthNumber - startDateState.value.monthNumber
        clickDateState.value.plusMonths(it - nowPage)
          .coerceIn(startDateState.value, endDateState.value)
      }.collect {
        if (it != clickDate && !verticalIsScrolling && !horizontalIsScrolling) {
          updateClickDate(it)
        }
      }
    }
  }
}

@Composable
fun CalendarState.WeekTextCompose(
  modifier: Modifier = Modifier,
) {
  Row(modifier = modifier.layout { measurable, constraints ->
    // 日历宽度严格以 7 的倍数进行计算，这里同步处理
    val width = constraints.maxWidth / 7 * 7
    val placeable = measurable.measure(constraints.copy(maxWidth = width))
    layout(width, placeable.height) {
      placeable.placeRelative(0, 0)
    }
  }) {
    arrayOf("一", "二", "三", "四", "五", "六", "日").forEach {
      Text(
        modifier = Modifier.weight(1F),
        text = it,
        textAlign = TextAlign.Center,
        fontSize = 10.sp
      )
    }
  }
}

@Stable
sealed interface CalendarDateShowValue {
  data object Normal : CalendarDateShowValue
  data object Clicked : CalendarDateShowValue
  data object Outside : CalendarDateShowValue // 超出范围或者不在当前月的日期
}

@Composable
fun CalendarState.CalendarDateCompose(
  date: Date,
  show: CalendarDateShowValue,
) {
  val today = today.invoke()
  Layout(
    modifier = Modifier.graphicsLayer {
      alpha = if (date !in startDateState.value..endDateState.value) 0.3F else {
        if (show == CalendarDateShowValue.Outside)
          1F - verticalScrollState.value.fraction * 0.7F
        else 1F
      }
    }.clickableNoIndicator {
      clickEventFlowInternal.tryEmit(CalendarState.ClickEventData(clickDate, date))
    }.background(
      color = when {
        date == today && show == CalendarDateShowValue.Clicked -> Color(0xFF1C71FF)
        show == CalendarDateShowValue.Clicked -> Color.LightGray
        date == today -> Color.White
        else -> Color.Transparent
      },
      shape = CircleShape
    ),
    content = {
      CalendarDateDayCompose(date, today, show)
      CalendarDateLunarCompose(date, today, show)
      CalendarDateRestCompose(date, today, show)
    },
    measurePolicy = remember {
      { measurables, constraints ->
        val width = constraints.maxWidth
        val height = minOf(constraints.maxWidth, constraints.maxHeight, 56.dp.roundToPx())
        val newConstraints = Constraints(maxWidth = width, maxHeight = height)
        val dayPlaceable = measurables[0].measure(newConstraints)
        val lunarPlaceable = measurables[1].measure(newConstraints)
        val restPlaceable = measurables[2].measure(newConstraints)
        layout(width, height) {
          val dayTop = (height - dayPlaceable.height - lunarPlaceable.height) / 2
          dayPlaceable.placeRelative(
            x = (width - dayPlaceable.measuredWidth) / 2,
            y = dayTop,
          )
          lunarPlaceable.placeRelative(
            x = (width - lunarPlaceable.width) / 2,
            y = dayTop + dayPlaceable.height,
          )
          restPlaceable.placeRelative(
            x = (width + dayPlaceable.measuredWidth) / 2 - 2.dp.roundToPx(),
            y = dayTop - 2.dp.roundToPx(),
          )
        }
      }
    }
  )
}

@Composable
private fun CalendarDateDayCompose(
  date: Date,
  today: Date,
  show: CalendarDateShowValue,
) {
  Text(
    modifier = Modifier,
    text = date.dayOfMonth.toString(),
    color = when {
      date == today && show == CalendarDateShowValue.Clicked -> Color.White
      date == today -> Color(0xFF1C71FF)
      else -> Color.Black
    },
    fontSize = 19.sp,
    fontWeight = FontWeight.Bold,
  )
}

@Composable
private fun CalendarDateLunarCompose(
  date: Date,
  today: Date,
  show: CalendarDateShowValue,
) {
  val specialDay = remember(date) { Festival.get(date) ?: SolarTerms.get(date)?.chinese }
  Text(
    modifier = Modifier,
    text = specialDay ?: date.toChineseCalendar().run {
      if (dayOfMonth == 1) getMonthStr() else getDayStr()
    },
    color = when {
      date == today && show == CalendarDateShowValue.Clicked -> Color.White
      specialDay != null -> Color(0xFF1C71FF)
      else -> Color.Gray
    },
    fontSize = 9.sp,
  )
}

@Composable
private fun CalendarDateRestCompose(
  date: Date,
  today: Date,
  show: CalendarDateShowValue,
) {
  Text(
    modifier = Modifier,
    text = "",
    color = when {
      date == today && show == CalendarDateShowValue.Clicked -> Color.White
      else -> Color(0xFF00C853)
    },
    fontSize = 8.sp,
  )
}