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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
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
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.plusDsl
import com.cyxbs.pages.course.view.item.modifier.CourseItemModifier
import com.cyxbs.pages.course.view.item.modifier.LayoutItemModifier
import com.cyxbs.pages.course.view.item.modifier.LongPressMoveItemModifier
import com.cyxbs.pages.course.view.item.modifier.PressScaleItemModifier
import com.cyxbs.pages.course.view.item.modifier.RoundedShadowItemModifier
import com.cyxbs.pages.course.view.page.LocalCoursePage
import com.cyxbs.pages.course.view.page.LocalCoursePageContext
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
  // todo 属性需要可变，待重新设计
  val item: T,
  val page: Int,
  val dayOfWeek: DayOfWeek,
  val beginTime: MinuteTime,
  // 如果 finalTime < beginTime 则表示跨了一天
  val finalTime: MinuteTime,
)

val LocalCourseItemState = staticCompositionLocalOf<CourseItemState> { error("未初始化") }

// CourseItem 自身引用将作为 Compose 重组的 key()
// 更新后应尽量保证等价的 item 前后属于同一个对象
interface CourseItem {

  // item 的时间信息
  val whatTime: CourseItemWhatTime

  // item 支持的扩展功能
  val extension: CourseItemExtension

  // item 所在的页面上下文
  val coursePage: LocalCoursePageContext
    @Composable
    get() = LocalCoursePage.current

  // item 当前 Compose 中的状态
  val itemState: CourseItemState
    @Composable
    get() = LocalCourseItemState.current

  /**
   * 绘制 item 内容，使用 [CourseDefaultItemContent]
   */
  @Composable
  fun CourseItemContent()
}

sealed interface CourseItemWhatTime {

  val now: Fixed

  data class Fixed(
    val page: Int, // 为 0 则表示整学期，否则表示第几周
    val dayOfWeek: DayOfWeek,
    val beginTime: MinuteTime,
    val finalTime: MinuteTime,
  ) : CourseItemWhatTime {
    override val now: Fixed
      get() = this
  }

  class Changeable(
    fixed: Fixed,
  ) : CourseItemWhatTime {

    private val flow = MutableStateFlow(fixed)

    override val now: Fixed
      get() = flow.value

    fun update(now: Fixed) {
      flow.value = now
    }

    fun observe(): StateFlow<Fixed> {
      return flow
    }
  }
}

// 用于描述 item 支持的扩展功能
interface CourseItemExtension

@Composable
fun CourseDefaultItemContent(
  itemState: CourseItemState,
  modifierList: ImmutableList<CourseItemModifier?> = remember {
    persistentListOf(
      LayoutItemModifier, // 布局
      PressScaleItemModifier, // 点击弹 Q 动画
      LongPressMoveItemModifier, // 长按移动 item
      RoundedShadowItemModifier, // 圆角+阴影
    )
  },
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
        then(it?.createModifier() ?: Modifier)
      }
    }.background(backgroundColor)
  ) {
    itemState.realShowRange.fastForEach { range ->
      CourseShowRange(
        range = range,
        itemRange = MinuteTimePair(
          itemState.item.whatTime.now.beginTime,
          itemState.item.whatTime.now.finalTime
        ),
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

@Composable
private fun CourseShowRange(
  range: MinuteTimePair,
  itemRange: MinuteTimePair,
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

