package com.cyxbs.pages.course.view.timeline

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMapIndexed
import com.cyxbs.components.utils.compose.reflexScrollableForMouse
import com.cyxbs.pages.course.view.timeline.data.MutableTimelineData
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/**
 * 课表滚动组件
 *
 * @author 985892345
 * @date 2024/1/27 16:25
 */
@Composable
internal fun CourseScrollCompose(
  timeline: CourseTimeline,
  modifier: Modifier = Modifier,
  verticalScrollState: ScrollState = rememberScrollState(),
  scrollPaddingValues: PaddingValues,
  content: @Composable () -> Unit,
) {
  Layout(
    modifier = modifier
      .reflexScrollableForMouse()
      .verticalScroll(state = verticalScrollState),
    content = { content() },
    measurePolicy = remember(timeline) {
      { measurables, constraints ->
        var widthConsume = 0
        var initialWeight = 0F
        var nowWeight = 0F
        timeline.data.fastForEach {
          initialWeight += it.initialWeight
          nowWeight += it.nowWeight
        }
        val startPadding = scrollPaddingValues.calculateStartPadding(layoutDirection).roundToPx()
        val endPadding = scrollPaddingValues.calculateEndPadding(layoutDirection).roundToPx()
        val topPadding = scrollPaddingValues.calculateTopPadding().roundToPx()
        val bottomPadding = scrollPaddingValues.calculateBottomPadding().roundToPx()
        // 因为有 verticalScroll，所以这里 minHeight 就是父布局的高度
        val height = ((constraints.minHeight - topPadding - bottomPadding) * (nowWeight / initialWeight))
          .roundToInt()
        val maxWidth = constraints.maxWidth - startPadding - endPadding
        val placeables = measurables.fastMapIndexed { index, measurable ->
          measurable.measure(
            Constraints(
              minWidth = if (index != measurables.lastIndex || index == 0) 0 else maxWidth - widthConsume,
              maxWidth = maxWidth - widthConsume,
              maxHeight = height,
            )
          ).apply { widthConsume += width }
        }
        layout(constraints.maxWidth, height + topPadding + bottomPadding) {
          var start = startPadding
          placeables.fastForEach {
            it.placeRelative(x = start, y = topPadding)
            start += it.width
          }
        }
      }
    }
  )
  LaunchedEffect(timeline, verticalScrollState) {
    val lastTimeline = timeline.data.last()
    if (lastTimeline is MutableTimelineData) {
      // 最后一个展开时需要向上滚动
      lastTimeline.clickAnimateState.collectLatest {
        if (it && verticalScrollState.value == verticalScrollState.maxValue) {
          verticalScrollState.scroll {
            animate(
              0F, 0F,
              animationSpec = infiniteRepeatable(tween(1000))
            ) { _, _ ->
              scrollBy((verticalScrollState.maxValue - verticalScrollState.value).toFloat())
            }
          }
        }
      }
    }
  }
}