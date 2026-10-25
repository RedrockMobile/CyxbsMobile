package com.cyxbs.pages.schedule.ui.category

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.pages.schedule.domain.model.OccurrenceStatus
import com.cyxbs.pages.schedule.ui.edit.EditScope
import com.cyxbs.pages.schedule.ui.todo.main.ScheduleTodoAccentColor
import com.cyxbs.pages.schedule.ui.todo.main.ScheduleTodoCard
import com.cyxbs.pages.schedule.ui.todo.main.ScheduleTodoItemUi
import com.cyxbs.pages.schedule.viewmodel.ScheduleMainViewModel

/** 分组完整列表按是否具有完成态分区，不再暴露日程最初来自清单还是事务。 */
internal enum class ScheduleCategoryItemTab { TODO, SCHEDULE }

/** 使用紧凑分段控件切换待办与普通日程，避免没有完成态的条目混入已完成分区。 */
@Composable
internal fun ScheduleCategoryItemTabs(
  selected: ScheduleCategoryItemTab,
  onSelect: (ScheduleCategoryItemTab) -> Unit,
) {
  val colors = LocalAppColors.current
  Surface(
    color = colors.topBg,
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 4.dp),
  ) {
    Row(Modifier.fillMaxWidth().padding(3.dp)) {
      ScheduleCategoryItemTab.entries.forEach { tab ->
        val isSelected = tab == selected
        Surface(
          color = if (isSelected) ScheduleTodoAccentColor else colors.topBg,
          contentColor = if (isSelected) {
            if (MaterialTheme.colors.isLight) colors.topBg else colors.tvLv1
          } else {
            colors.tvLv2
          },
          shape = RoundedCornerShape(9.dp),
          modifier = Modifier
            .weight(1F)
            .height(34.dp)
            .clickableNoIndicator { onSelect(tab) },
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(
              text = if (tab == ScheduleCategoryItemTab.TODO) "待办事项" else "普通日程",
              fontSize = 14.sp,
              fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            )
          }
        }
      }
    }
  }
}

/** 单张分组日程卡片；是否支持完成和置顶取决于其当前是否已关联清单。 */
@Composable
internal fun ScheduleCategoryItemCard(
  modifier: Modifier,
  item: ScheduleTodoItemUi,
  viewModel: ScheduleMainViewModel,
  manageMode: Boolean,
  selected: Boolean,
  pinned: Boolean,
  onOpen: () -> Unit,
  onTogglePin: () -> Unit,
) {
  val isInTodo = item.schedule.todoState != null
  val canCompleteOccurrence = isInTodo &&
    (item.schedule.recurrence == null || item.occurrence.recurrenceId != null)
  ScheduleTodoCard(
    modifier = modifier,
    item = item,
    highlighted = false,
    isPinned = isInTodo && pinned,
    manageMode = manageMode,
    selected = selected,
    onSelect = { viewModel.toggleSelect(item.schedule.id) },
    onLongPress = {
      if (!manageMode && viewModel.canSubmitMutation()) {
        viewModel.enterManageMode()
        viewModel.toggleSelect(item.schedule.id)
      }
    },
    onOpen = onOpen,
    onComplete = if (canCompleteOccurrence) {
      {
        viewModel.completeSchedule(
          item.schedule.id,
          item.occurrence.recurrenceId,
          completed = item.occurrence.status != OccurrenceStatus.COMPLETED,
        )
      }
    } else null,
    onTogglePin = if (isInTodo) onTogglePin else null,
    isLinkedToCalendar = item.schedule.linkedToCourse,
    onToggleCalendarLink = { viewModel.toggleCourseProjection(item.schedule.id) },
    onDelete = {
      viewModel.deleteScheduleScoped(
        item.schedule.id,
        if (item.occurrence.recurrenceId == null) EditScope.ALL else EditScope.THIS_ONLY,
        item.occurrence.recurrenceId,
      )
    },
  )
}
