package com.cyxbs.pages.course.frame.item.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.affair.api.AffairDateModel
import com.cyxbs.pages.course.frame.item.AffairItemFactory
import com.cyxbs.pages.course.frame.item.CourseAffairItem
import com.cyxbs.pages.course.view.item.CourseDefaultItemContent
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
import com.cyxbs.pages.course.view.item.ItemHierarchyWhatTime
import com.cyxbs.pages.course.view.item.extension.IMovableItemExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn

/**
 * .
 *
 * @author 985892345
 * @date 2025/9/23
 */
@Stable
class DefaultAffairItem(
  val affairWhatTime: ItemHierarchyWhatTime<CourseAffairItem>,
  coroutineScope: CoroutineScope,
  affairDateModel: AffairDateModel
) : CourseAffairItem(affairWhatTime, coroutineScope, affairDateModel) {

  companion object Companion : AffairItemFactory {
    override fun createAffairItemModel(
      whatTime: ItemHierarchyWhatTime<CourseAffairItem>,
      coroutineScope: CoroutineScope,
      affairDateModel: AffairDateModel
    ): CourseAffairItem {
      return DefaultAffairItem(whatTime, coroutineScope, affairDateModel)
    }
  }

  init {
    // 观察事务时间更新
    combine(
      SchoolCalendar.observeFirstMonDay(),
      affairDateModel.whatTime.mergeFlow,
      affairDateModel.date.mergeFlow
    ) { firstDate, whatTimeModel, date ->
      affairWhatTime.now.value = CourseItemWhatTime.Fixed(
        page = firstDate.daysUntil(affairDateModel.date.value) / 7 + 1,
        dayOfWeek = date.dayOfWeek,
        beginTime = whatTimeModel.timePair.value.first,
        finalTime = whatTimeModel.timePair.value.second,
      )
    }.launchIn(coroutineScope)
  }

  override val extension = DefaultAffairItemExtensionGroup(this)

  @Composable
  override fun CourseItemContent() {
    val whatTimeState = whatTime.now.collectAsState()
    val whatTimeFixed = whatTimeState.value
    val itemState = itemState
    CourseDefaultItemContent(
      itemState = itemState,
      topText = affairDateModel.idModel.title.mergeFlow.collectAsState("").value,
      bottomText = affairDateModel.idModel.content.mergeFlow.collectAsState("").value,
      textColor = when {
        whatTimeFixed.beginTime < MinuteTime(12, 0) -> 0xFFFF8015.dark(0xFFF0F0F2)
        whatTimeFixed.beginTime < MinuteTime(18, 0) -> 0xFFFF6262.dark(0xFFF0F0F2)
        else -> 0xFF4066EA.dark(0xFFF0F0F2)
      },
      backgroundColor = when {
        whatTimeFixed.beginTime < MinuteTime(12, 0) -> 0xFFF9E7D8.dark(0x26FFCCA1)
        whatTimeFixed.beginTime < MinuteTime(18, 0) -> 0xFFF9E3E4.dark(0x26FF979B)
        else -> 0xFFDDE3F8.dark(0x269BB2FF)
      },
    ) {
      toast(affairDateModel.idModel.title.value)
    }
  }

  override fun toString(): String {
    return "MobileAffairItemModel(fixed = ${whatTime.now.value}" +
        ", title=${affairDateModel.idModel.title.value}" +
        ", date=${affairDateModel.date.value}" +
        ", localId=${affairDateModel.idModel.localId}" +
        ", remoteId=${affairDateModel.idModel.remoteId}" +
        ")"
  }
}

class DefaultAffairItemExtensionGroup(
  val itemKeyImpl: DefaultAffairItem
) : IMovableItemExtension by MobileAffairMovableItemExtension(itemKeyImpl)

private class MobileAffairMovableItemExtension(
  val itemKeyImpl: DefaultAffairItem
) : IMovableItemExtension