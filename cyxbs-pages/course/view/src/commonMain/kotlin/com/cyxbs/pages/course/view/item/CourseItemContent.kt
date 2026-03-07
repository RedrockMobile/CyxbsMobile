package com.cyxbs.pages.course.view.item

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.plusDsl
import com.cyxbs.components.utils.compose.rememberDerivedStateOfStructure
import com.cyxbs.pages.course.view.item.modifier.CourseItemModifier
import com.cyxbs.pages.course.view.item.modifier.LayoutCoordinateSaveModifier
import com.cyxbs.pages.course.view.item.modifier.LayoutItemModifier
import com.cyxbs.pages.course.view.item.modifier.LongPressMoveItemModifier
import com.cyxbs.pages.course.view.item.modifier.PressScaleItemModifier
import com.cyxbs.pages.course.view.item.modifier.RoundedShadowItemModifier
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * item 默认的 Compose 样式函数
 *
 * @author 985892345
 * @date 2026/1/12
 */


@Composable
fun CourseDefaultItemContent(
  itemState: CourseItemState,
  modifierList: ImmutableList<CourseItemModifier> = remember { createCourseDefaultModifierList() },
  topText: String,
  bottomText: String,
  textColor: Color,
  backgroundColor: Color,
  onClick: ((MinuteTimePair) -> Unit)? = null,
) {
  if (itemState.realShowRange.isEmpty()) return
  Box(
    modifier = Modifier.plusDsl {
      // 外界实现 CourseItemModifier 来修改 item 的样式
      modifierList.forEach {
        then(it.createModifier())
      }
    }.background(backgroundColor)
  ) {
    itemState.realShowRange.fastForEach { range ->
      CourseShowRange(
        range = range,
        itemRange = Snapshot.withoutReadObservation {
          MinuteTimePair(
            itemState.item.whatTime.now.value.beginTime,
            itemState.item.whatTime.now.value.finalTime
          )
        },
        enableShowCoverTip = itemState.overlap?.coveredItemList?.isNotEmpty() == true,
        timeline = itemState.item.coursePage.timeline,
        topText = topText,
        bottomText = bottomText,
        textColor = textColor,
        onClick = onClick,
      )
    }
  }
}

fun createCourseDefaultModifierList() = persistentListOf(
  LayoutItemModifier, // 布局
  LongPressMoveItemModifier, // 长按移动 item
  LayoutCoordinateSaveModifier, // 保存 item 的坐标系
  PressScaleItemModifier, // 点击 Q 弹动画，需要在长按移动 item 之后
  RoundedShadowItemModifier, // 圆角+阴影
)

@Composable
private fun CourseShowRange(
  range: MinuteTimePair, // 当前显示的区间
  itemRange: MinuteTimePair, // item 总区间
  enableShowCoverTip: Boolean,
  timeline: CourseTimeline,
  topText: String,
  bottomText: String,
  textColor: Color,
  onClick: ((MinuteTimePair) -> Unit)? = null,
) {
  val weightAnim = remember {
    Animatable(
      typeConverter = Offset.VectorConverter,
      initialValue = calculateWeight(timeline, range, itemRange)
    )
  }
  LaunchedEffect(range, itemRange) {
    weightAnim.animateTo(calculateWeight(timeline, range, itemRange))
  }
  CourseItemTopBottomText(
    modifier = Modifier.layout { measurable, constraints ->
      val weight = weightAnim.value
      val height = (constraints.maxHeight * (weight.y - weight.x)).roundToInt()
      val placeable = measurable.measure(
        Constraints.fixed(constraints.maxWidth, height)
      )
      layout(placeable.width, placeable.height) {
        placeable.placeRelative(0, (constraints.maxHeight * weight.x).roundToInt())
      }
    }.drawWithContent {
      drawContent()
      if (enableShowCoverTip) {
        // 右上角的重叠标志
        drawRoundRect(
          color = textColor,
          topLeft = Offset(x = size.width - 12.dp.toPx(), y = 4.dp.toPx()),
          size = Size(width = 6.dp.toPx(), height = 2.dp.toPx()),
          cornerRadius = CornerRadius(1.dp.toPx()),
        )
      }
    }.clickableNoIndicator {
      onClick?.invoke(range)
    },
    topText = topText,
    bottomText = bottomText,
    textColor = textColor,
  )
}

private fun calculateWeight(
  timeline: CourseTimeline,
  range: MinuteTimePair,
  itemRange: MinuteTimePair,
): Offset {
  Snapshot.withoutReadObservation {
    return timeline.calculateRelativeWeight(
      beginTime1 = range.first,
      finalTime1 = range.second,
      beginTime2 = itemRange.first,
      finalTime2 = itemRange.second,
    )
  }
}


/**
 * 添加统一样式的顶部和底部文字
 */
@Composable
fun CourseItemTopBottomText(
  modifier: Modifier = Modifier,
  topText: String,
  bottomText: String,
  textColor: Color,
) {
  Layout(
    modifier = modifier.fillMaxSize(),
    content = {
      Text(
        text = topText,
        textAlign = TextAlign.Center,
        color = textColor,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        fontSize = 11.sp,
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 6.dp)
      )
      Text(
        text = bottomText,
        textAlign = TextAlign.Center,
        color = textColor,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        fontSize = 11.sp,
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
      )
    },
    measurePolicy = { measurables, constraints ->
      val topPlaceable = measurables[0].measure(
        constraints.copy(
          minHeight = 0
        )
      )
      val space = 2.dp.roundToPx()
      val bottomPlaceable = measurables[1].measure(
        constraints.copy(
          minHeight = 0,
          maxHeight = (constraints.maxHeight - topPlaceable.height - space).coerceAtLeast(0),
        )
      )
      layout(constraints.maxWidth, constraints.maxHeight) {
        topPlaceable.place(0, 0)
        if (topPlaceable.height + bottomPlaceable.height + space < constraints.maxHeight) {
          // 底部文本只有在能放下时才会显示
          bottomPlaceable.place(0, constraints.maxHeight - bottomPlaceable.height)
        }
      }
    }
  )
}





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