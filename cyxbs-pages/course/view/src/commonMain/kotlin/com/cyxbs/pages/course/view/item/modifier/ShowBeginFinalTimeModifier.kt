package com.cyxbs.pages.course.view.item.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
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
import com.cyxbs.pages.course.view.item.CourseItemState

/**
 * 绘制开始结束时间线，使用 [CourseItemState.showBeginFinalTimeAlpha] 进行展示
 *
 * @author 985892345
 * @date 2026/1/10
 */
object ShowBeginFinalTimeModifier : CourseItemModifier {
  @Composable
  override fun createModifier(): Modifier {
    return Modifier.drawBeginFinalTimeline(itemState)
  }
}

@Composable
private fun Modifier.drawBeginFinalTimeline(itemState: CourseItemState): Modifier {
  val enableShow = rememberDerivedStateOfStructure { itemState.showBeginFinalTimeAlpha.floatValue != 0F }.value
  if (!enableShow) return this // 不能展示时快速跳过
  val time1 = mutableStateOf(itemState.item.whatTime.now.value.beginTime)
  val time2 = mutableStateOf(itemState.item.whatTime.now.value.finalTime)
  LaunchedEffect(itemState) {
    itemState.item.whatTime.now.collect {
      time1.value = it.beginTime
      time2.value = it.finalTime
    }
  }
  return drawBeginFinalTimeline(
    alpha = itemState.showBeginFinalTimeAlpha,
    time1 = time1,
    time2 = time2,
  )
}


// 绘制开始结束时间线
@Composable
fun Modifier.drawBeginFinalTimeline(
  alpha: FloatState,
  time1: State<MinuteTime>,
  time2: State<MinuteTime>,
): Modifier {
  val localAppColor = LocalAppColors.current
  val textMeasurer = rememberTextMeasurer()
  val textStyle = remember { TextStyle(color = localAppColor.tvLv4, fontSize = 8.sp) }
  return drawWithCache {
    val beginTime = minOf(time1.value, time2.value)
    val finalTime = maxOf(time1.value, time2.value)
    val beginTextLayoutResult = textMeasurer.measure(beginTime.toString(), textStyle)
    val finalTextLayoutResult = textMeasurer.measure(finalTime.toString(), textStyle)
    val durationTextLayoutResult = textMeasurer.measure(
      beginTime.minutesUntil(finalTime).toString(),
      textStyle
    )
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
