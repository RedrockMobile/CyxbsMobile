package com.cyxbs.pages.schedule.ui.edit

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.config.time.TodayNoEffect
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.view.calendar.CalendarCompose
import com.cyxbs.components.view.calendar.state.rememberCalendarState
import com.cyxbs.components.view.wheel.WheelSelectBackground
import com.cyxbs.components.view.wheel.WheelSelectCompose
import com.cyxbs.pages.schedule.data.model.ScheduleEntity
import com.cyxbs.pages.schedule.ui.dialog.ScheduleBottomSheet
import com.cyxbs.pages.schedule.ui.dialog.ScheduleConfirmDialog
import com.cyxbs.pages.schedule.ui.timeline.formatScheduleDateTime
import kotlinx.collections.immutable.toPersistentList
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Clock

/**
 * 添加 / 查看 / 编辑日程的统一底部弹窗 —— **邮子清单与课表共用同一套**，外观对齐课表事务(affair)。
 *
 * 形态（见与用户确认的文本草图）：标题行 + **单行信息栏**(日期·第N周·周几·时间段·重复·提醒) + 备注。
 * - 查看态(Show)：只读，右上 ✎ 编辑 / 🗑 删除。
 * - 编辑态(Edit)：标题/备注可输入；信息栏每段可点——日期→日历、时间段→下方备注区变时分滚轮、
 *   重复→[RecurrenceEditDialog]、提醒→提前分钟选择。
 * - 周数由 commonMain 的 [SchoolCalendar] 推导（学期内显示「第N周」，否则只显示日期），不依赖课表帧，
 *   所以邮子清单与课表能真正共用、长得一样。
 *
 * 三态：编辑/删除「重复系列某一次」（[editSchedule] 重复 && [occurrenceDate] != null）时先弹三选一。
 */
@Composable
fun EditScheduleDialog(
  show: Boolean,
  editSchedule: ScheduleEntity? = null,
  occurrenceDate: Date? = null,
  onDismiss: () -> Unit,
  onConfirm: (EditScheduleState, EditScope) -> Unit,
  onDelete: ((EditScope) -> Unit)? = null,
) {
  if (!show) return

  val state = rememberEditScheduleState(editSchedule)
  // 新建直接进编辑态；点已有日程先进查看态。
  var mode by remember { mutableStateOf(if (editSchedule == null) Mode.EDIT else Mode.SHOW) }

  var showDatePicker by remember { mutableStateOf(false) }
  var editingTime by remember { mutableStateOf(false) }
  var showRepeat by remember { mutableStateOf(false) }
  var showRemind by remember { mutableStateOf(false) }
  var showUnsavedExit by remember { mutableStateOf(false) }
  var scopeChooser by remember { mutableStateOf<ScopeAction?>(null) }

  // 开学第一天（周一）：用于推导第N周，一次会话读一次即可。
  val firstMonday = remember { SchoolCalendar.getFirstMonDay() }
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
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
      when (mode) {
        Mode.SHOW -> ShowContent(
          state = state,
          firstMonday = firstMonday,
          onEdit = { mode = Mode.EDIT },
          onDelete = doDelete,
        )
        Mode.EDIT -> EditContent(
          state = state,
          firstMonday = firstMonday,
          editingTime = editingTime,
          onClickDate = { showDatePicker = true },
          onClickTime = { editingTime = true },
          onClickRepeat = { showRepeat = true },
          onClickRemind = { showRemind = true },
          onDoneTime = { editingTime = false },
          onSave = doSave,
          onCancel = requestDismiss,
        )
      }
    }
  }

  // 日期选择（仅取日期，周数随之重算）
  DatePickerSheet(
    show = showDatePicker,
    initial = state.anchorDate,
    onDismiss = { showDatePicker = false },
    onConfirm = { date ->
      state.startTime = reanchorTimeString(state.startTime, date)
      state.endTime = reanchorTimeString(state.endTime, date)
      showDatePicker = false
    },
  )

  // 重复规则编辑器
  RecurrenceEditDialog(
    show = showRepeat,
    initial = state.recurrence,
    onDismiss = { showRepeat = false },
    onConfirm = { state.recurrence = it },
  )

  // 提前提醒选择
  RemindChooserSheet(
    show = showRemind,
    current = state.remindMinutes,
    onDismiss = { showRemind = false },
    onChoose = { state.remindMinutes = it; showRemind = false },
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

private enum class Mode { SHOW, EDIT }
private enum class ScopeAction { SAVE, DELETE }

/* ---------------- 查看态 ---------------- */

@Composable
private fun ShowContent(
  state: EditScheduleState,
  firstMonday: Date?,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  val colors = LocalAppColors.current
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(
      text = state.title.text.toString().ifBlank { "(无标题)" },
      fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.tvLv2, maxLines = 1,
      modifier = Modifier.weight(1f).basicMarquee(),
    )
    Icon(
      painter = rememberVectorPainter(Icons.Outlined.Edit), contentDescription = "编辑",
      tint = colors.tvLv2, modifier = Modifier.padding(start = 12.dp).clickableNoIndicator(onClick = onEdit),
    )
    Icon(
      painter = rememberVectorPainter(Icons.Outlined.Delete), contentDescription = "删除",
      tint = colors.tvLv2, modifier = Modifier.padding(start = 12.dp).clickableNoIndicator(onClick = onDelete),
    )
  }
  Spacer(modifier = Modifier.height(10.dp))
  InfoRow(state = state, firstMonday = firstMonday, editable = false)
  val detail = state.detail.text.toString()
  if (detail.isNotBlank()) {
    Spacer(modifier = Modifier.height(10.dp))
    Text(text = detail, fontSize = 15.sp, color = colors.tvLv2)
  }
}

/* ---------------- 编辑态 ---------------- */

@Composable
private fun EditContent(
  state: EditScheduleState,
  firstMonday: Date?,
  editingTime: Boolean,
  onClickDate: () -> Unit,
  onClickTime: () -> Unit,
  onClickRepeat: () -> Unit,
  onClickRemind: () -> Unit,
  onDoneTime: () -> Unit,
  onSave: () -> Unit,
  onCancel: () -> Unit,
) {
  val colors = LocalAppColors.current
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Box(modifier = Modifier.weight(1f)) {
      BasicTextField(
        state = state.title,
        lineLimits = TextFieldLineLimits.SingleLine,
        cursorBrush = SolidColor(colors.positive),
        textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.tvLv2),
        modifier = Modifier.fillMaxWidth(),
      )
      if (state.title.text.isEmpty()) {
        Text("请输入标题", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.tvLv2.copy(alpha = 0.3f))
      }
    }
    val canSave = state.canConfirm
    Icon(
      painter = rememberVectorPainter(Icons.Outlined.Check), contentDescription = "保存",
      tint = if (canSave) colors.positive else colors.tvLv3.copy(alpha = 0.4f),
      modifier = Modifier.padding(start = 12.dp).clickableNoIndicator { if (canSave) onSave() },
    )
    Icon(
      painter = rememberVectorPainter(Icons.Outlined.Close), contentDescription = "取消",
      tint = colors.tvLv2, modifier = Modifier.padding(start = 12.dp).clickableNoIndicator(onClick = onCancel),
    )
  }
  Spacer(modifier = Modifier.height(10.dp))
  InfoRow(
    state = state, firstMonday = firstMonday, editable = true,
    onClickDate = onClickDate, onClickTime = onClickTime,
    onClickRepeat = onClickRepeat, onClickRemind = onClickRemind,
  )
  Spacer(modifier = Modifier.height(10.dp))
  if (editingTime) {
    TimeWheelPane(state = state, onDone = onDoneTime)
  } else {
    Box {
      BasicTextField(
        state = state.detail,
        cursorBrush = SolidColor(colors.positive),
        textStyle = TextStyle(fontSize = 15.sp, color = colors.tvLv2),
        modifier = Modifier.fillMaxWidth(),
      )
      if (state.detail.text.isEmpty()) {
        Text("备注（可选）", fontSize = 15.sp, color = colors.tvLv2.copy(alpha = 0.3f))
      }
    }
  }
}

/* ---------------- 单行信息栏 ---------------- */

@Composable
private fun InfoRow(
  state: EditScheduleState,
  firstMonday: Date?,
  editable: Boolean,
  onClickDate: () -> Unit = {},
  onClickTime: () -> Unit = {},
  onClickRepeat: () -> Unit = {},
  onClickRemind: () -> Unit = {},
) {
  val date = state.anchorDate
  // 日期段：日期 + 第N周(学期内) + 周几，合为一个可点（→日历）。
  val dateText = buildString {
    append(formatInfoDate(date))
    formatWeekOfTerm(firstMonday, date)?.let { append(' ').append(it) }
    append(' ').append(formatWeekday(date))
  }
  val timeText = formatTimeRange(state.startMinuteOfDay, state.endMinuteOfDay)
  val repeatText = recurrenceRowLabel(state.outputRecurrence)
  val remindText = formatRemindAhead(state.remindMinutes)

  Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    InfoSegment(text = dateText, editable = editable, onClick = onClickDate)
    InfoSegment(text = timeText ?: if (editable) "设置时间" else null, placeholder = timeText == null, editable = editable, onClick = onClickTime)
    InfoSegment(text = repeatText ?: if (editable) "重复" else null, placeholder = repeatText == null, editable = editable, onClick = onClickRepeat)
    InfoSegment(text = remindText ?: if (editable) "提醒" else null, placeholder = remindText == null, editable = editable, onClick = onClickRemind)
  }
}

@Composable
private fun InfoSegment(
  text: String?,
  editable: Boolean,
  placeholder: Boolean = false,
  onClick: () -> Unit = {},
) {
  if (text == null) return
  val colors = LocalAppColors.current
  Text(
    text = text,
    fontSize = 13.sp,
    maxLines = 1,
    color = if (placeholder) colors.tvLv3.copy(alpha = 0.4f) else colors.tvLv2,
    modifier = Modifier
      .padding(end = 12.dp)
      .then(if (editable) Modifier.clickableNoIndicator(onClick = onClick) else Modifier),
  )
}

/* ---------------- 时分滚轮（点时间段后替换备注区） ---------------- */

@Composable
private fun TimeWheelPane(
  state: EditScheduleState,
  onDone: () -> Unit,
) {
  val colors = LocalAppColors.current
  val now = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }
  val startMin0 = state.startMinuteOfDay ?: (now.hour * 60 + now.minute)
  val endMin0 = state.endMinuteOfDay ?: ((startMin0 + 60).coerceAtMost(23 * 60 + 59))
  // 截止型：原本没有开始时间。提供一个开关在「时间段 / 仅截止」之间切换。
  var deadlineOnly by remember { mutableStateOf(state.outputStartTime == null && state.outputEndTime != null) }

  val startHour = remember { Animatable((startMin0 / 60).toFloat()) }
  val startMinute = remember { Animatable((startMin0 % 60).toFloat()) }
  val endHour = remember { Animatable((endMin0 / 60).toFloat()) }
  val endMinute = remember { Animatable((endMin0 % 60).toFloat()) }
  val hours = remember { (0..23).map { it.toString().padStart(2, '0') }.toPersistentList() }
  val minutes = remember { (0..59).map { it.toString().padStart(2, '0') }.toPersistentList() }

  Column(modifier = Modifier.fillMaxWidth()) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = if (deadlineOnly) "截止时间" else "开始 — 结束",
        fontSize = 13.sp, color = colors.tvLv3.copy(alpha = 0.7f), modifier = Modifier.weight(1f),
      )
      // 时间段 / 仅截止 切换
      Text(
        text = if (deadlineOnly) "改为时间段" else "设为截止型",
        fontSize = 13.sp, color = colors.positive,
        modifier = Modifier.clickableNoIndicator { deadlineOnly = !deadlineOnly },
      )
    }
    Row(modifier = Modifier.fillMaxWidth().height(120.dp), verticalAlignment = Alignment.CenterVertically) {
      if (!deadlineOnly) {
        WheelPair(hours, minutes, startHour, startMinute, modifier = Modifier.weight(1f))
        Text("—", modifier = Modifier.padding(horizontal = 8.dp), color = colors.tvLv2)
      }
      WheelPair(hours, minutes, endHour, endMinute, modifier = Modifier.weight(1f))
    }
    Text(
      text = "完成",
      fontSize = 15.sp, color = colors.positive, textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickableNoIndicator {
        val date = state.anchorDate
        val eH = endHour.value.roundToInt().coerceIn(0, 23)
        val eM = endMinute.value.roundToInt().coerceIn(0, 59)
        state.endTime = formatScheduleDateTime(date.year, date.monthNumber, date.dayOfMonth, eH, eM)
        if (deadlineOnly) {
          state.startTime = "" // 截止型：清空开始
        } else {
          val sH = startHour.value.roundToInt().coerceIn(0, 23)
          val sM = startMinute.value.roundToInt().coerceIn(0, 59)
          state.startTime = formatScheduleDateTime(date.year, date.monthNumber, date.dayOfMonth, sH, sM)
        }
        onDone()
      },
    )
  }
}

@Composable
private fun WheelPair(
  hours: kotlinx.collections.immutable.ImmutableList<String>,
  minutes: kotlinx.collections.immutable.ImmutableList<String>,
  hourLine: Animatable<Float, *>,
  minuteLine: Animatable<Float, *>,
  modifier: Modifier = Modifier,
) {
  @Suppress("UNCHECKED_CAST")
  Row(modifier = modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
    WheelSelectBackground(modifier = Modifier.weight(1f).fillMaxHeight()) {
      WheelSelectCompose(
        selectedLine = hourLine as Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
        options = hours, modifier = Modifier.fillMaxSize(),
      )
    }
    WheelSelectBackground(modifier = Modifier.weight(1f).fillMaxHeight()) {
      WheelSelectCompose(
        selectedLine = minuteLine as Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
        options = minutes, modifier = Modifier.fillMaxSize(),
      )
    }
  }
}

/* ---------------- 日期选择 ---------------- */

@Composable
private fun DatePickerSheet(
  show: Boolean,
  initial: Date,
  onDismiss: () -> Unit,
  onConfirm: (Date) -> Unit,
) {
  if (!show) return
  val colors = LocalAppColors.current
  val calendarState = rememberCalendarState(initialClickDate = initial)
  ScheduleBottomSheet(show = true, onDismiss = onDismiss) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
      Text("选择日期", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.tvLv2,
        modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp))
      CalendarCompose(modifier = Modifier.fillMaxWidth().height(280.dp), state = calendarState)
      Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
        Text("取消", fontSize = 16.sp, color = colors.tvLv3.copy(alpha = 0.5f),
          modifier = Modifier.clickableNoIndicator(onClick = onDismiss))
        Spacer(modifier = Modifier.width(24.dp))
        Text("确定", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colors.positive,
          modifier = Modifier.clickableNoIndicator { onConfirm(calendarState.clickDate) })
      }
    }
  }
}

/* ---------------- 提前提醒选择 ---------------- */

@Composable
private fun RemindChooserSheet(
  show: Boolean,
  current: Int,
  onDismiss: () -> Unit,
  onChoose: (Int) -> Unit,
) {
  if (!show) return
  val colors = LocalAppColors.current
  ScheduleBottomSheet(show = true, onDismiss = onDismiss) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
      Text("提前提醒", fontSize = 14.sp, color = colors.tvLv3.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth().padding(20.dp))
      REMIND_AHEAD_OPTIONS.forEach { opt ->
        Text(
          text = remindOptionLabel(opt),
          fontSize = 16.sp,
          color = if (opt == current) colors.positive else colors.tvLv2,
          modifier = Modifier.fillMaxWidth().clickableNoIndicator { onChoose(opt) }.padding(horizontal = 20.dp, vertical = 13.dp),
        )
      }
      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

/* ---------------- 三态选择 ---------------- */

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
        text = "取消", fontSize = 16.sp, color = colors.tvLv3.copy(alpha = 0.6f), textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clickableNoIndicator(onClick = onDismiss).padding(14.dp),
      )
    }
  }
}

@Composable
private fun ScopeRow(text: String, onClick: () -> Unit) {
  Text(
    text = text, fontSize = 16.sp, color = LocalAppColors.current.tvLv2,
    modifier = Modifier.fillMaxWidth().clickableNoIndicator(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
  )
}

/** 把中文时间串的「日期」改写为 [date]，保留「时分」；空串/无法解析原样返回。 */
private fun reanchorTimeString(time: String, date: Date): String {
  if (time.isBlank()) return time
  val parsed = com.cyxbs.pages.schedule.ui.timeline.parseScheduleDateTime(time) ?: return time
  val min = parsed.minuteOfDay ?: 0
  return formatScheduleDateTime(date.year, date.monthNumber, date.dayOfMonth, min / 60, min % 60)
}
