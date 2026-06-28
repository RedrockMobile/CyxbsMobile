package com.cyxbs.pages.schedule.ui.dialog

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cyxbs.components.view.ui.ChooseDialogCompose

/**
 * todo 模块的确认弹窗。
 *
 * 内部基于项目通用的 [ChooseDialogCompose] 实现，提供 title + message + 双按钮的标准语义，
 * 用于删除 todo、完成 todo、未保存退出确认等场景。
 *
 * 复刻老端 [com.cyxbs.pages.schedule.ui.dialog.DeleteTodoDialog] /
 * [com.cyxbs.pages.schedule.ui.dialog.CheckTodoDialog] /
 * [com.cyxbs.pages.schedule.ui.dialog.DetailAlarmDialog] 的语义。
 */
@Composable
fun ScheduleConfirmDialog(
  show: Boolean,
  title: String,
  message: String,
  confirmText: String = "确定",
  dismissText: String = "取消",
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  val showState = remember { mutableStateOf(show) }
  LaunchedEffect(show) { showState.value = show }

  ChooseDialogCompose(
    showState = showState,
    positiveBtnText = confirmText,
    negativeBtnText = dismissText,
    onDismissRequest = onDismiss,
    onClickPositiveBtn = {
      onConfirm()
      onDismiss()
    },
    onClickNegativeBtn = onDismiss,
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold),
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
    Text(
      text = message,
      style = MaterialTheme.typography.body2,
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
    )
    Spacer(modifier = Modifier.height(4.dp))
  }
}
