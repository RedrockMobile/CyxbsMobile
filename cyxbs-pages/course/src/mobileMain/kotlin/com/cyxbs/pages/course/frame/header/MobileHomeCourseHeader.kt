package com.cyxbs.pages.course.frame.header

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.cyxbs.components.config.time.toMinuteTimeDate
import com.cyxbs.components.view.ui.BottomSheetValueState
import com.cyxbs.pages.course.frame.MobileHomeCourseFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
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
  var header by remember(frame) { mutableStateOf<CourseBottomSheetHeaderExtension>(EmptyHeader) }
  key(header) {
    header.CourseBottomSheetHeaderContent(modifier)
  }
  LaunchedEffect(frame) {
    val localDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    var now = localDateTime.toMinuteTimeDate()
    snapshotFlow { NextItemHeaderUtils.find(now, frame) }
      .flatMapLatest {
        if (it == null) flowOf<CourseBottomSheetHeaderExtension?>(null) else flow {
          emit(it)
          delay(1.minutes - localDateTime.second.seconds)
          while (true) {
            now = now.plusMinutes(1)
            val next = NextItemHeaderUtils.find(now, frame)
            if (next != null) emit(next) else break // 如果为 null 则跳出循环
            delay(1.minutes)
          }
        }
      }.onEach {
        header = it ?: EmptyHeader
      }.launchIn(this)
  }
}

