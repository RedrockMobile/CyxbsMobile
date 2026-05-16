package com.cyxbs.pages.course.view.timeline.data

import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastSumBy
import com.cyxbs.components.config.serializable.ColorSerializable
import com.cyxbs.components.config.serializable.TextUnitSerializable
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.pages.course.view.timeline.DefaultTimelineLightTextColor
import com.cyxbs.pages.course.view.timeline.DefaultTimelineLightTextDarkColor
import com.cyxbs.pages.course.view.timeline.LocalCourseScroll
import com.cyxbs.pages.course.view.timeline.LocalCourseScrollContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.roundToInt

/**
 * .
 *
 * @author 985892345
 * 2024/3/11 19:20
 */
@Serializable
data class MutableTimelineData(
  val text: String,
  override val optionText: String,
  override val startTime: MinuteTime,
  override val endTime: MinuteTime,
  val maxWeight: Float,
  override val initialWeight: Float,
  @Serializable(TextUnitSerializable::class)
  override val fontSize: TextUnit = 12.sp,
  @Serializable(ColorSerializable::class)
  val textColor: Color = DefaultTimelineLightTextColor,
  @Serializable(ColorSerializable::class)
  val textDarkColor: Color = DefaultTimelineLightTextDarkColor,
  @Serializable(ColorSerializable::class)
  val expandTextColor: Color = DefaultTimelineLightTextColor,
  @Serializable(ColorSerializable::class)
  val expandTextDarkColor: Color = DefaultTimelineLightTextDarkColor,
) : CourseTimelineData {

  @Transient
  private var nowWeightState = mutableFloatStateOf(initialWeight)

  override val nowWeight: Float
    get() = nowWeightState.value

  @Composable
  override fun ColumnScope.Content() {
    val scrollContext = LocalCourseScroll.current
    MutableTimelineCompose(modifier = Modifier.weight(nowWeight).clickableNoIndicator {
      click()
    }.onGloballyPositioned {
      scrollContext.timelineCoordinatesMap[this@MutableTimelineData] = it
    })
    LaunchedEffect(Unit) {
      _state.filter {
        it == State.ExpandToCollapseAnim || it == State.CollapseToExpandAnim
      }.collectLatest {
        val targetWeight = if (it == State.ExpandToCollapseAnim) initialWeight else maxWeight
        try {
          supervisorScope {
            launch {
              animate(nowWeight, targetWeight) { value, _ ->
                nowWeightState.value = value
              }
            }
            scrollExpand(scrollContext, targetWeight)
          }
        } finally {
          _state.value = if (it == State.ExpandToCollapseAnim) {
            State.Collapse
          } else {
            State.Expand
          }
        }
      }
    }
  }

  private fun CoroutineScope.scrollExpand(
    scrollContext: LocalCourseScrollContext,
    targetWeight: Float
  ) {
    val upOrDown = scrollUpOrDown ?: let {
      // 最后一个展开时需要向上滚动
      (scrollContext.timeline.data.last() === this@MutableTimelineData
          && scrollContext.scrollState.value == scrollContext.scrollState.maxValue)
    }
    if (upOrDown) {
      launch {
        val initialBottomRemainValue =
          scrollContext.scrollState.maxValue - scrollContext.scrollState.value
        scrollContext.scrollState.scroll {
          // 这里要使用跟展开一样的动画
          animate(nowWeight, targetWeight) { _, _ ->
            val scrollTo = scrollContext.scrollState.maxValue - initialBottomRemainValue
            scrollBy((scrollTo - scrollContext.scrollState.value).toFloat())
          }
        }
      }
    }
  }

  /**
   * @param scrollUpOrDown true：滚轴向上展开；false：滚轴向下展开；null：根据当前情况进行判断
   */
  fun click(scrollUpOrDown: Boolean? = null): Int {
    if (clickLockCount > 0) return clickLockCount
    if (_state.value == State.Expand) {
      this.scrollUpOrDown = scrollUpOrDown
      _state.value = State.ExpandToCollapseAnim
    } else if (_state.value == State.Collapse) {
      this.scrollUpOrDown = scrollUpOrDown
      _state.value = State.CollapseToExpandAnim
    }
    return 0
  }

  // 给点击上锁
  fun lockClick(): ClickLock {
    clickLockCount++
    return object : ClickLock {
      var hasUnlock = false
      override fun unlock(): Int {
        if (hasUnlock) return clickLockCount
        hasUnlock = true
        clickLockCount--
        return clickLockCount
      }
    }
  }

  @Transient
  private var clickLockCount = 0

  @Transient
  private var scrollUpOrDown: Boolean? = null

  @Transient
  private val _state =
    MutableStateFlow(if (nowWeight == maxWeight) State.Expand else State.Collapse)
  val state: StateFlow<State> = _state

  enum class State {
    Expand, Collapse, ExpandToCollapseAnim, CollapseToExpandAnim
  }

  interface ClickLock {
    // 多次调用只有第一次调用有效
    // 返回需要的剩余解锁次数，返回 0 时说明已经解锁
    fun unlock(): Int
  }
}

@Composable
private fun MutableTimelineData.MutableTimelineCompose(
  modifier: Modifier = Modifier,
) {
  Layout(
    modifier = modifier,
    content = {
      Text(
        text = text,
        textAlign = TextAlign.Center,
        fontSize = fontSize,
        color = textColor.dark(textDarkColor),
        overflow = TextOverflow.Visible
      )
      val time = if (startTime.minute == 0) startTime else MinuteTime(startTime.hour + 1, 0)
      val count =
        if (startTime < endTime) endTime.hour - time.hour + 1 else 24 - time.hour + endTime.hour + 1
      repeat(count) {
        Text(
          text = time.plusHours(it).toString(),
          textAlign = TextAlign.Center,
          fontSize = 9.sp,
          color = expandTextColor.dark(expandTextDarkColor),
          overflow = TextOverflow.Visible,
          maxLines = 1,
        )
      }
      if (endTime.minuteOfDay == 23 * 60 + 59) {
        // 单独显示 24:00
        Text(
          text = "24:00",
          textAlign = TextAlign.Center,
          fontSize = 9.sp,
          color = expandTextColor.dark(expandTextDarkColor),
          overflow = TextOverflow.Visible,
          maxLines = 1,
        )
      }
    },
    measurePolicy = remember {
      { measurables, constraints ->
        val placeables = measurables.fastMap {
          it.measure(
            constraints.copy(
              minWidth = 0,
              minHeight = 0,
              maxHeight = Constraints.Infinity
            )
          )
        }
        val layoutWidth = constraints.maxWidth
        val layoutHeight = constraints.maxHeight
        layout(layoutWidth, layoutHeight) {
          val minTimeHeight = placeables.fastSumBy { it.height } - placeables[0].height
          if (layoutHeight < minTimeHeight) { // 说明展示时间的高度不够
            placeables[0].let {
              it.placeRelative(
                x = (layoutWidth - it.width) / 2,
                y = (layoutHeight - it.height) / 2
              )
            }
          } else {
            val minuteHeight = layoutHeight.toFloat() / startTime.minutesUntil(endTime, true)
            placeables.fastForEachIndexed { i, placeable ->
              if (i == 0) return@fastForEachIndexed
              placeable.placeRelative(
                x = (layoutWidth - placeable.width) / 2,
                y = (((60 - startTime.minute) % 60 + (i - 1) * 60) * minuteHeight - placeable.height / 2F).roundToInt()
              )
            }
          }
        }
      }
    }
  )
}