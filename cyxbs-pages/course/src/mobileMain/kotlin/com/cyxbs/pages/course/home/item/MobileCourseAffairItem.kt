package com.cyxbs.pages.course.home.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.pages.affair.api.AffairDateModel
import com.cyxbs.pages.course.home.dialog.CourseBottomSheetDialogExtension
import com.cyxbs.pages.course.home.dialog.MobileCourseBottomSheetDialog
import com.cyxbs.pages.course.home.header.CourseBottomSheetHeaderExtension
import com.cyxbs.pages.course.home.header.CourseItemBottomSheetHeader
import com.cyxbs.pages.course.view.item.CourseDefaultItemContent
import com.cyxbs.pages.course.view.item.CourseItemWrapper
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.IMovableItemExtension
import com.cyxbs.pages.course.view.overlay.CourseItemOverlap
import com.g985892345.provider.api.annotation.ImplProvider
import kotlin.math.roundToInt

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/25
 */
@Stable
data class MobileCourseAffairItem(
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
        item = MobileCourseAffairItem(page = page, affairDateModel = affairDateModel),
        page = page,
        dayOfWeek = affairDateModel.date.value.dayOfWeek,
        beginTime = affairDateModel.whatTime.value.timePair.value.first,
        finalTime = affairDateModel.whatTime.value.timePair.value.second,
      )
    }
  }

  override val extension = MobileCourseAffairItemExtensionGroup(this)

  override val content: @Composable ((Modifier, CourseItemState) -> Unit) = { modifier, itemState ->
    val dialogContents = remember { mutableStateOf(emptyList<CourseBottomSheetDialogExtension>()) }
    CourseDefaultItemContent(
      modifier = modifier,
      itemState = itemState,
      topText = affairDateModel.idModel.title.collectAsState().value,
      bottomText = affairDateModel.idModel.content.collectAsState().value,
      textColor = LocalAppColors.current.tvLv2,
      backgroundColor = Color.Transparent,
      onBuildDrawCache = drawAffairItemBackground(),
    ) {
      fun collectCoveredItems(
        set: MutableSet<CourseItemWrapper<*>>,
        data: CourseItemOverlap,
      ): Set<CourseItemWrapper<*>> {
        data.coveredItemList.fastForEach {
          val item = it.itemOverlap.wrapper
          if (item.beginTime < itemState.itemWrapper.finalTime && item.finalTime > itemState.itemWrapper.beginTime) {
            set.add(item)
          }
          collectCoveredItems(set, it.itemOverlap)
        }
        return set
      }
      dialogContents.value = collectCoveredItems(linkedSetOf(itemState.itemWrapper), itemState.overlap)
        .mapNotNull { it.item as? CourseBottomSheetDialogExtension }
    }
    MobileCourseBottomSheetDialog(dialogContents = dialogContents)
  }
}

@Composable
private fun drawAffairItemBackground(): CacheDrawScope.() -> DrawResult {
  val stripeColor = 0xFFE4E7EC.dark(0xFF4D4B4C)
  return {
    val lineWidth = 8.dp.toPx() // 线条的宽度
    val lineSpace = lineWidth * 1.414213F // 线条之间垂直间隔
    onDrawBehind {
      var start = Offset(0F, lineSpace / 2)
      var end = Offset(lineSpace / 2, 0F)
      repeat((size.maxDimension + size.minDimension * 1.414213F / lineSpace / 2).roundToInt()) {
        drawLine(stripeColor, start, end, lineWidth)
        start = start.copy(y = start.y + lineSpace)
        end = end.copy(x = end.x + lineSpace)
      }
    }
  }
}

class MobileCourseAffairItemExtensionGroup(
  val itemKeyImpl: MobileCourseAffairItem
) : IMovableItemExtension by MobileCourseAffairMovableItemExtension(itemKeyImpl)
  , CourseBottomSheetDialogExtension by MobileCourseAffairCourseBottomSheetDialogExtension(itemKeyImpl)
  , CourseBottomSheetHeaderExtension by MobileCourseAffairCourseBottomSheetHeaderExtension(itemKeyImpl)

private class MobileCourseAffairMovableItemExtension(
  val itemKeyImpl: MobileCourseAffairItem
) : IMovableItemExtension {
  override fun enableExpandTimelineWhenMove(itemState: CourseItemState): Boolean {
    return true
  }
}

private class MobileCourseAffairCourseBottomSheetDialogExtension(
  val itemKeyImpl: MobileCourseAffairItem
) : CourseBottomSheetDialogExtension {
  override val courseBottomSheetDialogContent: @Composable (() -> Unit) = {

  }
}

private class MobileCourseAffairCourseBottomSheetHeaderExtension(
  val itemKeyImpl: MobileCourseAffairItem
) : CourseBottomSheetHeaderExtension {
  override val courseBottomSheetHeaderContent: @Composable ((Modifier) -> Unit) = { modifier ->
    val dialogContents = remember { mutableStateOf(emptyList<CourseBottomSheetDialogExtension>()) }
    CourseItemBottomSheetHeader(
      modifier = modifier,
      state = remember(this) { mutableStateOf("下个事务") },
      title = itemKeyImpl.affairDateModel.idModel.title.collectAsState().value,
      content = itemKeyImpl.affairDateModel.idModel.content.collectAsState().value,
      beginTime = itemKeyImpl.affairDateModel.whatTime.value.timePair.value.first,
      finalTime = itemKeyImpl.affairDateModel.whatTime.value.timePair.value.second,
      onClickTitle = {
        dialogContents.value = listOf(itemKeyImpl.extension)
      },
      onClickContent = {
      },
    )
    MobileCourseBottomSheetDialog(dialogContents = dialogContents)
  }
}