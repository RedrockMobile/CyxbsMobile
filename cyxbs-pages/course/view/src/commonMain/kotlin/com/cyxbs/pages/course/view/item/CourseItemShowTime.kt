package com.cyxbs.pages.course.view.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
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
import kotlin.math.abs

/**
 * .
 *
 * @author 985892345
 * @date 2026/1/12
 */

// 绘制开始结束时间线
@Composable
fun Modifier.drawBeginFinalTimeline(
  alpha: State<Float>,
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
      if (alpha.value == 0F) return@onDrawWithContent
      drawText(
        textLayoutResult = beginTextLayoutResult,
        topLeft = Offset(
          -beginTextLayoutResult.size.width / 2F,
          -beginTextLayoutResult.size.height / 2F
        ),
        alpha = alpha.value,
      )
      drawText(
        textLayoutResult = finalTextLayoutResult,
        topLeft = Offset(
          -finalTextLayoutResult.size.width / 2F,
          size.height - finalTextLayoutResult.size.height / 2F
        ),
        alpha = alpha.value,
      )
      drawText(
        textLayoutResult = durationTextLayoutResult,
        topLeft = Offset(
          -durationTextLayoutResult.size.width / 2F,
          (size.height - durationTextLayoutResult.size.height) / 2
        ),
        alpha = alpha.value,
      )
      drawLine(
        color = localAppColor.tvLv4,
        start = Offset(0F, beginTextLayoutResult.size.height / 2F),
        end = Offset(0F, (size.height - durationTextLayoutResult.size.height) / 2),
        alpha = alpha.value,
      )
      drawLine(
        color = localAppColor.tvLv4,
        start = Offset(0F, (size.height + durationTextLayoutResult.size.height) / 2),
        end = Offset(0F, size.height - finalTextLayoutResult.size.height / 2F),
        alpha = alpha.value,
      )
    }
  }
}