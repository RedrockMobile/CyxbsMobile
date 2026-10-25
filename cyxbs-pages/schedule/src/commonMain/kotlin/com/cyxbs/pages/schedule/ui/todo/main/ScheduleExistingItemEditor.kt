package com.cyxbs.pages.schedule.ui.todo.main

import androidx.compose.runtime.Composable
import com.cyxbs.pages.schedule.ui.edit.EditScheduleDialog
import com.cyxbs.pages.schedule.viewmodel.ScheduleMainViewModel

/**
 * 已有日程卡片共用的编辑弹窗内容。
 *
 * 清单主页与分组全量页都只传入当前解析后的 occurrence；保存、删除和完成继续由同一个
 * [ScheduleMainViewModel] 路由范围语义，页面不复制业务命令。
 */
@Composable
internal fun ScheduleExistingItemEditor(
  item: ScheduleTodoItemUi,
  viewModel: ScheduleMainViewModel,
  onDismiss: () -> Unit,
) {
  EditScheduleDialog(
    show = true,
    editSchedule = item.schedule,
    editOccurrence = item.occurrence.toDomainOccurrence(),
    recurrenceId = item.occurrence.recurrenceId,
    categoryRepository = viewModel.repository,
    showCourseRelation = true,
    onDismiss = onDismiss,
    onConfirm = { state, scope, newCategory ->
      viewModel.saveSchedule(
        state,
        scope,
        item.occurrence.recurrenceId,
        newCategory,
      )
    },
    onDelete = { scope ->
      viewModel.deleteScheduleScoped(
        item.schedule.id,
        scope,
        item.occurrence.recurrenceId,
      )
      onDismiss()
    },
    onToggleCompleted = item.schedule.todoState?.let {
      { completed ->
        viewModel.completeSchedule(
          item.schedule.id,
          item.occurrence.recurrenceId,
          completed,
        )
      }
    },
  )
}
