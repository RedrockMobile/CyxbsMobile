package com.cyxbs.pages.course.dialog.item.affair

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.view.ui.rememberTextDialog
import com.cyxbs.pages.course.dialog.CourseItemBottomSheetDialogState
import com.cyxbs.pages.course.dialog.item.AffairBottomSheetDialogState
import com.cyxbs.pages.course.dialog.item.AffairBottomSheetDialogState.CurrentForm
import kotlinx.coroutines.launch

/**
 * .
 *
 * @author 985892345
 * @date 2026/2/19
 */

@Composable
fun AffairShowCompose(
  currentForm: CurrentForm.Show,
  courseState: CourseItemBottomSheetDialogState,
  affairState: AffairBottomSheetDialogState,
) {
  Column {
    TitleWithButton(
      currentForm = currentForm,
      courseState = courseState,
      affairState = affairState,
    )
    WeekWithTimePair(
      modifier = Modifier.padding(top = 8.dp),
      currentForm = currentForm,
    )
    Content(
      modifier = Modifier.padding(top = 8.dp),
      currentForm = currentForm,
    )
  }
}

@Composable
private fun TitleWithButton(
  currentForm: CurrentForm.Show,
  courseState: CourseItemBottomSheetDialogState,
  affairState: AffairBottomSheetDialogState,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SelectionContainer(modifier = Modifier.weight(1F)) {
      Text(
        text = currentForm.title,
        fontSize = 22.sp,
        color = LocalAppColors.current.tvLv2,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
      )
    }
    ShowStateButtons(
      courseState = courseState,
      affairState = affairState,
    )
  }
}

@Composable
private fun ShowStateButtons(
  courseState: CourseItemBottomSheetDialogState,
  affairState: AffairBottomSheetDialogState,
) {
  Icon(
    contentDescription = "编辑事务",
    painter = rememberVectorPainter(Icons.Outlined.Settings),
    tint = LocalAppColors.current.tvLv2,
    modifier = Modifier.padding(start = 8.dp).clickableNoIndicator {
      // 编辑事务
      val currentForm = affairState.currentFormState.value
      if (currentForm is CurrentForm.Show) {
        val edit = currentForm.createEdit()
        if (edit != null) {
          affairState.currentFormState.value = edit
          // 进入编辑模式后将弹窗的内容设置为仅当前 item
          courseState.dialogContents.value = listOf(courseState.currentPageItemFlow.value!!)
        }
      }
    },
  )
  val coroutineScope = rememberCoroutineScope()
  val dialog = rememberTextDialog(
    text = "确定删除该事务吗？",
    negativeBtnText = "取消",
    onClickNegativeBtn = { dismiss() },
    onClickPositiveBtn = {
      // todo 复杂事务的删除单独处理，需要考虑删除单个还是删除全部亦或是删除后续
      val currentForm = affairState.currentFormState.value
      if (currentForm is CurrentForm.Show) {
        val edit = currentForm.createEdit()
        if (edit != null) {
          edit.editor.idModelEditor.clear()
          coroutineScope.launch {
            edit.commit()
            dismiss()
            courseState.bottomSheetState.collapse()
          }
        }
      }
    },
  )
  Icon(
    contentDescription = "删除事务",
    painter = rememberVectorPainter(Icons.Outlined.Delete),
    tint = LocalAppColors.current.tvLv2,
    modifier = Modifier.padding(start = 8.dp).clickableNoIndicator {
      dialog.show()
    },
  )
}

@Composable
private fun WeekWithTimePair(
  modifier: Modifier,
  currentForm: CurrentForm.Show,
) {
  Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
    AffairEditWeekText(
      modifier = Modifier,
      dateState = remember { mutableStateOf(currentForm.date) },
      weekNumIsError = remember { mutableStateOf(false) },
      dayOfWeekIsError = remember { mutableStateOf(false) },
      readOnly = true,
    )
    SelectionContainer {
      Text(
        text = remember(currentForm.whatTime) {
          currentForm.whatTime.toString()
        },
        fontSize = 13.sp,
        color = LocalAppColors.current.tvLv2,
        modifier = Modifier.padding(start = 8.dp),
      )
    }
  }
}

@Composable
private fun Content(modifier: Modifier, currentForm: CurrentForm.Show) {
  SelectionContainer(modifier = modifier) {
    Text(
      text = currentForm.content,
      fontSize = 15.sp,
      color = LocalAppColors.current.tvLv2,
    )
  }
}