package com.cyxbs.pages.course.view.item

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.pages.course.view.data.CoveredRange
import com.cyxbs.pages.course.view.data.OverlayData
import com.cyxbs.pages.course.view.timeline.CourseTimeline
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
    beginTime = overlap.item.beginTime,
    finalTime = overlap.item.finalTime,
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
  beginTime: MinuteTime,
  finalTime: MinuteTime,
  backgroundColor: Color,
  content: @Composable (() -> Unit)? = null,
) {
  Box(
    modifier = modifier
      .then(timeline.createLayoutModifier(beginTime, finalTime))
      .padding(1.dp)
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