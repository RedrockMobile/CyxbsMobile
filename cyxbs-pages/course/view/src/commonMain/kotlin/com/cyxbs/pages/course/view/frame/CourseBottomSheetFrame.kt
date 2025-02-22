package com.cyxbs.pages.course.view.frame

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cyxbs.components.utils.compose.BottomSheetCompose
import com.cyxbs.components.utils.compose.BottomSheetState
import com.cyxbs.components.utils.utils.get.Num2CN
import com.cyxbs.pages.course.view.header.CourseBottomSheetHeaderBackground
import com.cyxbs.pages.course.view.header.CourseHeader
import com.cyxbs.pages.course.view.header.CourseHeaderController
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.math.abs

/**
 * 手机端 BottomSheet 样式课表，带有整学期页面
 *
 * @author 985892345
 * @date 2025/2/16
 */
@Stable
abstract class CourseBottomSheetFrame : CourseSemesterFrame(), CourseHeaderController {

  override var title: String by mutableStateOf("")

  override var subtitle: String by mutableStateOf("")

  override var subtitleScale: Float by mutableFloatStateOf(1F)

  override var backBtnOffsetRatio: Float by mutableFloatStateOf(0F)

  // 点击“回到本周”按钮
  protected val clickBackFlow = MutableSharedFlow<Unit>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )

  // BottomSheetCompose State
  open val bottomSheetState by lazy {
    BottomSheetState()
  }

  // BottomSheetCompose peekHeight
  open val peekHeight: Dp
    get() = 70.dp

  @Composable
  override fun CourseCompose() {
    BottomSheetCompose(
      bottomSheetState = bottomSheetState,
      scrimColor = Color.Transparent,
      peekHeight = peekHeight,
    ) {
      Column {
        CourseBottomSheetHeaderBackground(
          modifier = Modifier.bottomSheetDraggable()
        ) {
          CourseBottomSheetHeader()
        }
        CourseHorizontalPager {
          CoursePageContent(it)
        }
      }
    }
  }

  @Composable
  open fun CourseBottomSheetHeader() {
    CourseHeader(controller = this)
    LaunchedEffect(Unit) {
      clickBackFlow.onEach {
        pagerState.animateScrollToPage(initialPage)
      }.launchIn(this)
    }
  }

  @Composable
  override fun CourseHorizontalPager(pageContent: @Composable PagerScope.(page: Int) -> Unit) {
    super.CourseHorizontalPager(pageContent)
    OnCourseHorizontalPager()
  }

  @Composable
  open fun OnCourseHorizontalPager() {
    LaunchedEffect(pagerState) {
      snapshotFlow { pagerState.currentPage }.onEach {
        observeCurrentPage(it)
      }.launchIn(this)
      snapshotFlow { pagerState.currentPageOffsetFraction }.onEach {
        observeCurrentPageOffsetFraction(it)
      }.launchIn(this)
    }
  }

  // 观察 HorizontalPager 翻页
  open fun observeCurrentPage(page: Int) {
    title = if (page == 0) "整学期" else "第${Num2CN.number2ChineseNumber(page)}周"
    subtitle = if (page == initialPage) "(本周)" else ""
  }

  // 观察 HorizontalPager 页面偏移
  open fun observeCurrentPageOffsetFraction(fraction: Float) {
    // 1 -> 0 -> 1
    val pageFraction = minOf(abs(fraction + pagerState.currentPage - initialPage), 1F)
    subtitleScale = 1F - pageFraction
    backBtnOffsetRatio = 1F - pageFraction
  }

  override fun onClickBack() {
    clickBackFlow.tryEmit(Unit)
  }
}