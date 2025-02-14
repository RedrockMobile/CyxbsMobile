package com.cyxbs.pages.course.view.timeline.data

import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastSumBy
import com.cyxbs.components.config.serializable.ColorSerializable
import com.cyxbs.components.config.serializable.FloatStateSerializable
import com.cyxbs.components.config.serializable.TextUnitSerializable
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.utils.compose.clickableNoIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
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
  override val color: Color = Color.Unspecified,
  override val hasTomorrow: Boolean,
) : CourseTimelineData {

  @Serializable(FloatStateSerializable::class)
  private var nowWeightState = mutableFloatStateOf(initialWeight)

  override val nowWeight: Float
    get() = nowWeightState.value

  override fun copyData(): CourseTimelineData {
    return copy()
  }

  @Composable
  override fun ColumnScope.Content() {
    // 分离出 weight 配置以避免对子组件的重组
    Box(modifier = Modifier.weight(nowWeight)) {
      LayoutContent()
    }
  }

  @Composable
  private fun LayoutContent() {
    val coroutineScope = rememberCoroutineScope()
    MutableTimelineCompose(modifier = Modifier.clickableNoIndicator {
      onClick(coroutineScope)
    })
  }

  private fun onClick(coroutineScope: CoroutineScope) {
    if (nowWeight == maxWeight || nowWeight == initialWeight) {
      coroutineScope.launch {
        try {
          _clickAnimateState.value = true
          animate(
            nowWeight,
            if (nowWeight != maxWeight) maxWeight else initialWeight
          ) { value, _ ->
            nowWeightState.value = value
          }
        } finally {
          _clickAnimateState.value = false
        }
      }
    }
  }

  private val _clickAnimateState = MutableStateFlow(false)
  val clickAnimateState: StateFlow<Boolean> = _clickAnimateState
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
        color = color,
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
          color = color,
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
          val minHeight = placeables.fastSumBy { it.height } - placeables[0].height
          val startHour = if (startTime.minute == 0) startTime.hour else startTime.hour + 1
          val hourDiff =
            if (startTime < endTime) endTime.hour - startHour else 24 - startHour + endTime.hour
          val minuteHeight = layoutHeight.toFloat() / startTime.minutesUntil(endTime, true)
          val hourDiffHeight = minuteHeight * hourDiff * 60
          if (hourDiffHeight < minHeight) {
            placeables[0].let {
              it.placeRelative(
                x = (layoutWidth - it.width) / 2,
                y = (layoutHeight - it.height) / 2
              )
            }
          } else {
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