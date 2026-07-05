package com.cyxbs.pages.schedule.ui.edit

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.utils.compose.bringIntoViewFullBounds
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.plusDsl
import com.cyxbs.components.utils.compose.rememberDerivedStateOfStructure
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
import kotlinx.coroutines.flow.first
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
}

@Composable
fun EditScheduleDialog(
  show: Boolean,
  editSchedule: ScheduleEntity? = null,
  occurrenceDate: Date? = null,
  onDismiss: () -> Unit,
  onConfirm: (EditScheduleModelState, EditScope) -> Unit,
  onDelete: ((EditScope) -> Unit)? = null,
) {
  if (!show) return

  val modelState = rememberEditScheduleModelState(editSchedule)

  var showUnsavedExit by remember { mutableStateOf(false) }
  var scopeChooser by remember { mutableStateOf<ScopeAction?>(null) }

  // 开学第一天（周一）：用于推导第N周，一次会话读一次即可。
  val firstMonday = remember { SchoolCalendar.getFirstMonDay() }
  val needScope = editSchedule?.recurrence != null && occurrenceDate != null

  val requestDismiss = { if (modelState.isChanged) showUnsavedExit = true else onDismiss() }
  val doSave = {
    if (needScope) scopeChooser = ScopeAction.SAVE
    else {
      onConfirm(modelState, EditScope.ALL); onDismiss()
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
      if (modelState.isChanged) {
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
      ScheduleContent(
        modelState = modelState,
        firstMonday = firstMonday,
        onSave = doSave,
        onCancel = requestDismiss,
        onDelete = doDelete,
      )
    }
  }

  // 三态选择
  EditScopeChooserSheet(
    show = scopeChooser != null,
    isDelete = scopeChooser == ScopeAction.DELETE,
    onDismiss = { scopeChooser = null },
    onChoose = { scope ->
      when (scopeChooser) {
        ScopeAction.SAVE -> onConfirm(modelState, scope)
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

private enum class ScopeAction { SAVE, DELETE }

private sealed interface ScheduleUi {
  data object Show : ScheduleUi

  /**
   * 编辑态下「标题行下方区域」当前展示什么，用枚举统一流转：
   * [Note] 备注输入（默认）/ [Date] 日历选日期 / [Time] 时分滚轮 / [Repeat] 重复规则 / [Remind] 提前提醒。
   * 点信息栏对应段切到对应区，← 返回回到 [Note]，改动均实时写回 state。
   */
  sealed interface Edit : ScheduleUi {
    data object Note : ScheduleUi.Edit
    data object Date : ScheduleUi.Edit
    data object Time : ScheduleUi.Edit
    data object Repeat : ScheduleUi.Edit
    data object Remind : ScheduleUi.Edit
  }
}

@Composable
private fun ScheduleContent(
  modelState: EditScheduleModelState,
  firstMonday: Date?,
  onSave: () -> Unit,
  onCancel: () -> Unit,
  onDelete: () -> Unit,
) {
  val colors = LocalAppColors.current
  var uiState by remember { mutableStateOf<ScheduleUi>(ScheduleUi.Show) }
  Column(modifier = Modifier.bringIntoViewFullBounds()) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Box(modifier = Modifier.weight(1f)) {
        val focusRequester = remember { FocusRequester() }
        BasicTextField(
          state = modelState.title,
          enabled = uiState is ScheduleUi.Edit,
          lineLimits = TextFieldLineLimits.SingleLine,
          cursorBrush = SolidColor(colors.positive),
          textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.tvLv2),
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next,
            showKeyboardOnFocus = false,
          ),
          modifier = Modifier.fillMaxWidth()
            .focusRequester(focusRequester)
            .plusDsl {
              if (uiState is ScheduleUi.Show) {
                basicMarquee()
              }
            },
        )
        val focusManager = LocalFocusManager.current
        LaunchedEffect(Unit) {
          snapshotFlow { uiState }.first { it is ScheduleUi.Edit }
          withFrameMillis {  } // 刚进入 Edit 需要等待 Compose 重组后把 enable 设置为 true 才可以请求聚焦
          // 首次进入编辑状态时标题显示光标提示用户可以输入
          focusRequester.requestFocus()
          snapshotFlow { uiState }.collect {
            if (it !is ScheduleUi.Edit.Note) {
              // 进入其他状态移除焦点，防止光标一直闪烁
              focusManager.clearFocus()
            }
          }
        }
        val isTitleEmpty by rememberDerivedStateOfStructure { modelState.title.text.isEmpty() }
        if (isTitleEmpty) {
          Text(
            text = if (uiState is ScheduleUi.Edit) "请输入标题" else "(无标题)",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colors.tvLv2.copy(alpha = 0.3f)
          )
        }
      }
      TitleRightIcons(
        modelState = modelState,
        uiState = uiState,
        onEdit = { uiState = ScheduleUi.Edit.Note },
        onSave = onSave,
        onCancel = onCancel,
        onDelete = onDelete,
      )
    }
    Spacer(modifier = Modifier.height(10.dp))
    InfoRow(
      modelState = modelState, firstMonday = firstMonday, editable = uiState is ScheduleUi.Edit,
      onClickDate = { uiState = ScheduleUi.Edit.Date },
      onClickTime = { uiState = ScheduleUi.Edit.Time },
      onClickRepeat = { uiState = ScheduleUi.Edit.Repeat },
      onClickRemind = { uiState = ScheduleUi.Edit.Remind },
    )
    Spacer(modifier = Modifier.height(10.dp))
  }
  when (uiState) {
    // 备注输入
    ScheduleUi.Show, ScheduleUi.Edit.Note -> Box {
      BasicTextField(
        state = modelState.detail,
        enabled = uiState is ScheduleUi.Edit,
        cursorBrush = SolidColor(colors.positive),
        textStyle = TextStyle(fontSize = 15.sp, color = colors.tvLv2),
        modifier = Modifier.fillMaxWidth(),
      )
      val isShowHint by rememberDerivedStateOfStructure {
        uiState is ScheduleUi.Edit && modelState.detail.text.isEmpty()
      }
      if (isShowHint) {
        Text("备注（可选）", fontSize = 15.sp, color = colors.tvLv2.copy(alpha = 0.3f))
      }
    }
    // 日期：下方就地变日历，点某天实时改写开始/结束的日期（周数随之重算）；← 返回
    ScheduleUi.Edit.Date -> EditScheduleCalendarArea(state = modelState)
    // 时间段：下方变时分滚轮
    ScheduleUi.Edit.Time -> EditScheduleTimeArea(state = modelState)
    // 重复：内容较多，外层弹窗会增高；结束条件固定在底部，主体选择区内部滚动。
    ScheduleUi.Edit.Repeat -> EditScheduleRecurrenceArea(
      draft = modelState.recurrence,
      onChange = { modelState.recurrence = it },
      modifier = Modifier.fillMaxWidth(),
    )
    // 提醒：下方就地变提前分钟选项，实时写回 state.remindMinutes
    ScheduleUi.Edit.Remind -> EditScheduleRemindArea(
      current = modelState.remindMinutes,
      onChoose = { modelState.remindMinutes = it },
      modifier = Modifier,
    )
  }
  Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun TitleRightIcons(
  modelState: EditScheduleModelState,
  uiState: ScheduleUi,
  onEdit: () -> Unit,
  onSave: () -> Unit,
  onCancel: () -> Unit,
  onDelete: () -> Unit,
) {
  val colors = LocalAppColors.current
  when (uiState) {
    ScheduleUi.Show -> {
      Icon(
        painter = rememberVectorPainter(Icons.Outlined.Edit),
        contentDescription = "编辑",
        tint = colors.tvLv2,
        modifier = Modifier.padding(start = 12.dp).clickableNoIndicator {
          onEdit.invoke()
        },
      )
      Icon(
        painter = rememberVectorPainter(Icons.Outlined.Delete),
        contentDescription = "删除",
        tint = colors.tvLv2,
        modifier = Modifier.padding(start = 12.dp).clickableNoIndicator(onClick = onDelete),
      )
    }

    is ScheduleUi.Edit -> {
      if (uiState != ScheduleUi.Edit.Note) {
        // 正在编辑时间段/日期：右上是「返回」← 收起子编辑区回到表单（改动已实时写回，不会丢）。
        Icon(
          painter = rememberVectorPainter(Icons.AutoMirrored.Rounded.ArrowBack),
          contentDescription = "返回",
          tint = colors.tvLv2,
          modifier = Modifier.padding(start = 12.dp).clickableNoIndicator {
            onEdit.invoke()
          },
        )
      } else {
        val canSave = modelState.canConfirm
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
  }
}

/* ---------------- 单行信息栏 ---------------- */

@Composable
private fun InfoRow(
  modelState: EditScheduleModelState,
  firstMonday: Date?,
  editable: Boolean,
  onClickDate: () -> Unit = {},
  onClickTime: () -> Unit = {},
  onClickRepeat: () -> Unit = {},
  onClickRemind: () -> Unit = {},
) {
  val date = modelState.anchorDate
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
    modelState.startMinuteOfDay,
    modelState.endMinuteOfDay,
    placeholderColor,
    timeIcon,
    onClickTime
  ) {
    val text = formatTimeRange(modelState.startMinuteOfDay, modelState.endMinuteOfDay)
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
      modelState.outputRecurrence,
      repeatIcon,
      onClickRepeat
    ) {
      val text = recurrenceRowLabel(modelState.outputRecurrence)
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
    remember(colors, editable, modelState.remindMinutes, remindIcon, onClickRemind) {
      val text = formatRemindAhead(modelState.remindMinutes)
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
internal fun ToggleChip(
  text: String,
  selected: Boolean,
  fontSize: TextUnit = 13.sp,
  onClick: () -> Unit
) {
  val colors = LocalAppColors.current
  val accent = colors.positive
  Text(
    text = text,
    fontSize = fontSize,
    textAlign = TextAlign.Center,
    color = if (selected) accent else colors.tvLv3.copy(alpha = 0.6f),
    modifier = Modifier
      .border(
        1.dp,
        if (selected) accent else colors.tvLv3.copy(alpha = 0.15f),
        RoundedCornerShape(6.dp)
      )
      .background(
        if (selected) accent.copy(alpha = 0.1f) else Color.Transparent,
        RoundedCornerShape(6.dp)
      )
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

