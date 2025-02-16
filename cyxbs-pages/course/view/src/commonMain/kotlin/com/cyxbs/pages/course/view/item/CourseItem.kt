package com.cyxbs.pages.course.view.item

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.course.view.overlap.OverlayData
import com.cyxbs.pages.course.view.timeline.CourseTimeline

/**
 * .
 *
 * @author 985892345
 * @date 2025/2/14
 */
@Stable
interface CourseItem {

  val beginTime: MinuteTimeDate

  val finalTime: MinuteTimeDate

  /**
   * 绘制 item 内容
   */
  @Composable
  fun Content(
    modifier: Modifier,
    overlap: OverlayData,
    timeline: CourseTimeline,
  )
}

@Composable
fun CourseItem.DefaultContent(
  topText: String,
  bottomText: String,
  textColor: Color,
  backgroundColor: Color,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.padding(1.6.dp).fillMaxSize(),
    shape = RoundedCornerShape(8.dp),
    elevation = 0.5.dp,
    backgroundColor = backgroundColor
  ) {
    TopBottomText(
      top = topText,
      topColor = textColor,
      bottom = bottomText,
      bottomColor = textColor,
    )
  }
}

/**
 * 添加统一样式的顶部和底部文字
 */
@Composable
private fun TopBottomText(
  top: String,
  topColor: Color,
  bottom: String,
  bottomColor: Color,
  modifier: Modifier = Modifier,
) {
  Layout(
    modifier = modifier.fillMaxSize()
      .padding(horizontal = 7.dp, vertical = 7.dp),
    content = {
      Text(
        text = top,
        textAlign = TextAlign.Center,
        color = topColor,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        fontSize = 11.sp,
        modifier = Modifier.fillMaxWidth()
      )
      Text(
        text = bottom,
        textAlign = TextAlign.Center,
        color = bottomColor,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        fontSize = 11.sp,
        modifier = Modifier.fillMaxWidth()
      )
    },
    measurePolicy = { measurables, constraints ->
      val topPlaceable = measurables[0].measure(constraints.copy(
        minHeight = 0
      ))
      val space = 2.dp.roundToPx()
      val bottomPlaceable = measurables[1].measure(constraints.copy(
        minHeight = 0,
        maxHeight = (constraints.maxHeight - topPlaceable.height - space).coerceAtLeast(0),
      ))
      layout(constraints.maxWidth, constraints.maxHeight) {
        topPlaceable.place(0, 0)
        if (topPlaceable.height + bottomPlaceable.height + space < constraints.maxHeight) {
          // 底部文本只有在能放下时才会显示
          bottomPlaceable.place(0, constraints.maxHeight - bottomPlaceable.height - space)
        }
      }
    }
  )
}