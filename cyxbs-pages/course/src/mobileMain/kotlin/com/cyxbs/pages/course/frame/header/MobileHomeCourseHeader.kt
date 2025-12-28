package com.cyxbs.pages.course.frame.header

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.config.time.Today
import com.cyxbs.components.config.time.toMinuteTimeDate
import com.cyxbs.components.view.ui.BottomSheetValueState
import com.cyxbs.pages.course.frame.MobileHomeCourseFrame
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.CourseItemViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.max
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 主页课表头，分为折叠时的外课表头与展开时的内课表头
 *
 * @author 985892345
 * @date 2025/3/18
 */
@Composable
fun MobileHomeCourseHeader(
  modifier: Modifier,
  frame: MobileHomeCourseFrame,
) {
  Box(modifier = modifier) {
    val headerVisibility by remember(frame) {
      frame.bottomSheetState.stateFlow.filter {
        it != BottomSheetValueState.Hide
      }.map {
        when (it) {
          BottomSheetValueState.Hide -> error("")
          BottomSheetValueState.Expanded -> true
          BottomSheetValueState.Scrolling -> null
          BottomSheetValueState.Collapsed -> false
        }
      }
    }.collectAsState(false)
    if (headerVisibility != true) { // 折叠和滚动时显示
      // 主页课表外层 header
      MobileHomeCourseOuterHeader(
        frame = frame,
        modifier = Modifier.graphicsLayer {
          alpha = max(1 - frame.bottomSheetState.fraction * 2, 0F)
        },
      )
    }
    if (headerVisibility != false) { // 展开和滚动时显示
      // 主页课表内层 header
      CourseHeader(
        frame = frame,
        modifier = Modifier.graphicsLayer {
          alpha = max(frame.bottomSheetState.fraction * 2 - 1, 0F)
        }
      )
    }
  }
  LaunchedEffect(frame) {
    if (frame.beginDate.value == null) {
      val selectPageJon = launch {
        // beginDate 未初始化，则进行等待
        frame.beginDate.filterNotNull().first()
        frame.pagerState.scrollToPage(frame.initialPage) // beginDate 初始化后跳到 initialPage
      }
      launch {
        frame.bottomSheetState.stateFlow.first { it == BottomSheetValueState.Expanded }
        selectPageJon.cancel() // 如果触发一次展开，则取消回到 initialPage
      }
    }
  }
}

private val EmptyHeader = HintCourseBottomSheetHeader("今天和明天都没课咯～")

@Composable
private fun MobileHomeCourseOuterHeader(
  modifier: Modifier,
  frame: MobileHomeCourseFrame,
) {
  val headerState = remember(frame) { mutableStateOf<CourseBottomSheetHeaderExtension>(EmptyHeader) }
  key(headerState.value) {
    headerState.value.CourseBottomSheetHeaderContent(modifier)
  }
  val courseItemViewModel = viewModel(CourseItemViewModel::class)
  LaunchedEffect(frame) {
    SchoolCalendar.observeFirstMonDay().flatMapLatest { firstDate ->
      snapshotFlow { Today }.map { firstDate.daysUntil(it) }
    }.flatMapLatest { dayDiff ->
      if (dayDiff < 0) return@flatMapLatest flowOf(null)
      // 今天的所有课表 item
      val todayList = combine(
        courseItemViewModel.itemHierarchy.map {
          it.observe(dayDiff / 7 + 1, DayOfWeek((dayDiff % 7) + 1))
        }
      ) {
        it.toList().flatten()
      }
      // 明天的所有课表 item
      val tomorrowList = combine(
        courseItemViewModel.itemHierarchy.map {
          it.observe((dayDiff + 1) / 7 + 1, DayOfWeek(((dayDiff + 1) % 7) + 1))
        }
      ) {
        it.toList().flatten()
      }

      combine(todayList, tomorrowList) { today, tomorrow ->
        today to tomorrow
      }.flatMapLatest { (today, tomorrow) ->
        // 每隔一分钟轮训一次
        flow {
          do {
            val localDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val now = localDateTime.toMinuteTimeDate().time
            emit(now)
            delay(1.minutes - localDateTime.second.seconds)
          } while (isActive)
        }.map { now ->
          findCourseBottomSheetHeaderExtension(now, today)
            ?: findCourseBottomSheetHeaderExtension(MinuteTime(0, 0), tomorrow)
        }
      }
    }.collect {
      headerState.value = it ?: EmptyHeader
    }
  }
}

private fun findCourseBottomSheetHeaderExtension(
  nowTime: MinuteTime,
  itemStateList: List<CourseItemState>,
): CourseBottomSheetHeaderExtension? {
  var minItem: CourseItem? = null
  for (itemState in itemStateList) {
    val item = itemState.item
    val extension = item.extension
    if (extension !is CourseBottomSheetHeaderExtension) continue
    if (nowTime in item.whatTime.now.value.beginTime..item.whatTime.now.value.finalTime) {
      return extension
    }
    if (item.whatTime.now.value.beginTime < nowTime) continue // 略过小于的时间段
    if (minItem == null || item.whatTime.now.value.beginTime < minItem.whatTime.now.value.beginTime) {
      minItem = item
      continue
    }
  }
  return minItem?.extension as? CourseBottomSheetHeaderExtension
}