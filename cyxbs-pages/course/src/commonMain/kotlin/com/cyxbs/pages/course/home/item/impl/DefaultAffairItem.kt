package com.cyxbs.pages.course.home.item.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.affair.api.AffairDateModel
import com.cyxbs.pages.course.home.item.AffairItemFactory
import com.cyxbs.pages.course.home.item.CourseAffairItem
import com.cyxbs.pages.course.view.item.CourseDefaultItemContent
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
import com.cyxbs.pages.course.view.item.IMovableItemExtension
import kotlinx.coroutines.flow.combineTransform

/**
 * .
 *
 * @author 985892345
 * @date 2025/9/23
 */
@Stable
data class DefaultAffairItem(
  override val page: Int, // 为 0 则表示整学期，否则表示第几周
  override val affairDateModel: AffairDateModel,
) : CourseAffairItem {

  companion object Companion : AffairItemFactory {
    override fun createAffairItemModel(
      page: Int,
      affairDateModel: AffairDateModel
    ): CourseAffairItem {
      return DefaultAffairItem(page, affairDateModel)
    }
  }

  override val whatTime = CourseItemWhatTime.Changeable(
    fixed = CourseItemWhatTime.Fixed(
      page = page,
      dayOfWeek = affairDateModel.date.value.dayOfWeek,
      beginTime = affairDateModel.whatTime.value.timePair.value.first,
      finalTime = affairDateModel.whatTime.value.timePair.value.second,
    )
  )

  override val extension = DefaultAffairItemExtensionGroup(this)

  @Composable
  override fun CourseItemContent() {
    val whatTimeState by whatTime.observe().collectAsState()
    val itemState = itemState
    CourseDefaultItemContent(
      itemState = itemState,
      topText = affairDateModel.idModel.title.mergeFlow.collectAsState("").value,
      bottomText = affairDateModel.idModel.content.mergeFlow.collectAsState("").value,
      textColor = when {
        whatTimeState.beginTime < MinuteTime(12, 0) -> 0xFFFF8015.dark(0xFFF0F0F2)
        whatTimeState.beginTime < MinuteTime(18, 0) -> 0xFFFF6262.dark(0xFFF0F0F2)
        else -> 0xFF4066EA.dark(0xFFF0F0F2)
      },
      backgroundColor = when {
        whatTimeState.beginTime < MinuteTime(12, 0) -> 0xFFF9E7D8.dark(0x26FFCCA1)
        whatTimeState.beginTime < MinuteTime(18, 0) -> 0xFFF9E3E4.dark(0x26FF979B)
        else -> 0xFFDDE3F8.dark(0x269BB2FF)
      },
    ) {
      toast(affairDateModel.idModel.title.value)
    }
    // 更新 whatTime
    LaunchedEffect(Unit) {
      combineTransform(
        SchoolCalendar.observeFirstMonDay(),
        affairDateModel.date.mergeFlow,
        affairDateModel.whatTime.mergeFlow,
      ) { firstDate, date, whatTimeModel ->
        whatTimeModel.timePair.mergeFlow.collect { timePair ->
          val fixed = CourseItemWhatTime.Fixed(
            page = if (whatTime.now.page == 0) 0 else firstDate.daysUntil(date) / 7 + 1,
            dayOfWeek = date.dayOfWeek,
            beginTime = timePair.first,
            finalTime = timePair.second
          )
          emit(fixed)
        }
      }.collect {
        whatTime.update(it)
      }
    }
  }

  override fun toString(): String {
    return "MobileAffairItemModel(fixed = ${whatTime.now}" +
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