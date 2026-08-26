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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.layout.Layout
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.imePaddingTarget
import com.cyxbs.components.utils.compose.plusDsl
import com.cyxbs.components.utils.compose.rememberDerivedStateOfStructure
import com.cyxbs.pages.schedule.domain.model.CategoryId
import com.cyxbs.pages.schedule.domain.model.IsoWeekDay
import com.cyxbs.pages.schedule.domain.model.RecurrenceFrequency
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.RecurrenceRule
import com.cyxbs.pages.schedule.domain.model.ReminderChannel
import com.cyxbs.pages.schedule.domain.model.ReminderId
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleCategory
import com.cyxbs.pages.schedule.domain.model.ScheduleTodoState
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.model.ScheduleKind
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrence
import com.cyxbs.pages.schedule.domain.model.ScheduleReminder
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.ui.dialog.ScheduleBottomSheet
import com.cyxbs.pages.schedule.ui.dialog.ScheduleConfirmDialog
import com.cyxbs.pages.schedule.ui.edit.area.EditScheduleCalendarArea
import com.cyxbs.pages.schedule.ui.edit.area.EditScheduleRecurrenceArea
import com.cyxbs.pages.schedule.ui.edit.area.EditScheduleRemindArea
import com.cyxbs.pages.schedule.ui.edit.area.EditScheduleTimeArea
import com.cyxbs.pages.schedule.widget.rememberIcAddtodoCalendar
import com.cyxbs.pages.schedule.widget.rememberIcAddtodoCategory
import com.cyxbs.pages.schedule.widget.rememberIcAddtodoNotice
import com.cyxbs.pages.schedule.widget.rememberIcAddtodoRepeat
import com.cyxbs.pages.schedule.widget.rememberIcAddtodoTime
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.time.Instant

/**
 * 添加 / 查看 / 编辑日程的统一底部弹窗 —— **邮子清单与课表共用同一套**，外观对齐课表事务(affair)。
 *
 * 形态（见与用户确认的文本草图）：标题行 + **紧凑信息栏**(日期·第N周·周几·时间段·重复·提醒·分类) + 备注。
 * - 查看态(Show)：只读，右上 ✎ 编辑 / 🗑 删除。
 * - 编辑态(Edit)：标题/备注可输入；信息栏每段可点——下方区域就地切换：日期→日历、时间段→时分滚轮、
 *   重复→[com.cyxbs.pages.schedule.ui.edit.area.EditScheduleRecurrenceArea]、提醒→提前分钟选择、分类→分类选择
 *   （均实时写回，← 返回）。
 * - 周数由 commonMain 的 [SchoolCalendar] 推导（学期内显示「第N周」，否则只显示日期），不依赖课表帧，
 *   所以邮子清单与课表能真正共用、长得一样。
 *
 * 范围状态机：编辑/删除重复系列实例时先选择“仅本次 / 本次及以后 / 全部”；非重复项直接使用全部范围。
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
      recurrenceId = RecurrenceId(MinuteTimeDate(2026, 7, 4, 10, 0), "Asia/Shanghai", false),
      onDismiss = {},
      onConfirm = { _, _ -> }
    )
  }

  private fun previewSampleSchedule() = Schedule(
    id = ScheduleId("00000000-0000-7000-8000-000000000001"), revision = 0, title = "项目答辩",
    description = "综合楼 503，记得带 U 盘", categoryId = null,
    timing = ScheduleTiming.Timed(MinuteTimeDate(2026, 7, 4, 10, 0), 90, "Asia/Shanghai"),
    recurrence = RecurrenceRule(RecurrenceFrequency.WEEKLY, byWeekDays = setOf(IsoWeekDay.SATURDAY)),
    reminders = listOf(ScheduleReminder(ReminderId("preview-reminder"), 10, ReminderChannel.DEVICE)),
    todoState = ScheduleTodoState.PENDING, createdAt = Instant.DISTANT_PAST, updatedAt = Instant.DISTANT_PAST,
  )
}

@Composable
fun EditScheduleDialog(
  show: Boolean,
  editSchedule: Schedule? = null,
  editOccurrence: ScheduleOccurrence? = null,
  /** 新建入口的不可变来源；编辑既有日程时该参数不生效。 */
  creationKind: ScheduleKind = ScheduleKind.TODO,
  /** 新建入口预填的时间；用于课表长按草稿，不会在打开弹窗时提前写仓库。 */
  creationTiming: ScheduleTiming? = null,
  recurrenceId: RecurrenceId? = null,
  categories: List<ScheduleCategory> = emptyList(),
  /** 弹窗外背景色；外部宿主可传透明，普通入口继续使用 Schedule 默认遮罩。 */
  scrimColor: Color? = null,
  /** true 时只绘制业务内容，由调用方提供外层 BottomSheet。 */
  embeddedInExternalHost: Boolean = false,
  onDismiss: () -> Unit,
  onConfirm: (EditScheduleModelState, EditScope) -> Unit,
  onDelete: ((EditScope) -> Unit)? = null,
  /** 嵌入外部宿主时报告查看/编辑模式；普通 ScheduleBottomSheet 可忽略。 */
  onEditModeChanged: (Boolean) -> Unit = {},
) {
  if (!show) return

  val modelState = rememberEditScheduleModelState(
    editSchedule,
    editOccurrence,
    creationKind,
    creationTiming,
  )

  var showUnsavedExit by remember { mutableStateOf(false) }
  var scopeChooser by remember { mutableStateOf<ScopeAction?>(null) }

  // 开学第一天（周一）：用于推导第N周，一次会话读一次即可。
  val firstMonday = remember { SchoolCalendar.getFirstMonDay() }
  val needScope = editSchedule?.recurrence != null && recurrenceId != null

  val requestDismiss = { if (modelState.isChanged) showUnsavedExit = true else onDismiss() }
  val doSave = {
    if (needScope) scopeChooser = ScopeAction.SAVE
    else {
      onConfirm(modelState, EditScope.ALL)
      onDismiss()
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

  val sheetContent: @Composable () -> Unit = {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .then(
          if (embeddedInExternalHost) Modifier
          else Modifier.heightIn(min = EditSheetHeight)
        )
        .animateContentSize()
        .padding(top = 16.dp, start = 16.dp, end = 16.dp),
    ) {
      ScheduleContent(
        modelState = modelState,
        firstMonday = firstMonday,
        categories = categories,
        onSave = doSave,
        onCancel = requestDismiss,
        onDelete = doDelete,
        onEditModeChanged = onEditModeChanged,
      )
    }
  }
  if (embeddedInExternalHost) {
    sheetContent()
  } else {
    ScheduleBottomSheet(
      show = true,
      onDismiss = onDismiss,
      scrimColor = scrimColor,
      onDismissRequest = {
        if (modelState.isChanged) {
          showUnsavedExit = true; false
        } else true
      },
      content = sheetContent,
    )
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

/** 范围选择弹层当前要提交的动作；保存与删除共用范围状态机，但最终命令入口严格分离。 */
private enum class ScopeAction { SAVE, DELETE }

/** 统一弹窗的查看态与编辑子区域状态；仅控制展示流转，不承载仓库写入状态。 */
private sealed interface ScheduleUi {
  data object Show : ScheduleUi

  /**
   * 编辑弹窗内部区域的状态机，统一描述标题下方当前展示的子编辑器。
   * [Note] 为默认备注区，[Date]/[Time]/[Repeat]/[Remind]/[Category] 分别承载日期、时间、重复、提醒与分类编辑；
   * 点击信息栏只切换区域，所有改动立即写回同一个 [EditScheduleModelState]，返回 [Note] 不回滚。
   * 作用范围选择是提交/删除前的独立状态，不与本区域状态混用，避免子编辑返回被误判为关闭弹窗。
   */
  sealed interface Edit : ScheduleUi {
    data object Note : ScheduleUi.Edit
    data object Date : ScheduleUi.Edit
    data object Time : ScheduleUi.Edit
    data object Repeat : ScheduleUi.Edit
    data object Remind : ScheduleUi.Edit
    data object Category : ScheduleUi.Edit
  }
}

@Composable
private fun ScheduleContent(
  modelState: EditScheduleModelState,
  firstMonday: Date?,
  categories: List<ScheduleCategory>,
  onSave: () -> Unit,
  onCancel: () -> Unit,
  onDelete: () -> Unit,
  onEditModeChanged: (Boolean) -> Unit,
) {
  val colors = LocalAppColors.current
  var uiState by remember(modelState.origin) {
    // 新建没有可供“查看”的既有资源，直接进入表单；已有日程仍先展示只读详情。
    mutableStateOf<ScheduleUi>(
      if (modelState.origin == null) ScheduleUi.Edit.Note else ScheduleUi.Show,
    )
  }
  LaunchedEffect(uiState is ScheduleUi.Edit) {
    onEditModeChanged(uiState is ScheduleUi.Edit)
  }
  DisposableEffect(Unit) {
    onDispose { onEditModeChanged(false) }
  }
  // 标题或备注获得焦点时，标题、信息栏和备注作为一个整体露出到键盘上方。
  Column(modifier = Modifier.imePaddingTarget()) {
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
      modelState = modelState,
      firstMonday = firstMonday,
      categories = categories,
      editable = uiState is ScheduleUi.Edit,
      onClickDate = { uiState = ScheduleUi.Edit.Date },
      onClickTime = { uiState = ScheduleUi.Edit.Time },
      onClickRepeat = { uiState = ScheduleUi.Edit.Repeat },
      onClickRemind = { uiState = ScheduleUi.Edit.Remind },
      onClickCategory = { uiState = ScheduleUi.Edit.Category },
    )
    Spacer(modifier = Modifier.height(10.dp))
    if (uiState is ScheduleUi.Show || uiState is ScheduleUi.Edit.Note) {
      Box {
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
    }
  }
  when (uiState) {
    // 备注已包含在上方 IME 目标区域中。
    ScheduleUi.Show, ScheduleUi.Edit.Note -> Unit
    // 日期：下方就地变日历，点某天实时改写开始/结束的日期（周数随之重算）；← 返回
    ScheduleUi.Edit.Date -> EditScheduleCalendarArea(state = modelState)
    // 时间段：下方变时分滚轮
    ScheduleUi.Edit.Time -> EditScheduleTimeArea(state = modelState)
    // 重复：内容较多，外层弹窗会增高；结束条件固定在底部，主体选择区内部滚动。
    ScheduleUi.Edit.Repeat -> EditScheduleRecurrenceArea(
      draft = modelState.recurrence,
      anchorDate = modelState.recurrenceAnchorDate,
      firstMonday = firstMonday,
      onChange = { modelState.recurrence = it },
      modifier = Modifier.fillMaxWidth(),
    )
    // 提醒：下方就地变提前分钟选项，实时写回 state.remindMinutes
    ScheduleUi.Edit.Remind -> EditScheduleRemindArea(
      current = modelState.remindMinutes,
      onChoose = { modelState.remindMinutes = it },
      modifier = Modifier,
    )
    // 分类：由信息栏进入，选择后仍停留在子区域，右上返回按钮回到备注。
    ScheduleUi.Edit.Category -> CategoryChooser(
      categories = categories,
      selected = modelState.categoryId,
      onSelected = { modelState.categoryId = it },
    )
  }
  Spacer(modifier = Modifier.height(16.dp))
}

/**
 * 共享编辑器中的分类选择器。
 *
 * 只写回已有分类 identity，不读取分类自由颜色文本；选中态沿用共享编辑器已有的轻量 ToggleChip。
 * 分类新增、删除需要处理其他清单引用，暂不放在清单编辑弹窗中，后续由独立设置入口负责。
 */
@Composable
private fun CategoryChooser(
  categories: List<ScheduleCategory>,
  selected: CategoryId?,
  onSelected: (CategoryId?) -> Unit,
) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    ToggleChip("未分组", selected = selected == null) { onSelected(null) }
    categories.sortedBy(ScheduleCategory::sortOrder).forEach { category ->
      ToggleChip(category.name, selected = selected == category.id) { onSelected(category.id) }
    }
  }
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
          painter = rememberVectorPainter(Icons.Rounded.Check),
          contentDescription = "保存",
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

/* ---------------- 紧凑信息栏 ---------------- */

@Composable
private fun InfoRow(
  modelState: EditScheduleModelState,
  firstMonday: Date?,
  categories: List<ScheduleCategory>,
  editable: Boolean,
  onClickDate: () -> Unit = {},
  onClickTime: () -> Unit = {},
  onClickRepeat: () -> Unit = {},
  onClickRemind: () -> Unit = {},
  onClickCategory: () -> Unit = {},
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
      text = text ?: if (editable) "设置时间" else "未设置",
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
  // 🏷分类；使用纯描边标签图标，避免 Material 图标与其余自绘图标的风格不一致。
  val categoryIcon = rememberIcAddtodoCategory()
  val categorySegment = remember(
    colors,
    editable,
    categories,
    modelState.categoryId,
    categoryIcon,
    onClickCategory,
  ) {
    val selectedName = categories.firstOrNull { it.id == modelState.categoryId }?.name
    InfoTextSegment(
      id = "category",
      text = selectedName ?: "未分组",
      icon = categoryIcon,
      color = if (selectedName == null) placeholderColor else colors.tvLv2,
      onClick = if (editable && categories.isNotEmpty()) onClickCategory else null,
    )
  }

  BalancedInfoRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalSpacing = 10.dp,
    verticalSpacing = 5.dp,
  ) {
    // 日期和时间仍使用单个富文本，统一字体度量，避免桌面端纯数字/英文文本高度不一致。
    BasicText(
      text = dateSegment.annotatedText + AnnotatedString("  ") + timeSegment.annotatedText,
      style = TextStyle(fontSize = 13.sp, lineHeight = 13.sp, color = colors.tvLv2),
      inlineContent = dateSegment.inlineContent + timeSegment.inlineContent,
      maxLines = 1,
    )
    (listOf(repeatSegment, remindSegment) + listOf(categorySegment)).forEach {
      BasicText(
        text = it.annotatedText,
        style = TextStyle(fontSize = 13.sp, lineHeight = 13.sp, color = colors.tvLv2),
        inlineContent = it.inlineContent,
        maxLines = 1,
      )
    }
  }
}

/**
 * 最多使用两行展示信息项，并在必须换行时选择更均衡的连续切分点。
 *
 * 信息项顺序不会改变；优先保证第一行不短于第二行，再让两行宽度差尽可能小。
 * 这可以避免普通 [FlowRow] 把大多数内容塞进第一行、第二行只留下一个短项。
 */
@Composable
private fun BalancedInfoRow(
  modifier: Modifier = Modifier,
  horizontalSpacing: Dp,
  verticalSpacing: Dp,
  content: @Composable () -> Unit,
) {
  Layout(
    modifier = modifier,
    content = content,
  ) { measurables, constraints ->
    val itemConstraints = constraints.copy(minWidth = 0, minHeight = 0)
    val placeables = measurables.map { it.measure(itemConstraints) }
    if (placeables.isEmpty()) {
      layout(constraints.minWidth, constraints.minHeight) {}
    } else {
      val horizontalSpacingPx = horizontalSpacing.roundToPx()
      val verticalSpacingPx = verticalSpacing.roundToPx()
      val splitIndex = chooseBalancedInfoRowSplit(
        itemWidths = placeables.map { it.width },
        horizontalSpacing = horizontalSpacingPx,
        maxWidth = constraints.maxWidth,
      )
      val rows = if (splitIndex == null) {
        listOf(placeables)
      } else {
        listOf(placeables.take(splitIndex), placeables.drop(splitIndex))
      }
      val rowWidths = rows.map { row ->
        row.sumOf { it.width } + horizontalSpacingPx * (row.size - 1).coerceAtLeast(0)
      }
      val rowHeights = rows.map { row -> row.maxOf { it.height } }
      val layoutWidth = rowWidths.max().coerceIn(constraints.minWidth, constraints.maxWidth)
      val contentHeight = rowHeights.sum() +
        verticalSpacingPx * (rows.size - 1).coerceAtLeast(0)
      val layoutHeight = contentHeight.coerceIn(constraints.minHeight, constraints.maxHeight)

      layout(layoutWidth, layoutHeight) {
        var y = 0
        rows.forEachIndexed { rowIndex, row ->
          var x = 0
          val rowHeight = rowHeights[rowIndex]
          row.forEach { placeable ->
            placeable.placeRelative(x, y + (rowHeight - placeable.height) / 2)
            x += placeable.width + horizontalSpacingPx
          }
          y += rowHeight + verticalSpacingPx
        }
      }
    }
  }
}

/**
 * 根据各信息项的实测宽度选择两行的连续切分位置；全部内容可放入一行时返回 `null`。
 *
 * [horizontalSpacing] 与 [maxWidth] 均为像素值。正常情况下两行都必须放得下；若极窄窗口下
 * 不存在可行切分，则优先选择总溢出最少的切分，交由父布局裁剪。
 */
internal fun chooseBalancedInfoRowSplit(
  itemWidths: List<Int>,
  horizontalSpacing: Int,
  maxWidth: Int,
): Int? {
  require(itemWidths.all { it >= 0 }) { "itemWidths must not contain negative values" }
  require(horizontalSpacing >= 0) { "horizontalSpacing must not be negative" }
  require(maxWidth >= 0) { "maxWidth must not be negative" }
  if (itemWidths.size < 2) return null

  fun rowWidth(startIndex: Int, endIndex: Int): Long {
    val itemCount = endIndex - startIndex
    return itemWidths.subList(startIndex, endIndex).sumOf { it.toLong() } +
      horizontalSpacing.toLong() * (itemCount - 1).coerceAtLeast(0)
  }

  val maxWidthLong = maxWidth.toLong()
  if (rowWidth(0, itemWidths.size) <= maxWidthLong) return null

  val candidates = (1 until itemWidths.size).map { splitIndex ->
    Triple(
      splitIndex,
      rowWidth(0, splitIndex),
      rowWidth(splitIndex, itemWidths.size),
    )
  }
  val fitting = candidates.filter { (_, firstWidth, secondWidth) ->
    firstWidth <= maxWidthLong && secondWidth <= maxWidthLong
  }
  val firstRowNotShorter = fitting.filter { (_, firstWidth, secondWidth) ->
    firstWidth >= secondWidth
  }

  return when {
    firstRowNotShorter.isNotEmpty() -> firstRowNotShorter.minBy { (_, firstWidth, secondWidth) ->
      firstWidth - secondWidth
    }.first

    fitting.isNotEmpty() -> fitting.minBy { (_, firstWidth, secondWidth) ->
      abs(firstWidth - secondWidth)
    }.first

    else -> candidates.minWith(
      compareBy<Triple<Int, Long, Long>>(
        { (_, firstWidth, secondWidth) ->
          (firstWidth - maxWidthLong).coerceAtLeast(0) +
            (secondWidth - maxWidthLong).coerceAtLeast(0)
        },
        { (_, firstWidth, secondWidth) -> abs(firstWidth - secondWidth) },
      )
    ).first
  }
}

private data class InfoTextSegment(
  val id: String,
  val text: String?,
  val icon: ImageVector,
  val color: Color,
  val onClick: (() -> Unit)?,
) {
  // 图标
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
        if (selected) accent.copy(alpha = 0.1f) else colors.topBg.copy(alpha = 0f),
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
