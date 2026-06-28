package com.cyxbs.pages.schedule.ui.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.schedule.data.model.ScheduleEntity
import com.cyxbs.pages.schedule.ui.dialog.ScheduleBottomSheet
import com.cyxbs.pages.schedule.ui.dialog.ScheduleCalendarPickerDialog
import com.cyxbs.pages.schedule.ui.dialog.ScheduleConfirmDialog
import com.cyxbs.pages.schedule.ui.timeline.formatScheduleDateTime
import com.cyxbs.pages.schedule.widget.rememberIcAddtodoCategory
import com.cyxbs.pages.schedule.widget.rememberIcAddtodoNotice
import com.cyxbs.pages.schedule.widget.rememberIcAddtodoRepeat
import com.cyxbs.pages.schedule.widget.rememberIcDetailClassifyMore

/**
 * 添加 / 编辑日程的统一底部弹窗 —— **邮子清单与课表共用同一套编辑入口**。
 *
 * 设计说明（对齐用户「课表编辑=邮子清单编辑共用一套」+「保持一致」的诉求）：affair 原编辑器深度耦合
 * `AbstractCourseFrame`（周数输入依赖课表帧），无法在邮子清单独立场景运行；故以可独立运行的 todo 式
 * BottomSheet 表单为底座，绑定 [ScheduleEntity]/[com.cyxbs.pages.schedule.recurrence.Recurrence]，
 * 重复改为 RFC5545 [RecurrenceEditDialog]。两处入口调用同一弹窗，天然一致。
 *
 * 三态：编辑「重复系列的某一次」（[editSchedule] 重复 && [occurrenceDate] != null）时，保存/删除会先弹
 * [EditScopeChooserSheet] 三选一，再经 [onConfirm]/[onDelete] 把 [EditScope] 回传给外层路由到仓库。
 *
 * @param occurrenceDate 被点击那一次的锚点日期；从课表/时间轴某天点入时传，邮子清单直接编辑整条传 null。
 * @param onConfirm 保存回调，回传表单状态与作用域。
 * @param onDelete 删除回调（编辑态才有意义），回传作用域；新建态为 null。
 */
@Composable
fun EditScheduleDialog(
  show: Boolean,
  editSchedule: ScheduleEntity? = null,
  occurrenceDate: Date? = null,
  categories: List<CategoryDisplay> = emptyList(),
  onAddCategory: (String) -> Unit = {},
  onDeleteCategory: (String) -> Unit = {},
  onDismiss: () -> Unit,
  onConfirm: (EditScheduleState, EditScope) -> Unit,
  onDelete: ((EditScope) -> Unit)? = null,
) {
  if (!show) return

  val state = rememberEditScheduleState(editSchedule)

  var showTimePicker by remember { mutableStateOf<TimeTarget?>(null) }
  var showRepeat by remember { mutableStateOf(false) }
  var showUnsavedExit by remember { mutableStateOf(false) }
  // 三态选择：保存或删除时若需要选作用域，记录其用途。
  var scopeChooser by remember { mutableStateOf<ScopeAction?>(null) }

  // 是否为「编辑重复系列的某一次」——决定保存/删除是否要弹三态。
  val needScope = editSchedule?.recurrence != null && occurrenceDate != null

  val requestDismiss = { if (state.isChanged) showUnsavedExit = true else onDismiss() }

  val doSave = {
    if (needScope) scopeChooser = ScopeAction.SAVE
    else { onConfirm(state, EditScope.ALL); onDismiss() }
  }
  val doDelete = {
    if (onDelete != null) {
      if (needScope) scopeChooser = ScopeAction.DELETE
      else { onDelete(EditScope.ALL); onDismiss() }
    }
  }

  ScheduleBottomSheet(
    show = true,
    onDismiss = onDismiss,
    onDismissRequest = { if (state.isChanged) { showUnsavedExit = true; false } else true },
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
      EditTopBar(
        canConfirm = state.canConfirm,
        showDelete = editSchedule != null && onDelete != null,
        onCancel = requestDismiss,
        onDelete = doDelete,
        onSave = doSave,
      )

      Spacer(modifier = Modifier.height(18.dp))
      ScheduleFilledTextField(
        state = state.title, hint = "添加待办事项", singleLine = true, cornerRadius = 22.dp,
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(modifier = Modifier.height(12.dp))
      ScheduleFilledTextField(
        state = state.detail, hint = "备注（可选）", maxLength = 100, minHeight = 72.dp,
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(modifier = Modifier.height(16.dp))
      ScheduleTimeTypeToggle(isInterval = state.isInterval, onChange = { state.isInterval = it })
      Spacer(modifier = Modifier.height(12.dp))
      EditTimeSection(
        isInterval = state.isInterval,
        startTime = state.startTime,
        endTime = state.endTime,
        onPick = { showTimePicker = it },
        onClearDeadline = { state.endTime = "" },
      )
      Spacer(modifier = Modifier.height(12.dp))
      EditRepeatSection(
        recurrence = state.outputRecurrence,
        onClickRepeat = { showRepeat = true },
      )
      Spacer(modifier = Modifier.height(12.dp))
      EditCategorySection(
        selectedType = state.type,
        categories = categories,
        onSelectType = { state.type = it },
        onAddCategory = onAddCategory,
        onDeleteCategory = { t ->
          if (t == state.type) {
            state.type = categories.firstOrNull { it.type != t }?.type ?: ScheduleEntity.TYPE_OTHER
          }
          onDeleteCategory(t)
        },
      )
    }
  }

  // 时间选择器（截止 / 开始 / 结束 复用）
  ScheduleCalendarPickerDialog(
    show = showTimePicker != null,
    onDismiss = { showTimePicker = null },
    onConfirm = { year, month, day, hour, minute ->
      val text = formatScheduleDateTime(year, month, day, hour, minute)
      when (showTimePicker) {
        TimeTarget.START -> state.startTime = text
        TimeTarget.END, TimeTarget.DEADLINE -> state.endTime = text
        null -> {}
      }
      showTimePicker = null
    },
  )

  // 重复规则编辑器
  RecurrenceEditDialog(
    show = showRepeat,
    initial = state.recurrence,
    onDismiss = { showRepeat = false },
    onConfirm = { state.recurrence = it },
  )

  // 三态选择
  EditScopeChooserSheet(
    show = scopeChooser != null,
    isDelete = scopeChooser == ScopeAction.DELETE,
    onDismiss = { scopeChooser = null },
    onChoose = { scope ->
      when (scopeChooser) {
        ScopeAction.SAVE -> onConfirm(state, scope)
        ScopeAction.DELETE -> onDelete?.invoke(scope)
        null -> {}
      }
      scopeChooser = null
      onDismiss()
    },
  )

  // 未保存退出确认
  ScheduleConfirmDialog(
    show = showUnsavedExit,
    title = "未保存",
    message = "当前修改未保存，是否放弃？",
    confirmText = "放弃",
    dismissText = "继续编辑",
    onConfirm = onDismiss,
    onDismiss = { showUnsavedExit = false },
  )
}

/** 保存/删除两种待选作用域的用途。 */
private enum class ScopeAction { SAVE, DELETE }

/** 顶部操作栏：左「取消」，右侧（编辑态）可选「删除」+「保存」。 */
@Composable
private fun EditTopBar(
  canConfirm: Boolean,
  showDelete: Boolean,
  onCancel: () -> Unit,
  onDelete: () -> Unit,
  onSave: () -> Unit,
) {
  val colors = LocalAppColors.current
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(text = "取消", fontSize = 16.sp, color = colors.tvLv3.copy(alpha = 0.5f),
      modifier = Modifier.clickable(onClick = onCancel))
    Spacer(modifier = Modifier.weight(1f))
    if (showDelete) {
      Text(text = "删除", fontSize = 16.sp, color = androidx.compose.ui.graphics.Color(0xFFE15B64),
        modifier = Modifier.clickable(onClick = onDelete))
      Spacer(modifier = Modifier.width(20.dp))
    }
    Text(
      text = "保存", fontSize = 16.sp, fontWeight = FontWeight.Medium,
      color = if (canConfirm) colors.positive else colors.tvLv3.copy(alpha = 0.4f),
      modifier = Modifier.clickable(enabled = canConfirm, onClick = onSave),
    )
  }
}

/** 时间区块：时间段=开始—结束同一行；截止=单个截止时间行。 */
@Composable
private fun EditTimeSection(
  isInterval: Boolean,
  startTime: String,
  endTime: String,
  onPick: (TimeTarget) -> Unit,
  onClearDeadline: () -> Unit,
) {
  val colors = LocalAppColors.current
  if (isInterval) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(modifier = Modifier.width(24.dp), contentAlignment = Alignment.Center) {
        Image(imageVector = rememberIcAddtodoNotice(), contentDescription = null)
      }
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = startTime.ifEmpty { "设置开始时间" }, fontSize = 14.sp, maxLines = 1,
        color = if (startTime.isEmpty()) colors.tvLv3.copy(alpha = 0.4f) else colors.tvLv3,
        modifier = Modifier.clickable { onPick(TimeTarget.START) },
      )
      Box(modifier = Modifier.padding(horizontal = 8.dp).width(12.dp).height(1.dp)
        .background(colors.tvLv3.copy(alpha = 0.35f)))
      Text(
        text = endTime.ifEmpty { "设置结束时间" }, fontSize = 14.sp, maxLines = 1,
        color = if (endTime.isEmpty()) colors.tvLv3.copy(alpha = 0.4f) else colors.tvLv3,
        modifier = Modifier.clickable { onPick(TimeTarget.END) },
      )
    }
  } else {
    EntryRow(
      icon = rememberIcAddtodoNotice(),
      text = endTime.ifEmpty { "设置截止时间" },
      isPlaceholder = endTime.isEmpty(),
      onClick = { onPick(TimeTarget.DEADLINE) },
      trailing = { if (endTime.isNotEmpty()) DeleteText(onClick = onClearDeadline) },
    )
  }
}

/** 重复区块：入口行展示当前规则摘要 + chip 流。 */
@Composable
private fun EditRepeatSection(
  recurrence: com.cyxbs.pages.schedule.recurrence.Recurrence?,
  onClickRepeat: () -> Unit,
) {
  val labels = buildRecurrenceLabels(recurrence)
  EntryRow(
    icon = rememberIcAddtodoRepeat(),
    text = if (labels.isEmpty()) "设置重复" else "重复",
    isPlaceholder = labels.isEmpty(),
    onClick = onClickRepeat,
  )
  if (labels.isNotEmpty()) {
    Spacer(modifier = Modifier.height(8.dp))
    LazyRow(modifier = Modifier.fillMaxWidth().padding(start = 30.dp), contentPadding = PaddingValues(end = 8.dp)) {
      itemsIndexed(labels) { _, label ->
        ScheduleRepeatChip(label = label, onRemove = null)
        Spacer(modifier = Modifier.width(8.dp))
      }
    }
  }
}

/** 分组区块。 */
@Composable
private fun EditCategorySection(
  selectedType: String,
  categories: List<CategoryDisplay>,
  onSelectType: (String) -> Unit,
  onAddCategory: (String) -> Unit,
  onDeleteCategory: (String) -> Unit,
) {
  val colors = LocalAppColors.current
  var categoryExpanded by remember { mutableStateOf(false) }
  val arrowRotation by animateFloatAsState(if (categoryExpanded) 90f else 0f)

  EntryRow(
    icon = rememberIcAddtodoCategory(),
    text = "分组",
    isPlaceholder = false,
    onClick = { categoryExpanded = !categoryExpanded },
    trailing = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = categoryLabel(selectedType), fontSize = 14.sp, color = colors.positive)
        Spacer(modifier = Modifier.width(6.dp))
        Image(
          imageVector = rememberIcDetailClassifyMore(),
          contentDescription = null,
          modifier = Modifier.size(width = 7.dp, height = 13.dp).graphicsLayer { rotationZ = arrowRotation },
        )
      }
    },
  )
  AnimatedVisibility(visible = categoryExpanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Spacer(modifier = Modifier.height(8.dp))
      CategorySelector(
        selectedType = selectedType, categories = categories,
        onSelect = onSelectType, onAdd = onAddCategory, onDelete = onDeleteCategory,
      )
    }
  }
}

/** 三态选择底部弹窗：仅此次 / 此次及后续 / 全部。 */
@Composable
private fun EditScopeChooserSheet(
  show: Boolean,
  isDelete: Boolean,
  onDismiss: () -> Unit,
  onChoose: (EditScope) -> Unit,
) {
  if (!show) return
  val colors = LocalAppColors.current
  ScheduleBottomSheet(show = true, onDismiss = onDismiss) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
      Text(
        text = if (isDelete) "删除重复日程" else "保存对重复日程的修改",
        fontSize = 14.sp, color = colors.tvLv3.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth().padding(20.dp),
      )
      ScopeRow(if (isDelete) "仅删除此次" else "仅此次") { onChoose(EditScope.THIS_ONLY) }
      ScopeRow(if (isDelete) "删除此次及后续" else "此次及后续") { onChoose(EditScope.THIS_AND_FOLLOWING) }
      ScopeRow(if (isDelete) "删除整个系列" else "整个系列") { onChoose(EditScope.ALL) }
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = "取消", fontSize = 16.sp, color = colors.tvLv3.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onDismiss).padding(16.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
      )
    }
  }
}

@Composable
private fun ScopeRow(text: String, onClick: () -> Unit) {
  Text(
    text = text,
    fontSize = 16.sp,
    color = LocalAppColors.current.tvLv2,
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
  )
}

/** 时间选择目标。 */
private enum class TimeTarget { DEADLINE, START, END }

/** 通用一行：图标 + 文案 + 可选 trailing。 */
@Composable
private fun EntryRow(
  icon: ImageVector?,
  text: String,
  isPlaceholder: Boolean,
  onClick: () -> Unit,
  trailing: (@Composable () -> Unit)? = null,
) {
  val colors = LocalAppColors.current
  Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(modifier = Modifier.width(24.dp), contentAlignment = Alignment.Center) {
      if (icon != null) Image(imageVector = icon, contentDescription = null)
    }
    Spacer(modifier = Modifier.width(8.dp))
    Text(
      text = text, fontSize = 14.sp,
      color = if (isPlaceholder) colors.tvLv3.copy(alpha = 0.4f) else colors.tvLv3,
      modifier = Modifier.weight(1f),
    )
    trailing?.invoke()
  }
}

/** 「删除」文字按钮。 */
@Composable
private fun DeleteText(onClick: () -> Unit) {
  Text(
    text = "删除", fontSize = 14.sp,
    color = LocalAppColors.current.positive.copy(alpha = 0.7f),
    modifier = Modifier.clickable(onClick = onClick),
  )
}
