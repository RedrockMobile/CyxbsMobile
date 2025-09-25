package com.cyxbs.pages.course.view.item

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
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
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.getValue
import com.cyxbs.components.utils.compose.rememberWrapper
import com.cyxbs.components.utils.compose.setValue
import com.cyxbs.pages.course.view.item.touch.EdgeScroll
import com.cyxbs.pages.course.view.item.touch.LongPressMoveControllerImpl
import com.cyxbs.pages.course.view.item.touch.PressScaleControllerImpl
import com.cyxbs.pages.course.view.item.touch.longPressMove
import com.cyxbs.pages.course.view.item.touch.pressScale
import com.cyxbs.pages.course.view.overlay.CourseItemRange
import com.cyxbs.pages.course.view.page.LocalCoursePage
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import kotlinx.datetime.DayOfWeek
import kotlin.math.roundToInt

/**
 * .
 *
 * @author 985892345
 * @date 2025/2/14
 */
@Stable
class CourseItemWrapper<T : CourseItem>(
  val item: T,
  val page: Int,
  val dayOfWeek: DayOfWeek,
  val beginTime: MinuteTime,
  // 如果 finalTime < beginTime 则表示跨了一天
  val finalTime: MinuteTime,
)

// CourseItem 自身引用将作为 Compose 重组的 key()
// 更新后应尽量保证等价的 item 前后属于同一个对象
interface CourseItem {

  // item 支持的扩展功能
  val extension: CourseItemExtension

  /**
   * 绘制 item 内容，使用 [CourseDefaultItemContent]
   */
  val content: @Composable (
    modifier: Modifier,
    itemState: CourseItemState,
  ) -> Unit
}

// 用于描述 item 支持的扩展功能
interface CourseItemExtension

@Composable
fun CourseDefaultItemContent(
  modifier: Modifier = Modifier,
  itemState: CourseItemState,
  topText: String,
  bottomText: String,
  textColor: Color,
  backgroundColor: Color,
  onBuildDrawCache: CacheDrawScope.() -> DrawResult = { onDrawBehind { } },
  onClick: ((CourseItemRange) -> Unit)? = null,
) {
  if (itemState.realShowRange.isEmpty()) return
  val edgeScroll = remember { EdgeScroll() } // 边缘滚动处理
  edgeScroll.attachCompose()
  val coursePageContext = LocalCoursePage.current
  Box(
    modifier = modifier
      .then(
        itemState.timeline.createLayoutModifier(
          itemState.itemWrapper.beginTime,
          itemState.itemWrapper.finalTime
        )
      ).longPressMove( // 长按移动 item
        remember {
          LongPressMoveControllerImpl(
            itemState = itemState,
            edgeScroll = edgeScroll,
            pageContext = coursePageContext,
          )
        }
      ).pressScale( // 点击后的 Q 弹动画
        remember {
          PressScaleControllerImpl(
            itemState = itemState,
            coursePageContext = coursePageContext
          )
        }
      ).courseItemBackground(backgroundColor) // 通用背景
      .drawWithCache(onBuildDrawCache)
  ) {
    itemState.realShowRange.fastForEach { range ->
      CourseShowRange(
        range = range,
        itemRange = CourseItemRange(itemState.itemWrapper.beginTime, itemState.itemWrapper.finalTime),
        enableShowCoverTip = itemState.overlap.coveredItemList.isNotEmpty(),
        timeline = itemState.timeline,
        topText = topText,
        bottomText = bottomText,
        textColor = textColor,
        onClick = onClick,
      )
    }
  }
}

@Stable
fun Modifier.courseItemBackground(backgroundColor: Color): Modifier = composed {
  padding(1.dp)
    .background(LocalAppColors.current.topBg, RoundedCornerShape(8.dp))
    .padding(0.6.dp)
    .shadow(elevation = 0.5.dp, shape = RoundedCornerShape(8.dp))
    .background(LocalAppColors.current.topBg) // 遮挡 shadow 阴影
    .background(backgroundColor)
}

@Composable
private fun CourseShowRange(
  range: CourseItemRange,
  itemRange: CourseItemRange,
  enableShowCoverTip: Boolean,
  timeline: CourseTimeline,
  topText: String,
  bottomText: String,
  textColor: Color,
  onClick: ((CourseItemRange) -> Unit)? = null,
) {
  val calculateWeight: (CourseItemRange) -> Offset = {
    timeline.calculateRelativeWeight(
      beginTime1 = it.begin,
      finalTime1 = it.final,
      beginTime2 = itemRange.begin,
      finalTime2 = itemRange.final,
    )
  }
  var lastRange by rememberWrapper(range)
  var lastWeight by rememberWrapper { Snapshot.withoutReadObservation { calculateWeight(range) } }
  val weightAnim = remember {
    Animatable(
      typeConverter = Offset.VectorConverter,
      initialValue = lastWeight
    )
  }
  LaunchedEffect(range) {
    lastRange = range
    weightAnim.snapTo(lastWeight)
    val newWeight = calculateWeight(range)
    if (newWeight != lastWeight) {
      weightAnim.animateTo(newWeight)
    }
  }
  CourseItemTopBottomText(
    modifier = Modifier.layout { measurable, constraints ->
      val weight = if (weightAnim.isRunning) weightAnim.value else {
        // 这里使用 lastRange，确保在 range 发生改变时先触发动画
        calculateWeight(lastRange).also { lastWeight = it } // lastWeight 用于记录重组后计算出的最新 weight
      }
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

