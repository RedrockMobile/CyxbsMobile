package com.cyxbs.pages.course.view.item

import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.pages.course.view.data.OverlayData
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
interface CourseItem {

  // 用于单天课程 item 的遍历定位
  val key: String

  val page: Int
  val dayOfWeek: DayOfWeek
  val beginTime: MinuteTime
  val finalTime: MinuteTime // 如果 finalTime < beginTime 则表示跨了一天

  /**
   * 绘制 item 内容，建议使用 [DefaultContent]
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
  modifier: Modifier,
  timeline: CourseTimeline,
  topText: String,
  bottomText: String,
  textColor: Color,
  backgroundColor: Color,
) {
  Card(
    modifier = modifier.padding(1.6.dp).layout { measurable, constraints ->
      val weight = timeline.calculateBeginFinalWeight(beginTime, finalTime)
      val height = (constraints.maxHeight * (weight.y - weight.x)).roundToInt()
      val placeable = measurable.measure(
        Constraints.fixed(constraints.maxWidth, height)
      )
      layout(placeable.width, placeable.height) {
        placeable.placeRelative(0, (constraints.maxHeight * weight.x).roundToInt())
      }
    },
    shape = RoundedCornerShape(8.dp),
    elevation = 0.5.dp,
    contentColor = Color.Transparent,
    backgroundColor = LocalAppColors.current.topBg, // 这里需要使用课表的背景颜色，在黑夜模式下遮挡 Card 的阴影
  ) {
    TopBottomText(
      top = topText,
      topColor = textColor,
      bottom = bottomText,
      bottomColor = textColor,
      modifier = Modifier.background(backgroundColor)
    )
  }
}

/**
 * 添加统一样式的顶部和底部文字
 */
@Composable
fun CourseItem.TopBottomText(
  top: String,
  topColor: Color,
  bottom: String,
  bottomColor: Color,
  modifier: Modifier = Modifier,
) {
  Layout(
    modifier = modifier.fillMaxSize()
      .padding(horizontal = 6.dp, vertical = 6.dp),
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