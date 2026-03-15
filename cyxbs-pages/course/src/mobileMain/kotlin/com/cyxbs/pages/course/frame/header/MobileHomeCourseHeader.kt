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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.Today
import com.cyxbs.components.view.ui.BottomSheetValueState
import com.cyxbs.pages.course.frame.MobileHomeCourseFrame
import com.cyxbs.pages.course.view.frame.header.CourseFrameHeader
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.viewmodel.CourseItemViewModel
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.max

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
    // 主页课表外层 header
    MobileHomeCourseOuterHeader(
      frame = frame,
      modifier = Modifier.graphicsLayer {
        alpha = max(1 - frame.bottomSheetState.fraction * 2, 0F)
      },
    )
    if (headerVisibility != false) { // 展开和滚动时才显示，折叠时需要移除掉，把触摸事件透给 MobileHomeCourseOuterHeader
      // 主页课表内层 header
      CourseFrameHeader(
        frame = frame,
        linkBtnVisibility = true,
        modifier = Modifier.pointerInput(Unit) {/*拦截 MobileHomeCourseOuterHeader 点击事件*/}
          .graphicsLayer {
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

private val EmptyHeader = HintCourseBottomSheetHeader("加载中...")
private val NoLessonHeader = HintCourseBottomSheetHeader("今天和明天都没课咯～")
private val HolidayHeader = HintCourseBottomSheetHeader("享受假期吧～")

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
    frame.beginDate.filterNotNull().collectLatest { beginDate ->
      snapshotFlow { Today }.collectLatest { today ->
        if (today < beginDate) {
          headerState.value = HolidayHeader
        } else {
          val page = frame.getPage(today)
          if (page == null) {
            headerState.value = HolidayHeader
          } else {
            delay(500) // 防止上游数据因为首次加载的抖动
            courseItemViewModel.nextItemFlow.collectLatest {
              headerState.value = if (it == null) NoLessonHeader else {
                // MobileCourseNextSearch 保证返回的一定是 CourseBottomSheetHeaderExtension 类型
                it.item.extensions.get(CourseBottomSheetHeaderExtension::class)!!
              }
            }
          }
        }
      }
    }
  }
}

@ImplProvider
object MobileCourseNextSearch : CourseItemViewModel.NextItemSearcher {
  override fun search(sortedList: List<CourseItemState>, now: MinuteTime): CourseItemState? {
    return sortedList.firstOrNull {
      it.item.whatTime.finalTime > now && it.item.extensions.get(CourseBottomSheetHeaderExtension::class) != null
    }
  }
}