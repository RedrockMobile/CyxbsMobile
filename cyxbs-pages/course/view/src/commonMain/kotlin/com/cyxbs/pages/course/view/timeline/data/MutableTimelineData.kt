package com.cyxbs.pages.course.view.timeline.data

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
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
 * 时间轴折叠状态变化使用的统一动画。
 *
 * 中低刚度让课表 Item 的位移能与 BottomSheet 弹起同时被观察到；权重本身通常小于 10，
 * 因此使用较小的可见阈值，避免被过早判定为到达目标。
 */
private val TimelineExpandAnimationSpec = spring(
  stiffness = Spring.StiffnessMediumLow,
  visibilityThreshold = 0.001F,
)

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
              animate(
                initialValue = nowWeight,
                targetValue = targetWeight,
                animationSpec = TimelineExpandAnimationSpec,
              ) { value, _ ->
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

  /**
   * 在权重动画期间按 [scrollMode] 同步课表滚轴。
   *
   * BottomSheet 等外部容器需要独立控制避让时应选择 [ScrollMode.DoNotScroll]，避免两个滚动控制器
   * 同时修改同一个 ScrollState；直接点击时间轴时默认使用 [ScrollMode.Auto]。
   */
  private fun CoroutineScope.scrollExpand(
    scrollContext: LocalCourseScrollContext,
    targetWeight: Float
  ) {
    val shouldScroll = when (scrollMode) {
      ScrollMode.Auto -> {
        // 最后一个时间段在滚轴底部展开时，保持用户距离底部的位置不变。
        scrollContext.timeline.data.last() === this@MutableTimelineData &&
            scrollContext.scrollState.value == scrollContext.scrollState.maxValue
      }

      ScrollMode.KeepBottomDistance -> true
      ScrollMode.DoNotScroll -> false
    }
    if (!shouldScroll) return
    launch {
      val initialBottomRemainValue =
        scrollContext.scrollState.maxValue - scrollContext.scrollState.value
      scrollContext.scrollState.scroll {
        animate(
          initialValue = nowWeight,
          targetValue = targetWeight,
          animationSpec = TimelineExpandAnimationSpec,
        ) { _, _ ->
          val scrollTo = scrollContext.scrollState.maxValue - initialBottomRemainValue
          scrollBy((scrollTo - scrollContext.scrollState.value).toFloat())
        }
      }
    }
  }

  /**
   * 切换当前时间轴区间的展开状态。
   *
   * @param scrollMode 权重变化期间的滚轴跟随方式。调用方已有自己的滚动避让逻辑时必须使用
   * [ScrollMode.DoNotScroll]，避免两个控制器竞争。
   * @return 剩余的点击锁数量；大于 0 表示本次切换未执行。
   */
  fun click(scrollMode: ScrollMode = ScrollMode.Auto): Int {
    if (clickLockCount > 0) return clickLockCount
    if (_state.value == State.Expand) {
      this.scrollMode = scrollMode
      _state.value = State.ExpandToCollapseAnim
    } else if (_state.value == State.Collapse) {
      this.scrollMode = scrollMode
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
  private var scrollMode: ScrollMode = ScrollMode.Auto

  /**
   * 时间轴权重变化时的滚轴处理方式。
   *
   * [Auto] 仅在末段且用户已位于底部时保持底部距离；
   * [KeepBottomDistance] 强制保持当前底部距离；
   * [DoNotScroll] 只改变时间轴高度，由 BottomSheet 等外部容器负责滚动避让。
   */
  enum class ScrollMode {
    Auto,
    KeepBottomDistance,
    DoNotScroll,
  }

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
