package com.cyxbs.pages.course.dialog.item.affair

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.rememberDerivedStateOfStructure
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.course.dialog.CourseItemBottomSheetDialogState
import com.cyxbs.pages.course.dialog.item.AffairBottomSheetDialogState.CurrentForm

/**
 * .
 *
 * @author 985892345
 * @date 2026/2/19
 */
@Composable
fun AffairEditBasicCompose(
  currentForm: CurrentForm.Edit,
  courseState: CourseItemBottomSheetDialogState,
) {
  Column {
    EditWeekWithTimePair(currentForm = currentForm)
    EditContent(
      modifier = Modifier.padding(top = 8.dp).onGloballyPositioned {
        // 设置 ime 的遮挡高度
        courseState.setImePeekBottomInWindow(it.positionInWindow().y + it.size.height + 10)
      },
      currentForm = currentForm
    )
  }
}

@Composable
private fun EditWeekWithTimePair(
  currentForm: CurrentForm.Edit,
) {
  val dateState = remember { mutableStateOf(currentForm.date) }
  val weekNumIsError = remember { mutableStateOf(false) }
  val dayOfWeekIsError = remember { mutableStateOf(false) }
  Row(verticalAlignment = Alignment.CenterVertically) {
    AffairEditWeekText(
      modifier = Modifier,
      dateState = dateState,
      weekNumIsError = weekNumIsError,
      dayOfWeekIsError = dayOfWeekIsError,
    )
    SelectionContainer {
      Text(
        text = remember { currentForm.whatTime.toString() },
        fontSize = 13.sp,
        color = LocalAppColors.current.tvLv2,
        modifier = Modifier.padding(start = 8.dp).clickableNoIndicator {
          if (weekNumIsError.value) {
            toast("周数不合法，请修改后再编辑时间段")
          } else if (dayOfWeekIsError.value) {
            toast("星期不合法，请修改后再编辑时间段")
          } else {
            // 流转到编辑时间状态
            currentForm.editState.value = CurrentForm.EditState.EditTime
          }
        },
      )
    }
  }
  DisposableEffect(Unit) {
    val check = {
      if (weekNumIsError.value) {
        "周数不合法，请修改后再保存"
      } else if (dayOfWeekIsError.value) {
        "星期不合法，请修改后再保存"
      } else null
    }
    currentForm.clickSaveCheck.add(check)
    onDispose {
      currentForm.clickSaveCheck.remove(check)
    }
  }
  LaunchedEffect(Unit) {
    snapshotFlow { dateState.value }.collect {
      currentForm.editor.setDate(it)
    }
  }
}

@Composable
private fun EditContent(
  modifier: Modifier,
  currentForm: CurrentForm.Edit,
) {
  val textFieldState = rememberTextFieldState(initialText = remember {
    currentForm.content
  })
  Box(modifier = modifier.fillMaxWidth()) {
    BasicTextField(
      modifier = Modifier.fillMaxWidth(),
      state = textFieldState,
      cursorBrush = SolidColor(TextFieldDefaults.textFieldColors().cursorColor(false).value),
      keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Text,
      ),
      textStyle = TextStyle(
        fontSize = 15.sp,
        color = LocalAppColors.current.tvLv2
      ),
    )
    if (rememberDerivedStateOfStructure { textFieldState.text.isEmpty() }.value) {
      Text(
        text = "请输入内容",
        fontSize = 15.sp,
        color = LocalAppColors.current.tvLv2.copy(alpha = 0.3F),
      )
    }
  }
  LaunchedEffect(Unit) {
    snapshotFlow { textFieldState.text }.collect {
      currentForm.editor.idModelEditor.setContent(it.toString())
    }
  }
  DisposableEffect(Unit) {
    val check = {
      if (textFieldState.text.isEmpty()) {
        "标题为空，请修改后再保存"
      } else null
    }
    currentForm.clickSaveCheck.add(check)
    onDispose {
      currentForm.clickSaveCheck.remove(check)
    }
  }
}