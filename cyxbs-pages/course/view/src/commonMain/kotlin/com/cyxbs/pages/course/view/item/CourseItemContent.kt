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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.plusDsl
import com.cyxbs.pages.course.view.item.modifier.BeginFinalTimeShowModifier
import com.cyxbs.pages.course.view.item.modifier.CourseItemModifier
import com.cyxbs.pages.course.view.item.modifier.LayoutCoordinateSaveModifier
import com.cyxbs.pages.course.view.item.modifier.LayoutItemModifier
import com.cyxbs.pages.course.view.item.modifier.LongPressMoveItemModifier
import com.cyxbs.pages.course.view.item.modifier.PressScaleItemModifier
import com.cyxbs.pages.course.view.item.modifier.RoundedShadowItemModifier
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.math.roundToInt
import androidx.compose.runtime.collectAsState

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
    val itemRange = MinuteTimePair(
      itemState.item.whatTime.now.collectAsState().value.beginTime,
      itemState.item.whatTime.now.collectAsState().value.finalTime
    )
    itemState.realShowRange.fastForEach { range ->
      CourseShowRange(
        range = range,
        itemRange = itemRange,
        timeline = itemState.item.coursePage.timeline,
        coverTipColor = if (itemState.overlap?.coveredItemList?.isNotEmpty() == true) textColor else Color.Transparent,
      ) {
        CourseItemTopBottomText(
          modifier = it.clickableNoIndicator {
            onClick?.invoke(range)
          },
          topText = topText,
          bottomText = bottomText,
          textColor = textColor,
        )
      }
    }
  }
}

fun createCourseDefaultModifierList() = persistentListOf(
  LayoutItemModifier, // 布局
  LongPressMoveItemModifier, // 长按移动 item
  LayoutCoordinateSaveModifier, // 保存 item 的坐标系
  BeginFinalTimeShowModifier, // 显示 item 开始和结束时间，默认不会显示
  PressScaleItemModifier, // 点击 Q 弹动画，需要在长按移动 item 之后
  RoundedShadowItemModifier, // 圆角+阴影
)

@Composable
fun CourseShowRange(
  range: MinuteTimePair, // 当前显示的区间
  itemRange: MinuteTimePair, // item 总区间
  timeline: CourseTimeline,
  coverTipColor: Color,
  enableAnim: Boolean = true,
  content: @Composable (Modifier) -> Unit,
) {
  val weightAnim = remember {
    Animatable(
      typeConverter = Offset.VectorConverter,
      initialValue = calculateWeight(timeline, range, itemRange)
    )
  }
  LaunchedEffect(range, itemRange) {
    if (enableAnim) {
      weightAnim.animateTo(calculateWeight(timeline, range, itemRange))
    } else {
      val weight = calculateWeight(timeline, range, itemRange)
      weightAnim.snapTo(weight)
    }
  }
  content.invoke(
    Modifier.layout { measurable, constraints ->
      val weight = weightAnim.value
      val height = (constraints.maxHeight * (weight.y - weight.x)).roundToInt().coerceAtLeast(1)
      val placeable = measurable.measure(
        Constraints.fixed(constraints.maxWidth, height)
      )
      layout(placeable.width, placeable.height) {
        placeable.placeRelative(0, (constraints.maxHeight * weight.x).roundToInt())
      }
    }.drawWithContent {
      drawContent()
      if (coverTipColor != Color.Transparent) {
        // 右上角的重叠标志
        drawRoundRect(
          color = coverTipColor,
          topLeft = Offset(x = size.width - 12.dp.toPx(), y = 4.dp.toPx()),
          size = Size(width = 6.dp.toPx(), height = 2.dp.toPx()),
          cornerRadius = CornerRadius(1.dp.toPx()),
        )
      }
    }
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




