package com.cyxbs.pages.course.view.item.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.affair.api.AffairDateModel
import com.cyxbs.pages.course.view.item.CourseDefaultItemContent
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
import com.cyxbs.pages.course.view.item.ItemHierarchyWhatTime
import com.cyxbs.pages.course.view.item.createCourseDefaultModifierList
import com.cyxbs.pages.course.view.item.extension.IMovableItemExtension
import com.cyxbs.pages.course.view.item.modifier.CourseItemModifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlin.math.roundToInt

/**
 * .
 *
 * @author 985892345
 * @date 2026/3/7
 */
class CourseAffairItem(
  val affairWhatTime: ItemHierarchyWhatTime<CourseAffairItem>,
  coroutineScope: CoroutineScope,
  val affairDateModel: AffairDateModel,
  platformItemFactory: PlatformCourseAffairItemFactory,
) : CourseItem(affairWhatTime, coroutineScope) {

  init {
    // 观察事务时间更新
    combine(
      SchoolCalendar.observeFirstMonDay(),
      affairDateModel.whatTime.mergeFlow.flatMapLatest { it.timePair.mergeFlow },
      affairDateModel.date.mergeFlow
    ) { firstDate, timePair, date ->
      affairWhatTime.now.value = CourseItemWhatTime.Fixed(
        page = firstDate.daysUntil(date) / 7 + 1,
        dayOfWeek = date.dayOfWeek,
        beginTime = timePair.first,
        finalTime = timePair.second,
      )
    }.launchIn(coroutineScope)
  }

  init {
    extensions.add(CourseAffairMovableItemExtension()) // 支持长按移动
  }

  // 下层到每个平台的课程配置
  private val platform = platformItemFactory.create(this)

  @Composable
  override fun CourseItemContent() {
    platform.CourseItemContentWrapper {
      Content(onClick = it)
    }
  }
}

@Composable
private fun CourseAffairItem.Content(
  onClick: ((MinuteTimePair) -> Unit)?,
) {
  val itemState = itemState
  CourseDefaultItemContent(
    itemState = itemState,
    topText = affairDateModel.idModel.title.mergeFlow.collectAsState("").value,
    bottomText = affairDateModel.idModel.content.mergeFlow.collectAsState("").value,
    textColor = LocalAppColors.current.tvLv2,
    backgroundColor = Color.Transparent,
    modifierList = remember { createCourseDefaultModifierList().add(AffairBackgroundItemModifier) },
    onClick = onClick,
  )
}

// 事务背景斜线
object AffairBackgroundItemModifier : CourseItemModifier {
  @Composable
  override fun createModifier(): Modifier {
    val stripeColor = 0xFFE4E7EC.dark(0xFF4D4B4C)
    return Modifier.drawBehind {
      val lineWidth = 8.dp.toPx() // 线条的宽度
      val lineSpace = lineWidth * 1.414F // 线条之间垂直间隔
      var start = Offset(-3.dp.toPx(), lineSpace)
      var end = Offset(lineSpace, -3.dp.toPx())
      repeat(((size.width + size.height) / lineSpace / 2).roundToInt()) {
        drawLine(stripeColor, start, end, lineWidth)
        start = start.copy(y = start.y + lineSpace * 2)
        end = end.copy(x = end.x + lineSpace * 2)
      }
    }
  }
}

// 事务支持长按移动
private class CourseAffairMovableItemExtension : IMovableItemExtension {
  override fun enableExpandTimelineWhenMove(itemState: CourseItemState): Boolean {
    return true
  }
}

// 下层到每个平台的事务配置
interface PlatformCourseAffairItemFactory {
  fun create(item: CourseAffairItem): PlatformCourseAffairItem
}

interface PlatformCourseAffairItem {
  @Composable
  fun CourseItemContentWrapper(content: @Composable (onClick: ((MinuteTimePair) -> Unit)?) -> Unit)

  companion object : PlatformCourseAffairItem {
    @Composable
    override fun CourseItemContentWrapper(content: @Composable ((onClick: ((MinuteTimePair) -> Unit)?) -> Unit)) {
      content.invoke { toast("点击事件未实现") }
    }
  }
}

