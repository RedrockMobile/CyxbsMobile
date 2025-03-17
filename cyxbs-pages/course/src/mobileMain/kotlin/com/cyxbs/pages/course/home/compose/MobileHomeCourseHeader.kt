package com.cyxbs.pages.course.home.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.components.config.time.next
import com.cyxbs.components.config.time.toMinuteTimeDate
import com.cyxbs.pages.course.view.item.BottomSheetItemHeader
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.item.HintBottomSheetItemHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 主页的外层课表头
 *
 * @author 985892345
 * @date 2025/3/18
 */
@Composable
fun MobileHomeCourseHeader(
  modifier: Modifier,
  frame: MobileHomeCourseFrame,
) {
  var header by remember { mutableStateOf<BottomSheetItemHeader?>(null) }
  val emptyItemHeader = remember { HintBottomSheetItemHeader("今天和明天都没课咯～") }
  (header ?: emptyItemHeader).HeaderContent(modifier)
  LaunchedEffect(frame) {
    val localDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    var now = localDateTime.toMinuteTimeDate()
    snapshotFlow { findNextItem(now, frame) }
      .flatMapLatest {
        if (it == null) flowOf<BottomSheetItemHeader?>(null) else flow {
          emit(it)
          delay(1.minutes - localDateTime.second.seconds)
          while (true) {
            now = now.plusMinutes(1)
            val next = findNextItem(now, frame)
            if (next != null) emit(next) else break // 如果为 null 则跳出循环
            delay(1.minutes)
          }
        }
      }.onEach {
        header = it
      }.launchIn(this)
  }
}

private fun findNextItem(
  nowTime: MinuteTimeDate,
  frame: MobileHomeCourseFrame,
): BottomSheetItemHeader? {
  return findTodayItem(nowTime, frame) ?: findTomorrowItem(nowTime, frame)
}

// 寻找今天正在进行的或者下一节的 BottomSheetItemHeader
private fun findTodayItem(
  nowTime: MinuteTimeDate,
  frame: MobileHomeCourseFrame,
): BottomSheetItemHeader? {
  val dayOfWeek = nowTime.date.dayOfWeek
  var minItem: CourseItem? = null
  for (weekPool in frame.providerGroup.getWeekDataPool(frame.initialPage)) {
    for (itemContent in weekPool.get(dayOfWeek).state.value) {
      val item = itemContent.item
      if (item is BottomSheetItemHeader) {
        if (nowTime.time in item.beginTime..item.finalTime) {
          return item
        }
        if (item.beginTime < nowTime.time) continue // 略过小于的时间段
        if (minItem == null || item.beginTime < minItem.beginTime) {
          minItem = item
          continue
        }
      }
    }
  }
  return minItem as BottomSheetItemHeader?
}

// 寻找明天第一节 BottomSheetItemHeader
private fun findTomorrowItem(
  nowTime: MinuteTimeDate,
  frame: MobileHomeCourseFrame,
): BottomSheetItemHeader? {
  var minItem: CourseItem? = null
  val dayOfWeek = nowTime.date.dayOfWeek.next()
  val page = frame.initialPage + if (dayOfWeek == frame.timeline.beginDayOfWeek) 1 else 0
  for (weekPool in frame.providerGroup.getWeekDataPool(page)) {
    for (itemContent in weekPool.get(dayOfWeek).state.value) {
      val item = itemContent.item
      if (item is BottomSheetItemHeader) {
        if (minItem == null || item.beginTime < minItem.beginTime) {
          minItem = item
        }
      }
    }
  }
  return minItem as BottomSheetItemHeader?
}