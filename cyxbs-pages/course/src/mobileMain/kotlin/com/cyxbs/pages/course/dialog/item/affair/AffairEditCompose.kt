package com.cyxbs.pages.course.dialog.item.affair

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.Icon
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.backHandler
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.rememberDerivedStateOfStructure
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.view.ui.rememberTextDialog
import com.cyxbs.pages.course.dialog.CourseItemBottomSheetDialogState
import com.cyxbs.pages.course.dialog.item.AffairBottomSheetDialogState
import com.cyxbs.pages.course.dialog.item.AffairBottomSheetDialogState.CurrentForm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * .
 *
 * @author 985892345
 * @date 2026/2/19
 */
@Composable
fun AffairEditCompose(
  currentForm: CurrentForm.Edit,
  courseState: CourseItemBottomSheetDialogState,
  affairState: AffairBottomSheetDialogState,
) {
  Column {
    EditTitleWithButton(
      currentForm = currentForm,
      courseState = courseState,
      affairState = affairState,
    )
    Box(modifier = Modifier.padding(top = 8.dp)) {
      when (currentForm.editState.value) {
        CurrentForm.EditState.EditBasic -> AffairEditBasicCompose(
          currentForm = currentForm,
          courseState = courseState,
        )
        CurrentForm.EditState.EditTime -> AffairEditTimeCompose(
          currentForm = currentForm,
          courseState = courseState,
        )
      }
    }
  }
  DisposableEffect(Unit) {
    courseState.bottomSheetState.userScrollEnabled.value = false
    onDispose {
      courseState.bottomSheetState.userScrollEnabled.value = true
    }
  }
}

@Composable
private fun EditTitleWithButton(
  currentForm: CurrentForm.Edit,
  courseState: CourseItemBottomSheetDialogState,
  affairState: AffairBottomSheetDialogState,
) {
  val focusRequester = remember { FocusRequester() }
  val coroutineScope = rememberCoroutineScope()
  val textFieldState = rememberTextFieldState(initialText = remember {
    affairState.currentFormState.value.title
  })
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    BasicTextField(
      modifier = Modifier.weight(1F)
        .focusRequester(focusRequester),
      state = textFieldState,
      lineLimits = TextFieldLineLimits.SingleLine,
      cursorBrush = SolidColor(TextFieldDefaults.textFieldColors().cursorColor(false).value),
      keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Text,
        imeAction = ImeAction.Next,
        showKeyboardOnFocus = false,
      ),
      textStyle = TextStyle(
        fontSize = 22.sp,
        color = LocalAppColors.current.tvLv2,
        fontWeight = FontWeight.Bold,
      ),
    )
    EditStateButtons(
      currentForm = currentForm,
      courseState = courseState,
      affairState = affairState,
      parentCoroutineScope = coroutineScope,
    )
  }
  val focusManager = LocalFocusManager.current
  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
    snapshotFlow { currentForm.editState.value }.collect {
      if (it != CurrentForm.EditState.EditBasic) {
        // 进入其他状态移除焦点，防止光标一直闪烁
        focusManager.clearFocus()
      }
    }
  }
  LaunchedEffect(Unit) {
    snapshotFlow { textFieldState.text }.collect {
      currentForm.editor.idModelEditor.setTitle(it.toString())
    }
  }
}

@Composable
private fun EditStateButtons(
  currentForm: CurrentForm.Edit,
  courseState: CourseItemBottomSheetDialogState,
  affairState: AffairBottomSheetDialogState,
  parentCoroutineScope: CoroutineScope,
) {
  val completeEditDialog = rememberTextDialog(
    text = "确定事务编辑完成？",
    negativeBtnText = "返回",
    onClickPositiveBtn = {
      parentCoroutineScope.launch {
        val show = currentForm.commit()
        if (show != null) {
          affairState.currentFormState.value = show
          dismiss()
        }
      }
    },
  )
  val isInEditBasic = rememberDerivedStateOfStructure {
    currentForm.editState.value == CurrentForm.EditState.EditBasic
  }
  val backPreStep = remember { {
    // 返回上一级
    if (currentForm.editTimePair.value != null) {
      // 时间轴编辑的返回不需要 check
      currentForm.editTimePair.value = null
    } else {
      val error = currentForm.clickPrevCheck.firstNotNullOfOrNull { it() }
      if (error != null) {
        toast(error)
      } else {
        currentForm.clickPrevCheck.clear()
        currentForm.clickSwitchTimeCheck.clear()
        currentForm.editState.value = CurrentForm.EditState.EditBasic
      }
    }
  } }
  Icon(
    contentDescription = if (isInEditBasic.value) "保存编辑" else "返回上一级",
    painter =
      if (isInEditBasic.value) rememberVectorPainter(Icons.Outlined.Check)
      else rememberVectorPainter(Icons.AutoMirrored.Outlined.ArrowBack),
    tint = LocalAppColors.current.tvLv2,
    modifier = Modifier.padding(start = 8.dp).clickableNoIndicator {
      if (isInEditBasic.value) {
        // 保存编辑
        val error = currentForm.clickSaveCheck.firstNotNullOfOrNull { it() }
        if (error != null) {
          toast(error)
        } else {
          currentForm.clickSaveCheck.clear()
          completeEditDialog.show()
        }
      } else {
        backPreStep.invoke()
      }
    }.backHandler(enabled = !isInEditBasic.value) {
      backPreStep.invoke()
    },
  )
  val cancelEditDialog = rememberTextDialog(
    text = "确定取消编辑？",
    negativeBtnText = "返回",
    onClickPositiveBtn = {
      val show = currentForm.cancelEdit()
      if (show != null) {
        courseState.currentPageItemFlow.value = courseState.dialogContents.value[0] // 还原之前的修改
        affairState.currentFormState.value = show
      }
      dismiss()
    }
  )
  Icon(
    contentDescription = "取消编辑",
    painter = rememberVectorPainter(Icons.Outlined.Close),
    tint = LocalAppColors.current.tvLv2,
    modifier = Modifier.padding(start = 8.dp).clickableNoIndicator {
      cancelEditDialog.show()
    },
  )

  DisposableEffect(Unit) {
    val oldOnDismiss = courseState.bottomSheetState.onDismissRequest
    courseState.bottomSheetState.onDismissRequest = {
      // bottomSheet 下滑时弹出一个确认取消编辑的弹窗
      cancelEditDialog.showAndCover(
        onClickPositiveBtnProxy = {
          onClickPositiveBtn.invoke(this)
          parentCoroutineScope.launch {
            // 需要使用外界的 coroutineScope，因为 EditStateButtons 函数立马就被移除了
            collapse()
          }
        },
      )
    }
    onDispose {
      courseState.bottomSheetState.onDismissRequest = oldOnDismiss
    }
  }
}