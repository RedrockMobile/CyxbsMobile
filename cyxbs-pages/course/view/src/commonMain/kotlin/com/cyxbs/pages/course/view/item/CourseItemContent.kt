package com.cyxbs.pages.course.view.item

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
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
    val realShowRanges = itemState.realShowRange
    val clickedRangeKey = remember(itemState) { mutableStateOf<MinuteTimePair?>(null) }
    realShowRanges.fastForEach { range ->
      // 多个可见片段收敛为完整区间时，复用被点击片段的组合节点，使展开动画从点击处开始。
      // 平时仍使用真实区间作为 key，避免不同片段之间错误复用 Animatable。
      val animationKey = if (realShowRanges.size == 1) clickedRangeKey.value ?: range else range
      key(animationKey) {
        CourseRealShowRange(
          itemState = itemState,
          range = range,
          itemRange = itemRange,
          topText = topText,
          bottomText = bottomText,
          textColor = textColor,
          onClick = { clickedRange ->
            clickedRangeKey.value = clickedRange
            onClick?.invoke(clickedRange)
          },
        )
      }
    }
  }
}

@Composable
private fun CourseRealShowRange(
  itemState: CourseItemState,
  range: MinuteTimePair,
  itemRange: MinuteTimePair,
  topText: String,
  bottomText: String,
  textColor: Color,
  onClick: ((MinuteTimePair) -> Unit)?,
) {
  val textPadding = itemState.calculatePointTextPadding(range)
  CourseShowRange(
    range = range,
    itemRange = itemRange,
    timeline = itemState.item.coursePage.timeline,
    coverTipColor = if (itemState.overlap?.coveredItemList?.isNotEmpty() == true) textColor else Color.Transparent,
  ) {
    CourseItemTopBottomText(
      modifier = it
        .clickableNoIndicator { onClick?.invoke(range) }
        .padding(top = textPadding.width, bottom = textPadding.height),
      topText = topText,
      bottomText = bottomText,
      textColor = textColor,
    )
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

/**
 * 根据直接上层的零时长切割锚点，为标题或描述增加少量间距。
 *
 * 片段从时间点开始时增加顶部 padding，片段在时间点结束时增加底部 padding。这里只移动文字
 * 内容，不改变 item 的时间范围、整体高度、背景或点击区域。
 */
private fun CourseItemState.calculatePointTextPadding(
  range: MinuteTimePair,
): DpSize {
  var hasPointAtTop = false
  var hasPointAtBottom = false
  overlap?.coveredRangeList?.forEach { cover ->
    if (cover.range.first != cover.range.second) return@forEach
    if (cover.range.first == range.first) hasPointAtTop = true
    if (cover.range.first == range.second) hasPointAtBottom = true
  }
  return DpSize(
    width = if (hasPointAtTop) POINT_TEXT_PADDING else 0.dp,
    height = if (hasPointAtBottom) POINT_TEXT_PADDING else 0.dp,
  )
}

private fun calculateWeight(
  timeline: CourseTimeline,
  range: MinuteTimePair,
  itemRange: MinuteTimePair,
): Offset {
  Snapshot.withoutReadObservation {
    // 零时长事项的业务区间没有相对比例；它的外层已经提供最小视觉高度，内容应填满该高度。
    if (itemRange.first == itemRange.second) return Offset(0F, 1F)
    return timeline.calculateRelativeWeight(
      beginTime1 = range.first,
      finalTime1 = range.second,
      beginTime2 = itemRange.first,
      finalTime2 = itemRange.second,
    )
  }
}

/** 时间点只需让相邻文字稍微远离边界，无需按其完整视觉高度避让。 */
private val POINT_TEXT_PADDING = 8.dp
