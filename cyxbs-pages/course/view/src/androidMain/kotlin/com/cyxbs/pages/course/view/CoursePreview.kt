package com.cyxbs.pages.course.view

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.tooling.preview.Preview
import com.cyxbs.components.config.compose.theme.AppTheme
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.TodayNoEffect
import com.cyxbs.components.utils.utils.get.Num2CN
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.math.abs

/**
 * .
 *
 * @author 985892345
 * @date 2025/2/14
 */
@Preview(showBackground = true)
@Composable
fun PreviewCourseWeekCompose() {
  AppTheme {
    CourseCompose(
      controller = CoursePreviewController,
    )
  }
}

private object CoursePreviewController : CourseController {

  private val clickBackFlow = MutableSharedFlow<Unit>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )

  override val courseBeginDate: Date = TodayNoEffect.weekBeginDate

  override val initialWeek: Int = courseBeginDate.daysUntil(TodayNoEffect) / 7 + 1

  @Composable
  override fun WeekItemContent(pagerState: PagerState, scrollState: ScrollState, beginDate: Date?) {
    LaunchedEffect(pagerState) {
      snapshotFlow { pagerState.currentPage }.onEach {
        title = if (it == 0) "整学期" else "第${Num2CN.number2ChineseNumber(it.toLong())}周"
        subtitle = if (it == initialWeek) "(本周)" else ""
      }.launchIn(this)
      snapshotFlow { pagerState.currentPageOffsetFraction }.onEach {
        // 1 -> 0 -> 1
        val fraction = minOf(abs(it + pagerState.currentPage - initialWeek), 1F)
        subtitleScale = 1F - fraction
        backBtnOffsetRatio = 1F - fraction
      }.launchIn(this)
      clickBackFlow.onEach {
        pagerState.animateScrollToPage(initialWeek)
      }.launchIn(this)
    }
  }

  override var title: String by mutableStateOf("")

  override var subtitle: String by mutableStateOf("")

  override var subtitleScale: Float by mutableFloatStateOf(1F)

  override var backBtnOffsetRatio: Float by mutableFloatStateOf(0F)

  override fun onClickTitle() {

  }

  override fun onClickSubtitle() {

  }

  override fun onClickBack() {
    clickBackFlow.tryEmit(Unit)
  }
}