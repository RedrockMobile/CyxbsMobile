package com.cyxbs.pages.course.home.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastForEach
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.pages.affair.api.AffairDateModel
import com.cyxbs.pages.course.home.dialog.BottomSheetDialogContent
import com.cyxbs.pages.course.home.dialog.MobileCourseBottomSheetDialog
import com.cyxbs.pages.course.home.header.BottomSheetItemHeader
import com.cyxbs.pages.course.home.header.CourseItemBottomSheetHeader
import com.cyxbs.pages.course.view.item.CourseDefaultItemContent
import com.cyxbs.pages.course.view.item.CourseItemModel
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.IMovableItemModel
import com.cyxbs.pages.course.view.overlay.CourseItemOverlap
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.datetime.DayOfWeek

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/25
 */
@Stable
class MobileAffairItemModel(
  override val page: Int, // 为 0 则表示整学期，否则表示第几周
  override val affairDateModel: AffairDateModel,
) : AffairItemModel, BottomSheetItemHeader, BottomSheetDialogContent, IMovableItemModel {

  companion object : AffairItemFactory {
    override fun createAffairItemModel(
      page: Int,
      affairDateModel: AffairDateModel
    ): AffairItemModel {
      return MobileAffairItemModel(page = page, affairDateModel = affairDateModel)
    }
  }

  override val weekItemKey: String = affairDateModel.hashCode().toString()

  override val dayOfWeek: DayOfWeek
    get() = affairDateModel.date.value.dayOfWeek
  override val beginTime: MinuteTime
    get() = affairDateModel.whatTime.value.timePair.value.first
  override val finalTime: MinuteTime
    get() = affairDateModel.whatTime.value.timePair.value.second

  @Composable
  override fun CourseItemContent(modifier: Modifier, itemState: CourseItemState) {
    val dialogContents = remember { mutableStateOf(emptyList<BottomSheetDialogContent>()) }
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
      fun collectCoveredItems(
        set: MutableSet<CourseItemModel>,
        data: CourseItemOverlap,
      ): Set<CourseItemModel> {
        data.coveredItemList.fastForEach {
          val item = it.itemOverlap.item
          if (item.beginTime < itemState.item.finalTime && item.finalTime > itemState.item.beginTime) {
            set.add(item)
          }
          collectCoveredItems(set, it.itemOverlap)
        }
        return set
      }
      dialogContents.value = collectCoveredItems(linkedSetOf(itemState.item), itemState.overlap)
        .filterIsInstance<BottomSheetDialogContent>()
    }
    MobileCourseBottomSheetDialog(dialogContents = dialogContents)
  }

  @Composable
  override fun BottomSheetHeaderContent(modifier: Modifier) {
    val dialogContents = remember { mutableStateOf(emptyList<BottomSheetDialogContent>()) }
    CourseItemBottomSheetHeader(
      modifier = modifier,
      state = remember(this) { mutableStateOf("下个事务") },
      title = affairDateModel.affair.title.collectAsState().value,
      content = affairDateModel.affair.content.collectAsState().value,
      beginTime = beginTime,
      finalTime = finalTime,
      onClickTitle = {
        dialogContents.value = listOf(this)
      },
      onClickContent = {
      },
    )
    MobileCourseBottomSheetDialog(dialogContents = dialogContents)
  }

  @Composable
  override fun BottomSheetDialogContent() {

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
}