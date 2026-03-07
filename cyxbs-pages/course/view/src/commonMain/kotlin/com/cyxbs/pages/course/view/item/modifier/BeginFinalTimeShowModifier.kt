package com.cyxbs.pages.course.view.item.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.utils.compose.rememberDerivedStateOfStructure
import com.cyxbs.pages.course.view.item.CourseItemState
import kotlin.math.abs

/**
 * item 显示开始结束时间
 *
 * 默认不显示，可使用 [enableShow] 开启
 *
 * @author 985892345
 * @date 2026/3/7
 */
object BeginFinalTimeShowModifier : CourseItemModifier {

  val enableShow = CourseItemState.ValueKey {
    mutableStateOf(false)
  }

  val alphaState = CourseItemState.ValueKey {
    mutableFloatStateOf(1F)
  }

  @Composable
  override fun createModifier(): Modifier {
    val itemState = itemState
    return if (!enableShow.get(itemState).value) Modifier else {
      LaunchedEffect(Unit) {
        // 同步 item 自身的 alpha
        snapshotFlow { itemState.alphaState.value }.collect {
          alphaState.get(itemState).floatValue = it
        }
      }
      Modifier.drawBeginFinalTimeline(
        alpha = alphaState.get(itemState),
        time1 = produceState(MinuteTime(0, 0)) {
          itemState.item.whatTime.now.collect {
            value = it.beginTime
          }
        },
        time2 = produceState(MinuteTime(0, 0)) {
          itemState.item.whatTime.now.collect {
            value = it.finalTime
          }
        }
      )
    }
  }
}


// 绘制开始结束时间线
@Composable
private fun Modifier.drawBeginFinalTimeline(
  alpha: FloatState,
  time1: State<MinuteTime>,
  time2: State<MinuteTime>,
): Modifier {
  val localAppColor = LocalAppColors.current
  val textMeasurer = rememberTextMeasurer()
  val textStyle = remember { TextStyle(color = localAppColor.tvLv4, fontSize = 8.sp) }
  val beginTextLayoutResultState = rememberDerivedStateOfStructure {
    val beginTime = minOf(time1.value, time2.value)
    textMeasurer.measure(beginTime.toString(), textStyle)
  }
  val finalTextLayoutResultState = rememberDerivedStateOfStructure {
    val finalTime = maxOf(time1.value, time2.value)
    textMeasurer.measure(finalTime.toString(), textStyle)
  }
  val durationTextLayoutResultState = rememberDerivedStateOfStructure {
    textMeasurer.measure(
      abs(time1.value.minutesUntil(time2.value)).toString(),
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