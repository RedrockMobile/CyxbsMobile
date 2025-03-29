package com.cyxbs.pages.course.home.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.config.time.toMinuteTimeDate
import com.cyxbs.components.utils.compose.BottomSheetValueState
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.rememberDerivedStateOfStructure
import com.cyxbs.pages.course.home.data.HomeLinkLessonDataProvider
import com.cyxbs.pages.course.home.header.NextItemHeaderUtils
import com.cyxbs.pages.course.home.header.BottomSheetItemHeader
import com.cyxbs.pages.course.home.header.HintBottomSheetItemHeader
import cyxbsmobile.cyxbs_pages.course.generated.resources.Res
import cyxbsmobile.cyxbs_pages.course.generated.resources.course_ic_item_header_link_double
import cyxbsmobile.cyxbs_pages.course.generated.resources.course_ic_item_header_link_single
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.max
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
      MobileHomeCourseInnerHeader(
        frame = frame,
        modifier = Modifier.graphicsLayer {
          alpha = max(frame.bottomSheetState.fraction * 2 - 1, 0F)
        }
      )
    }
  }
  LaunchedEffect(frame) {
    if (frame.beginDate == null) {
      // beginDate 未初始化，则进行等待
      val beginDateJob = launch {
        frame.beginDate = SchoolCalendar.observeFirstMonDay().first()
      }
      val selectPageJon = launch {
        beginDateJob.join() // beginDate 初始化后跳到 initialPage
        frame.pagerState.scrollToPage(frame.initialPage)
      }
      launch {
        frame.bottomSheetState.stateFlow.first { it == BottomSheetValueState.Expanded }
        selectPageJon.cancel() // 如果触发一次展开，则取消回到 initialPage
      }
    }
  }
}

private val EmptyHeader = HintBottomSheetItemHeader("今天和明天都没课咯～")

@Composable
private fun MobileHomeCourseOuterHeader(
  modifier: Modifier,
  frame: MobileHomeCourseFrame,
) {
  var header by remember(frame) { mutableStateOf<BottomSheetItemHeader>(EmptyHeader) }
  key(header) {
    header.BottomSheetHeaderContent(modifier)
  }
  LaunchedEffect(frame) {
    val localDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    var now = localDateTime.toMinuteTimeDate()
    snapshotFlow { NextItemHeaderUtils.find(now, frame) }
      .flatMapLatest {
        if (it == null) flowOf<BottomSheetItemHeader?>(null) else flow {
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


@Composable
private fun MobileHomeCourseInnerHeader(
  modifier: Modifier,
  frame: MobileHomeCourseFrame,
) {
  Box(modifier = modifier.fillMaxSize()) {
    Row(modifier = Modifier.align(Alignment.BottomStart)) {
      frame.CourseHeaderTitle(
        modifier = Modifier.align(Alignment.Bottom)
          .padding(start = 16.dp, bottom = 4.dp)
      )
      frame.CourseHeaderSubtitle(
        modifier = Modifier.align(Alignment.Bottom)
          .padding(start = 13.dp, bottom = 6.dp)
      )
    }
    CourseLinkStudentBtn(
      frame = frame,
      modifier = Modifier.align(Alignment.BottomEnd)
        .padding(end = 110.dp, bottom = 2.dp),
    )
    frame.CourseHeaderBack(
      modifier = Modifier.align(Alignment.BottomEnd)
        .padding(end = 16.dp, bottom = 2.dp)
    )
  }
}

@Composable
private fun CourseLinkStudentBtn(
  modifier: Modifier,
  frame: MobileHomeCourseFrame,
) {
  // 0 -> 1 -> 0
  val pageFraction by rememberDerivedStateOfStructure(frame) {
    val fraction = frame.pagerState.currentPageOffsetFraction
    1 - minOf(abs(fraction + frame.pagerState.currentPage - frame.initialPage), 1F)
  }
  val enableShowState = HomeLinkLessonDataProvider.enableShow.collectAsState()
  val enableShow = enableShowState.value
  if (enableShow != null) {
    Image(
      contentDescription = if (enableShow) "点击不显示关联人课程" else "点击显示关联人课程",
      contentScale = ContentScale.Inside,
      painter = painterResource(
        if (enableShow) Res.drawable.course_ic_item_header_link_double
        else Res.drawable.course_ic_item_header_link_single,
      ),
      modifier = modifier.size(32.dp).graphicsLayer {
        translationX = (101.dp * pageFraction).toPx()
      }.clickableNoIndicator {
        HomeLinkLessonDataProvider.changeVisible()
      },
    )
  }
}