package com.cyxbs.pages.course.frame.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.affair.api.AffairIdModelEditor
import com.cyxbs.pages.course.dialog.CourseItemBottomSheetDialogExtension
import com.cyxbs.pages.course.dialog.CourseItemBottomSheetDialogState
import com.cyxbs.pages.course.dialog.LocalCourseItemBottomSheetDialog
import com.cyxbs.pages.course.dialog.item.AffairBottomSheetDialog
import com.cyxbs.pages.course.dialog.item.AffairBottomSheetDialogState
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.impl.CourseCreateAffairItem
import com.cyxbs.pages.course.view.item.impl.PlatformCourseCreateAffairItem
import com.cyxbs.pages.course.view.item.impl.PlatformCourseCreateAffairItemFactory

/**
 * .
 *
 * @author 985892345
 * @date 2026/3/7
 */
object MobileCourseCreateAffairItemFactory : PlatformCourseCreateAffairItemFactory {
  override fun create(item: CourseCreateAffairItem): PlatformCourseCreateAffairItem {
    return MobileCourseCreateAffairItem(item)
  }
}

class MobileCourseCreateAffairItem(
  val item: CourseCreateAffairItem,
) : PlatformCourseCreateAffairItem {

  private val bottomSheetExtension = MobileCreateAffairBottomSheetExtension(item)

  init {
    item.extensions.add(bottomSheetExtension)
  }

  @Composable
  override fun CourseItemContentWrapper(content: @Composable ((onClick: (MinuteTimePair) -> Unit) -> Unit)) {
    val itemBottomSheetDialog = LocalCourseItemBottomSheetDialog.current
    content.invoke {
      // 点击事件
      itemBottomSheetDialog.showDialog(bottomSheetExtension)
    }
  }
}


private class MobileCreateAffairBottomSheetExtension(
  val item: CourseCreateAffairItem
) : CourseItemBottomSheetDialogExtension {

  override val itemState: CourseItemState
    get() = item.itemState

  @Composable
  override fun CourseBottomSheetDialogContent(state: CourseItemBottomSheetDialogState) {
    val dateModel = item.dateModelFlow.collectAsState().value ?: return
    AffairBottomSheetDialog(
      courseBottomSheetDialogState = state,
      affairBottomSheetDialogState = remember {
        AffairBottomSheetDialogState(
          currentForm = AffairBottomSheetDialogState.CurrentForm.Edit(
            editor = dateModel.idModel.tryCreateEditor()!!.findDateModelEditor(dateModel)!!,
            isCreateAffair = true
          ) { result ->
            result.onSuccess {
              when (it) {
                AffairIdModelEditor.EditResult.Deleted -> {
                  item.viewModel.resetTouchedItem()
                }
                AffairIdModelEditor.EditResult.Success -> {
                  toast("添加成功")
                  item.viewModel.resetTouchedItem() // 清空即可，后续由 CourseAffairItem 来展示了
                }
              }
            }
          }
        )
      }
    )
  }
}