package com.cyxbs.pages.course.view.item

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.utils.compose.Wrapper
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.pages.course.view.overlay.CoveredRange
import com.cyxbs.pages.course.view.overlay.LocalOverlayController
import com.cyxbs.pages.course.view.overlay.OverlayData
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek

/**
 * .
 *
 * @author 985892345
 * @date 2025/2/14
 */
@Stable
interface CourseItem {

  // 用于单天课程 item 的遍历定位
  val key: String

  val page: Int
  val dayOfWeek: DayOfWeek
  val beginTime: MinuteTime
  val finalTime: MinuteTime // 如果 finalTime < beginTime 则表示跨了一天

  /**
   * 绘制 item 内容，使用 [CourseDefaultItemContent]
   */
  @Composable
  fun CourseItemContent(
    modifier: Modifier,
    overlap: OverlayData,
    timeline: CourseTimeline,
  )
}

@Composable
fun CourseDefaultItemContent(
  modifier: Modifier = Modifier,
  lastModifier: Modifier = Modifier,
  timeline: CourseTimeline,
  overlap: OverlayData,
  topText: String,
  bottomText: String,
  textColor: Color,
  backgroundColor: Color,
  onClick: ((CoveredRange) -> Unit)? = null,
) {
  CourseCardItem(
    modifier = modifier,
    lastModifier = lastModifier,
    timeline = timeline,
    item = overlap.item,
    backgroundColor = backgroundColor,
  ) {
    overlap.showRangeList.fastForEach {
      CourseItemTopBottomText(
        modifier = Modifier.then(
          timeline.createLayoutModifier(
            it.begin, it.final,
            overlap.item.beginTime to overlap.item.finalTime,
          )
        ).drawWithContent {
          drawContent()
          if (it.coveredItems.size > 0) {
            drawRoundRect(
              color = textColor,
              topLeft = Offset(x = size.width - 12.dp.toPx(), y = 4.dp.toPx()),
              size = Size(width = 6.dp.toPx(), height = 2.dp.toPx()),
              cornerRadius = CornerRadius(1.dp.toPx()),
            )
          }
        }.clickableNoIndicator {
          onClick?.invoke(it)
        },
        topText = topText,
        bottomText = bottomText,
        textColor = textColor,
      )
    }
  }
}

@Composable
fun CourseCardItem(
  modifier: Modifier = Modifier,
  lastModifier: Modifier = Modifier,
  timeline: CourseTimeline,
  item: CourseItem,
  backgroundColor: Color,
  content: @Composable (() -> Unit)? = null,
) {
  Box(
    modifier = modifier
      .then(timeline.createLayoutModifier(item.beginTime, item.finalTime))
      .padding(1.dp)
      .pointerPressRation(item) // 点击后的 Q 弹动画
      .background(LocalAppColors.current.topBg, RoundedCornerShape(8.dp))
      .padding(0.6.dp)
      .shadow(elevation = 0.5.dp, shape = RoundedCornerShape(8.dp))
      .background(LocalAppColors.current.topBg) // 遮挡 shadow 阴影
      .background(backgroundColor)
      .then(lastModifier)
  ) {
    content?.invoke()
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

// 点击后的 Q 弹动画实现
@Stable
@Composable
private fun Modifier.pointerPressRation(item: CourseItem): Modifier {
  val pointerOffset = remember { Wrapper<Offset?>(null) }
  val scale = remember { Animatable(initialValue = 1F) }
  val coroutineScope = rememberCoroutineScope()
  val localOverlayController = LocalOverlayController.current
  return pointerInput(item) {
    awaitEachGesture {
      val down = awaitFirstDown(pass = PointerEventPass.Initial)
      pointerOffset.value = down.position
      // 点击后会缩小，这里让被覆盖的 item 显示出来
      val overlayLock = localOverlayController.ignoreCoverOther(item)
      coroutineScope.launch { scale.animateTo(0.8F) }
      while (true) {
        val event = try {
          awaitPointerEvent(PointerEventPass.Initial)
        } catch (e: Exception) {
          overlayLock.unlock()
          throw e
        }
        val pointer = event.changes.firstOrNull { it.id == down.id }
        if (
          pointer == null ||
          pointer.isConsumed || // 被消耗
          pointer.changedToUp() || // 抬起
          pointer.isOutOfBounds(size, Size.Zero) || // 越界
          pointer.positionChange().getDistance() > viewConfiguration.touchSlop // 移动距离过大
        ) {
          pointerOffset.value = null
          coroutineScope.launch {
            try {
              scale.animateTo(1F)
            } finally {
              overlayLock.unlock()
            }
          }
          break
        }
      }
    }
  }.graphicsLayer {
    scaleX = scale.value
    scaleY = scale.value
    val pointer = pointerOffset.value
    if (pointer != null) {
      val centerX = size.width / 2F
      val centerY = size.height / 2F
      rotationX = -(pointer.y - centerY) / centerY * ((-0.0023F * size.height + 1.7F) * 16) // 上下翻转
      rotationY = (pointer.x - centerX) / centerX * ((-0.0023F * size.width + 1.7F) * 10) // 左右翻转
    }
  }
}