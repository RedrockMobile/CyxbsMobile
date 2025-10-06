package com.cyxbs.pages.course.home.item.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.affair.api.AffairDateModel
import com.cyxbs.pages.course.home.item.AffairItemFactory
import com.cyxbs.pages.course.home.item.CourseAffairItem
import com.cyxbs.pages.course.view.item.CourseDefaultItemContent
import com.cyxbs.pages.course.view.item.CourseItemExtension
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.CourseItemWrapper
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.datetime.DayOfWeek

/**
 * .
 *
 * @author 985892345
 * @date 2025/9/23
 */
@Stable
data class DefaultAffairItemModel(
  override val page: Int, // 为 0 则表示整学期，否则表示第几周
  override val affairDateModel: AffairDateModel,
) : CourseAffairItem {

  @ImplProvider
  companion object Companion : AffairItemFactory {
    override fun createAffairItemModel(
      page: Int,
      affairDateModel: AffairDateModel
    ): CourseItemWrapper<CourseAffairItem> {
      return CourseItemWrapper(
        item = DefaultAffairItemModel(page, affairDateModel),
        page = page,
        dayOfWeek = affairDateModel.date.value.dayOfWeek,
        beginTime = affairDateModel.whatTime.value.timePair.value.first,
        finalTime = affairDateModel.whatTime.value.timePair.value.second,
      )
    }
  }

  val dayOfWeek: DayOfWeek
    get() = affairDateModel.date.value.dayOfWeek
  val beginTime: MinuteTime
    get() = affairDateModel.whatTime.value.timePair.value.first
  val finalTime: MinuteTime
    get() = affairDateModel.whatTime.value.timePair.value.second

  @Composable
  override fun CourseItemContent(modifier: Modifier, itemState: CourseItemState) {
    CourseDefaultItemContent(
      modifier = modifier,
      itemState = itemState,
      topText = affairDateModel.affair.title.collectAsState().value,
      bottomText = affairDateModel.affair.content.collectAsState().value,
      textColor = when {
        beginTime < MinuteTime(12, 0) -> 0xFFFF8015.dark(0xFFF0F0F2)
        beginTime < MinuteTime(18, 0) -> 0xFFFF6262.dark(0xFFF0F0F2)
        else -> 0xFF4066EA.dark(0xFFF0F0F2)
      },
      backgroundColor = when {
        beginTime < MinuteTime(12, 0) -> 0xFFF9E7D8.dark(0x26FFCCA1)
        beginTime < MinuteTime(18, 0) -> 0xFFF9E3E4.dark(0x26FF979B)
        else -> 0xFFDDE3F8.dark(0x269BB2FF)
      },
    ) {
      toast(affairDateModel.affair.title.value)
    }
  }

  override fun toString(): String {
    return "MobileAffairItemModel(page=$page" +
        ", dayOfWeek=$dayOfWeek" +
        ", begin=$beginTime" +
        ", final=$finalTime" +
        ", id=${affairDateModel.affair.id}" +
        ", title=${affairDateModel.affair.title.value}" +
        ", date=${affairDateModel.date.value}" +
        ")"
  }

  override val extension: CourseItemExtension
    get() = TODO("Not yet implemented")
}