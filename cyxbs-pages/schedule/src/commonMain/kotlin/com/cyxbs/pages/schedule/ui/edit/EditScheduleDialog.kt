package com.cyxbs.pages.schedule.ui.edit

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.AppTheme
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.pages.schedule.data.model.ScheduleEntity
import com.cyxbs.pages.schedule.ui.dialog.ScheduleBottomSheet
import com.cyxbs.pages.schedule.ui.dialog.ScheduleConfirmDialog
import com.cyxbs.pages.schedule.ui.edit.area.EditScheduleCalendarArea
import com.cyxbs.pages.schedule.ui.edit.area.EditScheduleRecurrenceArea
import com.cyxbs.pages.schedule.ui.edit.area.EditScheduleRemindArea
import com.cyxbs.pages.schedule.ui.edit.area.EditScheduleTimeArea
import com.cyxbs.pages.schedule.widget.rememberIcAddtodoCalendar
import com.cyxbs.pages.schedule.widget.rememberIcAddtodoNotice
import com.cyxbs.pages.schedule.widget.rememberIcAddtodoRepeat
import com.cyxbs.pages.schedule.widget.rememberIcAddtodoTime
import kotlinx.serialization.Serializable

/**
 * 添加 / 查看 / 编辑日程的统一底部弹窗 —— **邮子清单与课表共用同一套**，外观对齐课表事务(affair)。
 *
 * 形态（见与用户确认的文本草图）：标题行 + **单行信息栏**(日期·第N周·周几·时间段·重复·提醒) + 备注。
 * - 查看态(Show)：只读，右上 ✎ 编辑 / 🗑 删除。
 * - 编辑态(Edit)：标题/备注可输入；信息栏每段可点——下方区域就地切换：日期→日历、时间段→时分滚轮、
 *   重复→[com.cyxbs.pages.schedule.ui.edit.area.EditScheduleRecurrenceArea]、提醒→提前分钟选择（均实时写回，← 返回）。
 * - 周数由 commonMain 的 [SchoolCalendar] 推导（学期内显示「第N周」，否则只显示日期），不依赖课表帧，
 *   所以邮子清单与课表能真正共用、长得一样。
 *
 * 三态：编辑/删除「重复系列某一次」（[editSchedule] 重复 && [occurrenceDate] != null）时先弹三选一。
 */

/** 弹窗内容固定高度，对齐课表 item 弹窗（[com.cyxbs.pages.course.dialog] 的 280dp），两处观感一致。 */
private val EditSheetHeight = 280.dp
private val RepeatEditPreviewHeight = 410.dp

@Serializable
object EditScheduleDialogNavArgument : AppNavArgument

@AppNav(route = "schedule/edit")
class EditScheduleDialogPreview : AppNavEntry<EditScheduleDialogNavArgument>() {
  override fun isNeedLogin(argument: EditScheduleDialogNavArgument): Boolean {
    return false
  }

  @Composable
  override fun Content(argument: EditScheduleDialogNavArgument) {
    EditScheduleDialog(
      show = true,
      editSchedule = previewSampleSchedule(),
      occurrenceDate = Date.now(),
      onDismiss = {},
      onConfirm = { _, _ -> }
    )
  }
}

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

  // 标题行下方区域的编辑子模式，用枚举流转（备注 / 日历 / 时分滚轮 / 重复 / 提醒）。
  var subArea by remember { mutableStateOf(EditSubArea.NOTE) }
  var showUnsavedExit by remember { mutableStateOf(false) }
  var scopeChooser by remember { mutableStateOf<ScopeAction?>(null) }

  // 开学第一天（周一）：用于推导第N周，一次会话读一次即可。
  val firstMonday = remember { SchoolCalendar.getFirstMonDay() }
  val needScope = editSchedule?.recurrence != null && occurrenceDate != null

  val requestDismiss = { if (state.isChanged) showUnsavedExit = true else onDismiss() }
  val doSave = {
    if (needScope) scopeChooser = ScopeAction.SAVE
    else {
      onConfirm(state, EditScope.ALL); onDismiss()
    }
  }
  val doDelete = {
    if (onDelete != null) {
      if (needScope) scopeChooser = ScopeAction.DELETE
      else {
        onDelete(EditScope.ALL); onDismiss()
      }
    }
  }

  ScheduleBottomSheet(
    show = true,
    onDismiss = onDismiss,
    onDismissRequest = {
      if (state.isChanged) {
        showUnsavedExit = true; false
      } else true
    },
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = EditSheetHeight)
        .animateContentSize()
        .padding(top = 16.dp, start = 16.dp, end = 16.dp),
    ) {
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
          subArea = subArea,
          onClickDate = { subArea = EditSubArea.DATE },
          onClickTime = { subArea = EditSubArea.TIME },
          onClickRepeat = { subArea = EditSubArea.REPEAT },
          onClickRemind = { subArea = EditSubArea.REMIND },
          onBackSub = { subArea = EditSubArea.NOTE },
          onSave = doSave,
          onCancel = requestDismiss,
        )
      }
    }
  }

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

/**
 * 编辑态下「标题行下方区域」当前展示什么，用枚举统一流转：
 * [NOTE] 备注输入（默认）/ [DATE] 日历选日期 / [TIME] 时分滚轮 / [REPEAT] 重复规则 / [REMIND] 提前提醒。
 * 点信息栏对应段切到对应区，← 返回回到 [NOTE]，改动均实时写回 state。
 */
private enum class EditSubArea { NOTE, DATE, TIME, REPEAT, REMIND }

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
      painter = rememberVectorPainter(Icons.Outlined.Edit),
      contentDescription = "编辑",
      tint = colors.tvLv2,
      modifier = Modifier.padding(start = 12.dp).clickableNoIndicator(onClick = onEdit),
    )
    Icon(
      painter = rememberVectorPainter(Icons.Outlined.Delete),
      contentDescription = "删除",
      tint = colors.tvLv2,
      modifier = Modifier.padding(start = 12.dp).clickableNoIndicator(onClick = onDelete),
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
private fun ColumnScope.EditContent(
  state: EditScheduleState,
  firstMonday: Date?,
  subArea: EditSubArea,
  onClickDate: () -> Unit,
  onClickTime: () -> Unit,
  onClickRepeat: () -> Unit,
  onClickRemind: () -> Unit,
  onBackSub: () -> Unit,
  onSave: () -> Unit,
  onCancel: () -> Unit,
) {
  val colors = LocalAppColors.current
  val inSubEdit = subArea != EditSubArea.NOTE
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
        Text(
          "请输入标题",
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold,
          color = colors.tvLv2.copy(alpha = 0.3f)
        )
      }
    }
    if (inSubEdit) {
      // 正在编辑时间段/日期：右上是「返回」← 收起子编辑区回到表单（改动已实时写回，不会丢）。
      Icon(
        painter = rememberVectorPainter(Icons.AutoMirrored.Rounded.ArrowBack),
        contentDescription = "返回",
        tint = colors.tvLv2,
        modifier = Modifier.padding(start = 12.dp).clickableNoIndicator(onClick = onBackSub),
      )
    } else {
      val canSave = state.canConfirm
      Icon(
        painter = rememberVectorPainter(Icons.Rounded.Check), contentDescription = "保存",
        tint = if (canSave) colors.tvLv2 else colors.tvLv2.copy(alpha = 0.4f),
        modifier = Modifier.padding(start = 12.dp).clickableNoIndicator { if (canSave) onSave() },
      )
    }
    // 取消 ✕ 始终显示（编辑子区时也能取消整个编辑）。
    Icon(
      painter = rememberVectorPainter(Icons.Rounded.Close),
      contentDescription = "取消",
      tint = colors.tvLv2,
      modifier = Modifier.padding(start = 12.dp).clickableNoIndicator(onClick = onCancel),
    )
  }
  Spacer(modifier = Modifier.height(10.dp))
  InfoRow(
    state = state, firstMonday = firstMonday, editable = true,
    onClickDate = onClickDate, onClickTime = onClickTime,
    onClickRepeat = onClickRepeat, onClickRemind = onClickRemind,
  )
  Spacer(modifier = Modifier.height(10.dp))
  when (subArea) {
    // 时间段：下方变时分滚轮
    EditSubArea.TIME -> EditScheduleTimeArea(state = state)
    // 日期：下方就地变日历，点某天实时改写开始/结束的日期（周数随之重算）；← 返回
    EditSubArea.DATE -> EditScheduleCalendarArea(state = state)
    // 重复：内容较多，外层弹窗会增高；结束条件固定在底部，主体选择区内部滚动。
    EditSubArea.REPEAT -> EditScheduleRecurrenceArea(
      draft = state.recurrence,
      onChange = { state.recurrence = it },
      modifier = Modifier.fillMaxWidth(),
    )
    // 提醒：下方就地变提前分钟选项，实时写回 state.remindMinutes
    EditSubArea.REMIND -> EditScheduleRemindArea(
      current = state.remindMinutes,
      onChoose = { state.remindMinutes = it },
      modifier = Modifier,
    )
    // 默认：备注输入
    EditSubArea.NOTE -> Box {
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
  Spacer(modifier = Modifier.height(16.dp))
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
  val colors = LocalAppColors.current
  val placeholderColor = colors.tvLv2.copy(alpha = 0.4f)
  // 📅日期 第几周 周几
  val dateIcon = rememberIcAddtodoCalendar()
  val dateSegment = remember(
    colors,
    editable,
    firstMonday,
    date,
    dateIcon,
    onClickDate
  ) {
    InfoTextSegment(
      id = "date",
      text = buildString {
        append(formatInfoDate(date))
        formatWeekOfTerm(firstMonday, date)?.let { append(' ').append(it) }
        append(' ').append(formatWeekday(date))
      },
      icon = dateIcon,
      color = colors.tvLv2,
      onClick = if (editable) onClickDate else null,
    )
  }
  // 🕙时间段
  val timeIcon = rememberIcAddtodoTime()
  val timeSegment = remember(
    colors,
    editable,
    state.startMinuteOfDay,
    state.endMinuteOfDay,
    placeholderColor,
    timeIcon,
    onClickTime
  ) {
    val text = formatTimeRange(state.startMinuteOfDay, state.endMinuteOfDay)
    InfoTextSegment(
      id = "time",
      text = text ?: if (editable) "设置时间" else null,
      icon = timeIcon,
      color = if (text == null) placeholderColor else colors.tvLv2,
      onClick = if (editable) onClickTime else null,
    )
  }
  // 🔁重复规则
  val repeatIcon = rememberIcAddtodoRepeat()
  val repeatSegment =
    remember(
      colors,
      editable,
      state.outputRecurrence,
      repeatIcon,
      onClickRepeat
    ) {
      val text = recurrenceRowLabel(state.outputRecurrence)
      InfoTextSegment(
        id = "repeat",
        text = text ?: "仅一次",
        icon = repeatIcon,
        color = colors.tvLv2,
        onClick = if (editable) onClickRepeat else null,
      )
    }
  // ⏰提醒时间
  val remindIcon = rememberIcAddtodoNotice()
  val remindSegment =
    remember(colors, editable, state.remindMinutes, remindIcon, onClickRemind) {
      val text = formatRemindAhead(state.remindMinutes)
      InfoTextSegment(
        id = "remind",
        text = text ?: "不提醒",
        icon = remindIcon,
        color = colors.tvLv2,
        onClick = if (editable) onClickRemind else null,
      )
    }

  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    // 日期和时间仍使用单个富文本，统一字体度量，避免桌面端纯数字/英文文本高度不一致。
    BasicText(
      text = dateSegment.annotatedText + AnnotatedString("  ") + timeSegment.annotatedText,
      style = TextStyle(fontSize = 13.sp, lineHeight = 13.sp, color = colors.tvLv2),
      inlineContent = dateSegment.inlineContent + timeSegment.inlineContent,
    )
    listOf(repeatSegment, remindSegment).forEach {
      BasicText(
        text = it.annotatedText,
        style = TextStyle(fontSize = 13.sp, lineHeight = 13.sp, color = colors.tvLv2),
        inlineContent = it.inlineContent,
      )
    }
  }
}

private data class InfoTextSegment(
  val id: String,
  val text: String?,
  val icon: ImageVector,
  val color: Color,
  val onClick: (() -> Unit)?,
) {
  // icon
  val inlineContent = mapOf(
    id to InlineTextContent(
      placeholder = Placeholder(
        width = 13.sp,
        height = 13.sp,
        placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
      ),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(13.dp)
      )
    }
  )

  // 富文本
  val annotatedText = buildAnnotatedString {
    val start = length
    appendInlineContent(id)
    append(" ")
    append(text.orEmpty())
    addStyle(SpanStyle(color = color), start, length)
    onClick?.let { onClick ->
      addLink(
        LinkAnnotation.Clickable(
          tag = id,
          styles = TextLinkStyles(style = SpanStyle(color = color)),
        ) {
          onClick()
        },
        start,
        length,
      )
    }
  }
}


/** 多选/分段胶囊。 */
@Composable
internal fun ToggleChip(text: String, selected: Boolean, fontSize: TextUnit = 13.sp, onClick: () -> Unit) {
  val colors = LocalAppColors.current
  val accent = colors.positive
  Text(
    text = text,
    fontSize = fontSize,
    textAlign = TextAlign.Center,
    color = if (selected) accent else colors.tvLv3.copy(alpha = 0.6f),
    modifier = Modifier
      .border(1.dp, if (selected) accent else colors.tvLv3.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
      .background(if (selected) accent.copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(6.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 5.dp),
  )
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
        text = "取消",
        fontSize = 16.sp,
        color = colors.tvLv3.copy(alpha = 0.6f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clickableNoIndicator(onClick = onDismiss).padding(14.dp),
      )
    }
  }
}

@Composable
private fun ScopeRow(text: String, onClick: () -> Unit) {
  Text(
    text = text, fontSize = 16.sp, color = LocalAppColors.current.tvLv2,
    modifier = Modifier.fillMaxWidth().clickableNoIndicator(onClick = onClick)
      .padding(horizontal = 20.dp, vertical = 14.dp),
  )
}

/* ================= @Preview（Android Studio 里直接预览，无需跑 app） ================= */
//
// 预览的是「内容部分」(ShowContent/EditContent)，不含 ScheduleBottomSheet 外壳——底部弹窗靠
// LaunchedEffect+动画展开，静态预览里高度为 0 看不到，所以这里直接渲染内容、用 AppTheme 提供配色。
// 周数显式传入示例开学日(2026-03-02 周一)，避免预览读 SchoolCalendar 设置。

private fun previewSampleSchedule() = ScheduleEntity(
  todoId = 1L,
  title = "项目答辩",
  detail = "综合楼 503，记得带 U 盘",
  startTime = "2026年7月4日 10:00",
  endTime = "2026年7月4日 11:30",
  recurrence = com.cyxbs.pages.schedule.recurrence.Recurrence(
    rrule = com.cyxbs.pages.schedule.recurrence.RRule(
      freq = com.cyxbs.pages.schedule.recurrence.Freq.WEEKLY,
      byDay = listOf(7),
    ),
  ),
  remindMinutes = 10,
  lastModifyTime = 0L,
)

/** 示例开学第一天（周一），用于预览第N周。 */
private val previewFirstMonday = Date(2026, 3, 2)

@Composable
private fun PreviewFrame(
  height: androidx.compose.ui.unit.Dp = EditSheetHeight,
  content: @Composable ColumnScope.() -> Unit,
) {
  AppTheme {
    // 用 Column 提供纵向排布作用域（ShowContent/EditContent 内部直接发多个同级子项，
    // 真实弹窗里靠外层 Column 堆叠；预览必须同样包一层 Column，否则会全叠在一起）。
    // 固定高度 EditSheetHeight，和真实弹窗、课表 item 弹窗一致。
    Column(
      modifier = Modifier
        .width(360.dp)
        .height(height)
        .background(LocalAppColors.current.topBg)
        .padding(top = 16.dp, start = 16.dp, end = 16.dp),
    ) { content() }
  }
}

/** 查看态：标题 + ✎/🗑 + 单行信息栏(日期·第N周·周几·时间段·重复·提醒) + 备注。 */
@Preview
@Composable
private fun PreviewScheduleShow() {
  PreviewFrame {
    val state = remember { EditScheduleState(previewSampleSchedule()) }
    ShowContent(state = state, firstMonday = previewFirstMonday, onEdit = {}, onDelete = {})
  }
}

/** 编辑态（默认）：标题输入 + ✓/✕ + 可点信息栏 + 备注输入。 */
@Preview
@Composable
private fun PreviewScheduleEdit() {
  PreviewFrame {
    val state = remember { EditScheduleState(previewSampleSchedule()) }
    EditContent(
      state = state, firstMonday = previewFirstMonday, subArea = EditSubArea.NOTE,
      onClickDate = {}, onClickTime = {}, onClickRepeat = {}, onClickRemind = {},
      onBackSub = {}, onSave = {}, onCancel = {},
    )
  }
}

/** 编辑态·点时间段后：下方备注区变时分滚轮。 */
@Preview
@Composable
private fun PreviewScheduleEditTime() {
  PreviewFrame {
    val state = remember { EditScheduleState(previewSampleSchedule()) }
    EditContent(
      state = state, firstMonday = previewFirstMonday, subArea = EditSubArea.TIME,
      onClickDate = {}, onClickTime = {}, onClickRepeat = {}, onClickRemind = {},
      onBackSub = {}, onSave = {}, onCancel = {},
    )
  }
}

/** 截止型样例：无开始时间、只有截止时间，进编辑时间态时 deadlineOnly 默认为 true。 */
private fun previewSampleDeadline() = ScheduleEntity(
  todoId = 2L,
  title = "交实验报告",
  detail = "提交到学习通",
  startTime = null,
  endTime = "2026年6月28日 23:00",
  remindMinutes = 30,
  lastModifyTime = 0L,
)

/** 编辑态·点时间段后·截止型：header「截止时间 / 改为时间段」+ 只有一个结束时分滚轮。 */
@Preview
@Composable
private fun PreviewScheduleEditDeadline() {
  PreviewFrame {
    val state = remember { EditScheduleState(previewSampleDeadline()) }
    EditContent(
      state = state, firstMonday = previewFirstMonday, subArea = EditSubArea.TIME,
      onClickDate = {}, onClickTime = {}, onClickRepeat = {}, onClickRemind = {},
      onBackSub = {}, onSave = {}, onCancel = {},
    )
  }
}

/** 编辑态·点日期后：下方备注区就地变日历（点某天实时应用），← 返回。 */
@Preview
@Composable
private fun PreviewScheduleEditDate() {
  PreviewFrame {
    val state = remember { EditScheduleState(previewSampleSchedule()) }
    EditContent(
      state = state, firstMonday = previewFirstMonday, subArea = EditSubArea.DATE,
      onClickDate = {}, onClickTime = {}, onClickRepeat = {}, onClickRemind = {},
      onBackSub = {}, onSave = {}, onCancel = {},
    )
  }
}

/** 编辑态·点重复后：下方就地变 RFC5545 重复规则编辑器。 */
@Preview
@Composable
private fun PreviewScheduleEditRepeat() {
  PreviewFrame(height = RepeatEditPreviewHeight) {
    val state = remember { EditScheduleState(previewSampleSchedule()) }
    EditContent(
      state = state, firstMonday = previewFirstMonday, subArea = EditSubArea.REPEAT,
      onClickDate = {}, onClickTime = {}, onClickRepeat = {}, onClickRemind = {},
      onBackSub = {}, onSave = {}, onCancel = {},
    )
  }
}

private fun previewSampleMonthlySchedule() = previewSampleSchedule().copy(
  recurrence = com.cyxbs.pages.schedule.recurrence.Recurrence(
    rrule = com.cyxbs.pages.schedule.recurrence.RRule(
      freq = com.cyxbs.pages.schedule.recurrence.Freq.MONTHLY,
      byMonthDay = listOf(1, 15, 28, -1),
    ),
  ),
)

/** 编辑态·重复·每月：月内日期较多，弹窗增高且「结束」固定在底部。 */
@Preview
@Composable
private fun PreviewScheduleEditRepeatMonthly() {
  PreviewFrame(height = RepeatEditPreviewHeight) {
    val state = remember { EditScheduleState(previewSampleMonthlySchedule()) }
    EditContent(
      state = state, firstMonday = previewFirstMonday, subArea = EditSubArea.REPEAT,
      onClickDate = {}, onClickTime = {}, onClickRepeat = {}, onClickRemind = {},
      onBackSub = {}, onSave = {}, onCancel = {},
    )
  }
}

/** 编辑态·点提醒后：下方就地变提前分钟选项。 */
@Preview
@Composable
private fun PreviewScheduleEditRemind() {
  PreviewFrame {
    val state = remember { EditScheduleState(previewSampleSchedule()) }
    EditContent(
      state = state, firstMonday = previewFirstMonday, subArea = EditSubArea.REMIND,
      onClickDate = {}, onClickTime = {}, onClickRepeat = {}, onClickRemind = {},
      onBackSub = {}, onSave = {}, onCancel = {},
    )
  }
}

/** 新建态（空表单）：占位文案。 */
@Preview
@Composable
private fun PreviewScheduleNew() {
  PreviewFrame {
    val state = remember { EditScheduleState(null) }
    EditContent(
      state = state, firstMonday = previewFirstMonday, subArea = EditSubArea.NOTE,
      onClickDate = {}, onClickTime = {}, onClickRepeat = {}, onClickRemind = {},
      onBackSub = {}, onSave = {}, onCancel = {},
    )
  }
}

