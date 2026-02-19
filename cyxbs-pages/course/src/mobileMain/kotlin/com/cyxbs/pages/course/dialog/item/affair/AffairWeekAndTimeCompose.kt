package com.cyxbs.pages.course.dialog.item.affair

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.course.dialog.CourseItemBottomSheetDialogState
import com.cyxbs.pages.course.dialog.item.AffairBottomSheetDialogState
import com.cyxbs.pages.course.dialog.item.AffairBottomSheetDialogState.CurrentForm
import kotlinx.coroutines.flow.collectLatest

/**
 * .
 *
 * @author 985892345
 * @date 2026/2/11
 */
@Composable
internal fun AffairWeekAndTimeCompose(
  modifier: Modifier,
  courseState: CourseItemBottomSheetDialogState,
  affairState: AffairBottomSheetDialogState,
) {
  val dateState = remember { mutableStateOf(affairState.currentFormState.value.date) }
  val weekNumIsError = remember { mutableStateOf(false) }
  val dayOfWeekIsError = remember { mutableStateOf(false) }
  Row(modifier) {
    AffairEditWeekText(
      modifier = Modifier,
      dateState = dateState,
      weekNumIsError = weekNumIsError,
      dayOfWeekIsError = dayOfWeekIsError,
      readOnly = affairState.currentFormState.value is CurrentForm.Show
    )
    SelectionContainer {
      Text(
        text = remember { affairState.currentFormState.value.whatTime.toString() },
        fontSize = 13.sp,
        color = LocalAppColors.current.tvLv2,
        modifier = Modifier.padding(start = 8.dp).clickableNoIndicator {
          val currentForm = affairState.currentFormState.value
          if (currentForm is CurrentForm.Edit) {
            if (weekNumIsError.value) {
              toast("周数不合法，请修改后再编辑时间段")
            } else if (dayOfWeekIsError.value) {
              toast("星期不合法，请修改后再编辑时间段")
            } else {
              currentForm.isInEditTime.value = true
            }
          }
        },
      )
    }
  }
  LaunchedEffect(affairState) {
    snapshotFlow { affairState.currentFormState.value }.collectLatest { currentForm ->
      when (currentForm) {
        is CurrentForm.Show -> {
          dateState.value = currentForm.date
        }
        is CurrentForm.Edit -> {
          currentForm.clickSaveCheck.add { // 这里添加后不需要手动移除
            if (weekNumIsError.value) {
              "周数不合法，请修改后再保存"
            } else if (dayOfWeekIsError.value) {
              "星期不合法，请修改后再保存"
            } else null
          }
        }
      }
    }
  }
  LaunchedEffect(affairState) {
    snapshotFlow { dateState.value }.collect {
      val currentFrom = affairState.currentFormState.value
      if (currentFrom is CurrentForm.Edit) {
        currentFrom.editor.setDate(it)
      }
    }
  }
}

