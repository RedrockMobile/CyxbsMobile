package com.cyxbs.pages.course.view.item.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.utils.compose.derivedStateOfStructure
import com.cyxbs.components.utils.compose.rememberDerivedStateOfStructure
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.modifier.BeginFinalTimeShowModifier.visibilityLock
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlin.math.abs

/**
 * item 显示开始结束时间
 *
 * 默认不显示，可使用 [visibilityLock] 开启
 *
 * @author 985892345
 * @date 2026/3/7
 */
object BeginFinalTimeShowModifier : CourseItemModifier {

  // 显示开始结束时间，默认不显示
  val visibilityLock = CourseItemState.ValueKey {
    Lock()
  }

  // 透明度，默认与 itemState#alphaState 同步
  val alphaState = CourseItemState.ValueKey {
    mutableFloatStateOf(1F)
  }

  // 是否根据当 item 的实际展示位置强制计算时间，用于长按整个 item 移动的场景
  val forceCalculateMinuteTime = CourseItemState.ValueKey {
    MutableStateFlow(false)
  }

  @Composable
  override fun createModifier(): Modifier {
    val itemState = itemState
    return if (!visibilityLock.get(itemState).isLocked()) Modifier else {
      LaunchedEffect(Unit) {
        // 同步 item 自身的 alpha
        snapshotFlow { itemState.alphaState.value }.collect {
          alphaState.get(itemState).floatValue = it
        }
      }
      val timePairState = produceState(MinuteTimePair(0)) {
        forceCalculateMinuteTime.get(itemState).flatMapLatest { enable ->
          if (enable) combine(
            itemState.layoutCoordinatesFlow.filterNotNull(),
            itemState.coursePage.layoutCoordinatesFlow,
            itemState.item.whatTime.now,
          ) { itemCoordinates, courseCoordinates, whatTime ->
            val offset = courseCoordinates.localPositionOf(itemCoordinates)
            val beginMinuteTime = itemState.coursePage.timeline.calculateMinuteTime(
              itemState.coursePage,
              offset.y
            )
            MinuteTimePair(
              first = beginMinuteTime,
              second = beginMinuteTime + (whatTime.finalTime - whatTime.beginTime),
            )
          } else itemState.item.whatTime.now.map {
            MinuteTimePair(it.beginTime, it.finalTime)
          }
        }.collect {
          value = it
        }
      }
      Modifier.drawBeginFinalTimeline(
        alpha = alphaState.get(itemState),
        timePair = timePairState,
      )
    }
  }

  class Lock {
    private val count = mutableIntStateOf(0)

    private val isLocked = derivedStateOfStructure { count.intValue > 0 }

    fun lock(): Runnable {
      count.intValue++
      var isUnlock = false
      return Runnable {
        if (isUnlock) return@Runnable
        isUnlock = true
        count.intValue--
      }
    }

    fun isLocked(): Boolean {
      return isLocked.value
    }
  }
}


// 绘制开始结束时间线
@Composable
private fun Modifier.drawBeginFinalTimeline(
  alpha: FloatState,
  timePair: State<MinuteTimePair>,
): Modifier {
  val localAppColor = LocalAppColors.current
  val textMeasurer = rememberTextMeasurer()
  val textStyle = remember { TextStyle(color = localAppColor.tvLv4, fontSize = 8.sp) }
  val beginTextLayoutResultState = rememberDerivedStateOfStructure {
    val beginTime = minOf(timePair.value.first, timePair.value.second)
    textMeasurer.measure(beginTime.toString(), textStyle)
  }
  val finalTextLayoutResultState = rememberDerivedStateOfStructure {
    val finalTime = maxOf(timePair.value.first, timePair.value.second)
    textMeasurer.measure(finalTime.toString(), textStyle)
  }
  val durationTextLayoutResultState = rememberDerivedStateOfStructure {
    textMeasurer.measure(
      abs(timePair.value.durationMinute()).toString(),
      textStyle
    )
  }
  return drawWithCache {
    val beginTextLayoutResult = beginTextLayoutResultState.value
    val finalTextLayoutResult = finalTextLayoutResultState.value
    val durationTextLayoutResult = durationTextLayoutResultState.value
    onDrawWithContent {
      drawContent()
      if (alpha.floatValue == 0F) return@onDrawWithContent
      drawText(
        textLayoutResult = beginTextLayoutResult,
        topLeft = Offset(
          -beginTextLayoutResult.size.width / 2F,
          -beginTextLayoutResult.size.height / 2F
        ),
        alpha = alpha.floatValue,
      )
      drawText(
        textLayoutResult = finalTextLayoutResult,
        topLeft = Offset(
          -finalTextLayoutResult.size.width / 2F,
          size.height - finalTextLayoutResult.size.height / 2F
        ),
        alpha = alpha.floatValue,
      )
      drawText(
        textLayoutResult = durationTextLayoutResult,
        topLeft = Offset(
          -durationTextLayoutResult.size.width / 2F,
          (size.height - durationTextLayoutResult.size.height) / 2
        ),
        alpha = alpha.floatValue,
      )
      drawLine(
        color = localAppColor.tvLv4,
        start = Offset(0F, beginTextLayoutResult.size.height / 2F),
        end = Offset(0F, (size.height - durationTextLayoutResult.size.height) / 2),
        alpha = alpha.floatValue,
      )
      drawLine(
        color = localAppColor.tvLv4,
        start = Offset(0F, (size.height + durationTextLayoutResult.size.height) / 2),
        end = Offset(0F, size.height - finalTextLayoutResult.size.height / 2F),
        alpha = alpha.floatValue,
      )
    }
  }
}