package com.cyxbs.pages.course.frame.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.pages.affair.api.AffairDateModel
import com.cyxbs.pages.course.dialog.CourseBottomSheetDialogExtension
import com.cyxbs.pages.course.dialog.item.AffairBottomSheetDialog
import com.cyxbs.pages.course.dialog.rememberCourseBottomSheetDialogState
import com.cyxbs.pages.course.frame.header.CourseBottomSheetHeaderExtension
import com.cyxbs.pages.course.frame.header.CourseItemBottomSheetHeader
import com.cyxbs.pages.course.view.item.CourseDefaultItemContent
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
import com.cyxbs.pages.course.view.item.ItemHierarchyWhatTime
import com.cyxbs.pages.course.view.item.extension.IMovableItemExtension
import com.cyxbs.pages.course.view.item.modifier.CourseItemModifier
import com.cyxbs.pages.course.view.item.modifier.LayoutCoordinateSaveModifier
import com.cyxbs.pages.course.view.item.modifier.LayoutItemModifier
import com.cyxbs.pages.course.view.item.modifier.LongPressMoveItemModifier
import com.cyxbs.pages.course.view.item.modifier.PressScaleItemModifier
import com.cyxbs.pages.course.view.item.modifier.RoundedShadowItemModifier
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlin.math.roundToInt

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/25
 */
@Stable
class MobileCourseAffairItem(
  val affairWhatTime: ItemHierarchyWhatTime<CourseAffairItem>,
  coroutineScope: CoroutineScope,
  affairDateModel: AffairDateModel
) : CourseAffairItem(affairWhatTime, coroutineScope, affairDateModel) {

  @ImplProvider
  companion object Companion : AffairItemFactory {
    override fun createAffairItemModel(
      whatTime: ItemHierarchyWhatTime<CourseAffairItem>,
      coroutineScope: CoroutineScope,
      affairDateModel: AffairDateModel
    ): CourseAffairItem {
      return MobileCourseAffairItem(whatTime, coroutineScope, affairDateModel)
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

  override val extension = MobileCourseAffairItemExtensionGroup(this)

  @Composable
  override fun CourseItemContent() {
    val bottomSheetDialogState = rememberCourseBottomSheetDialogState()
    val itemState = itemState
    CourseDefaultItemContent(
      itemState = itemState,
      topText = affairDateModel.idModel.title.collectAsState().value,
      bottomText = affairDateModel.idModel.content.collectAsState().value,
      textColor = LocalAppColors.current.tvLv2,
      backgroundColor = Color.Transparent,
      modifierList = remember {
        persistentListOf(
          LayoutItemModifier, // 布局
          LongPressMoveItemModifier, // 长按移动 item
          LayoutCoordinateSaveModifier, // 保存 item 的坐标系
          PressScaleItemModifier, // 点击 Q 弹动画，需要在长按移动 item 之后
          RoundedShadowItemModifier, // 圆角+阴影
          AffairBackgroundItemModifier, // 事务背景斜线
        )
      }
    ) {
      bottomSheetDialogState.showDialog(itemState.overlap)
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

private object AffairBackgroundItemModifier : CourseItemModifier {
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

  override val itemState: CourseItemState
    get() = itemKeyImpl.itemState

  @Composable
  override fun CourseBottomSheetDialogContent() {
    AffairBottomSheetDialog(itemKeyImpl.affairDateModel)
  }
}

private class MobileCourseAffairCourseBottomSheetHeaderExtension(
  val itemKeyImpl: MobileCourseAffairItem
) : CourseBottomSheetHeaderExtension {
  @Composable
  override fun CourseBottomSheetHeaderContent(modifier: Modifier) {
    val bottomSheetDialogState = rememberCourseBottomSheetDialogState()
    CourseItemBottomSheetHeader(
      modifier = modifier,
      state = remember(this) { mutableStateOf("下个事务") },
      title = itemKeyImpl.affairDateModel.idModel.title.collectAsState().value,
      content = itemKeyImpl.affairDateModel.idModel.content.collectAsState().value,
      beginTime = itemKeyImpl.affairDateModel.whatTime.value.timePair.value.first,
      finalTime = itemKeyImpl.affairDateModel.whatTime.value.timePair.value.second,
      onClickTitle = {
        bottomSheetDialogState.showDialog(itemKeyImpl.extension)
      },
      onClickContent = {
      },
    )
  }
}