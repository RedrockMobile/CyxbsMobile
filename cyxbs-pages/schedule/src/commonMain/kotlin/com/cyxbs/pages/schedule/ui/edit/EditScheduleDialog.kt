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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.platform.LocalFocusManager
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.imePaddingTarget
import com.cyxbs.components.utils.compose.plusDsl
import com.cyxbs.components.utils.compose.rememberDerivedStateOfStructure
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.schedule.data.repository.ScheduleRepositoryProvider
import com.cyxbs.pages.schedule.domain.model.CategoryId
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleCategory
import com.cyxbs.pages.schedule.domain.model.ScheduleKind
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrence
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrenceAdjustment
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.recurrence.RecurrenceEngine
import com.cyxbs.pages.schedule.domain.recurrence.SeriesSplitter
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepository
import com.cyxbs.pages.schedule.domain.repository.ScheduleSnapshot
import com.cyxbs.pages.schedule.ui.category.rememberScheduleCategoryCatalog
import com.cyxbs.pages.schedule.ui.dialog.ScheduleBottomSheet
import com.cyxbs.pages.schedule.ui.dialog.ScheduleConfirmDialog
import com.cyxbs.pages.schedule.ui.edit.area.EditScheduleCalendarArea
import com.cyxbs.pages.schedule.ui.edit.area.EditScheduleRecurrenceArea
import com.cyxbs.pages.schedule.ui.edit.area.EditScheduleRemindArea
import com.cyxbs.pages.schedule.ui.edit.area.EditScheduleTimeArea
import com.cyxbs.pages.schedule.ui.model.ScheduleDraft
import com.cyxbs.pages.schedule.ui.todo.main.ScheduleTodoCompletedIndicatorColor
import com.cyxbs.pages.schedule.ui.todo.main.ScheduleTodoPendingIndicatorColor
import com.cyxbs.pages.schedule.widget.rememberIcAddtodoCalendar
import com.cyxbs.pages.schedule.widget.rememberIcAddtodoCategory
import com.cyxbs.pages.schedule.widget.rememberIcAddtodoNotice
import com.cyxbs.pages.schedule.widget.rememberIcAddtodoRelation
import com.cyxbs.pages.schedule.widget.rememberIcAddtodoRepeat
import com.cyxbs.pages.schedule.widget.rememberIcAddtodoTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 添加 / 查看 / 编辑日程的统一底部弹窗 —— **邮子清单与课表共用同一套**，外观对齐课表事务(affair)。
 *
 * 形态（见与用户确认的文本草图）：标题行 + **紧凑信息栏**(日期·第N周·周几·时间段·重复·提醒·分类·关联) + 备注。
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
  /** 默认使用进程共享仓库；Preview/mock 可注入其页面 ViewModel 使用的同一仓库。 */
  categoryRepository: ScheduleRepository = ScheduleRepositoryProvider.repository,
  /** 弹窗外背景色；外部宿主可传透明，普通入口继续使用 Schedule 默认遮罩。 */
  scrimColor: Color? = null,
  /**
   * true 时只绘制业务内容，由调用方同时提供外层 BottomSheet 和 Window；false 时本组件提供
   * BottomSheet，但 Window 仍由调用入口持有，确保主编辑、范围选择和确认层处于同一窗口。
   */
  embeddedInExternalHost: Boolean = false,
  /** 是否展示“关联清单/关联课表”入口；清单页与课表页可按各自业务入口启用。 */
  showCourseRelation: Boolean = false,
  /**
   * 新建日程保存后是否关闭当前弹窗。课表创建事务会传 false，等待本地创建完成后在同一宿主中替换为详情；
   * 普通清单新建仍保持保存后关闭。
   */
  dismissAfterCreateSave: Boolean = true,
  onDismiss: () -> Unit,
  /** 第三个参数仅在所选固定默认分类尚未落库时非空，调用方需沿用原子保存命令。 */
  onConfirm: (EditScheduleModelState, EditScope, ScheduleCategory?) -> Unit,
  onDelete: ((EditScope) -> Unit)? = null,
  /** 已保存清单的快速完成操作；重复项由调用方按当前 occurrence identity 更新。 */
  onToggleCompleted: ((Boolean) -> Unit)? = null,
  /** 嵌入外部宿主时报告查看/编辑模式；普通 ScheduleBottomSheet 可忽略。 */
  onEditModeChanged: (Boolean) -> Unit = {},
  /**
   * 嵌入外部宿主时注册关闭拦截函数：函数返回 true 表示允许宿主继续收起，返回 false 表示拦截关闭，
   * 宿主需要把已被拖动的 BottomSheet 回弹到展开状态；传入 null 表示编辑内容已离开组合，应注销拦截。
   */
  onDismissRequestChanged: (((suspend () -> Boolean)?) -> Unit) = {},
  /** 嵌入外部宿主时把子弹层交给 Window 根布局绘制；传入 null 表示清除。 */
  onWindowOverlayContentChanged: (((@Composable () -> Unit)?) -> Unit) = {},
) {
  if (!show) return

  val modelState = rememberEditScheduleModelState(
    editSchedule,
    editOccurrence,
    creationKind,
    creationTiming,
  )
  val categoryCatalog = rememberScheduleCategoryCatalog(categoryRepository)
  val repositorySnapshot by categoryRepository.snapshot.collectAsState()

  var showUnsavedExit by remember { mutableStateOf(false) }
  var showReminderPermissionExplanation by remember { mutableStateOf(false) }
  var resetReminderAfterRejectedAuthorization by remember { mutableStateOf(false) }
  var pendingDismissDecision by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
  var scopeChooser by remember { mutableStateOf<ScopeAction?>(null) }
  // 普通日程直接记录 ALL；重复日程先选择范围，再统一进入删除确认，避免任何入口绕过二次确认。
  var pendingDeleteScope by remember { mutableStateOf<EditScope?>(null) }
  // 最后一个可见实例的单次删除会升级为整系列删除，确认文案需要明确告知这一影响范围。
  var deletingLastOccurrence by remember { mutableStateOf(false) }
  var contentUiState by remember(modelState) {
    mutableStateOf<ScheduleUi>(if (modelState.origin == null) ScheduleUi.Edit.Note else ScheduleUi.Show)
  }
  var acceptedDraft by remember(modelState) { mutableStateOf<ScheduleDraft?>(null) }
  var acceptedRestoreIds by remember(modelState) { mutableStateOf<Set<RecurrenceId>>(emptySet()) }
  val coroutineScope = rememberCoroutineScope()
  val reminderAuthorization = rememberScheduleReminderAuthorization { granted ->
    showReminderPermissionExplanation = false
    if (!granted && resetReminderAfterRejectedAuthorization) modelState.remindMinutes = -1
    resetReminderAfterRejectedAuthorization = false
  }

  // 开学第一天（周一）：用于推导第N周，一次会话读一次即可。
  val firstMonday = remember { SchoolCalendar.getFirstMonDay() }
  val needScope = editSchedule?.recurrence != null && recurrenceId != null
  val confirm: (EditScope) -> Unit = { scope ->
    onConfirm(
      modelState,
      scope,
      categoryCatalog.findMissingDefaultCategory(modelState.categoryId),
    )
  }
  val dismissGate: suspend () -> Boolean = {
    val currentChangesWereAccepted = acceptedDraft == modelState.toDraft() &&
      acceptedRestoreIds == modelState.occurrenceRestoreIds
    if (modelState.isChanged && !currentChangesWereAccepted) {
      val decision = pendingDismissDecision ?: CompletableDeferred<Boolean>().also {
        pendingDismissDecision = it
        showUnsavedExit = true
      }
      decision.await()
    } else {
      true
    }
  }
  DisposableEffect(embeddedInExternalHost, modelState, onDismissRequestChanged) {
    // 外部 BottomSheet 必须复用编辑器的 dirty 判断，否则点击蒙层会绕过未保存确认。
    if (embeddedInExternalHost) onDismissRequestChanged(dismissGate)
    onDispose {
      if (embeddedInExternalHost) onDismissRequestChanged(null)
    }
  }
  DisposableEffect(modelState) {
    onDispose { pendingDismissDecision?.cancel() }
  }
  val doDelete: () -> Unit = {
    if (onDelete != null) {
      if (needScope) scopeChooser = ScopeAction.DELETE
      else pendingDeleteScope = EditScope.ALL
    }
  }

  /** 保存已被调用方接收后，已有日程回到查看态；新建入口按宿主策略关闭或等待详情替换。 */
  val finishSave: (EditScope, () -> Unit) -> Unit = { scope, requestAnimatedDismiss ->
    confirm(scope)
    acceptedDraft = modelState.toDraft()
    acceptedRestoreIds = modelState.occurrenceRestoreIds
    when {
      modelState.origin != null -> contentUiState = ScheduleUi.Show
      dismissAfterCreateSave -> requestAnimatedDismiss()
      else -> Unit // 创建事务由调用方在本地写入完成后原位替换为详情。
    }
  }

  val sheetContent: @Composable (requestAnimatedDismiss: () -> Unit) -> Unit = { requestAnimatedDismiss ->
    val requestDismiss: () -> Unit = {
      coroutineScope.launch {
        if (dismissGate()) requestAnimatedDismiss()
      }
      Unit
    }
    val doSave: () -> Unit = {
      when {
        // 已有日程即使没有改动，勾选也只退出编辑态；新建入口没有可展示资源，仍按宿主策略处理。
        !modelState.isChanged && modelState.origin != null -> contentUiState = ScheduleUi.Show
        !modelState.isChanged -> if (dismissAfterCreateSave) requestAnimatedDismiss()
        needScope -> scopeChooser = ScopeAction.SAVE
        else -> finishSave(EditScope.ALL, requestAnimatedDismiss)
      }
    }
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
        uiState = contentUiState,
        onUiStateChanged = { contentUiState = it },
        firstMonday = firstMonday,
        categories = categoryCatalog.selectableCategories,
        repositorySnapshot = repositorySnapshot,
        occurrenceAdjustments = repositorySnapshot.occurrenceAdjustments.filterNot {
          it.recurrenceId in modelState.occurrenceRestoreIds
        },
        showCourseRelation = showCourseRelation,
        reminderAuthorized = reminderAuthorization.authorized,
        onRequestReminderAuthorization = { resetOnFailure ->
          resetReminderAfterRejectedAuthorization = resetOnFailure
          if (resetOnFailure && modelState.remindMinutes < 0) modelState.remindMinutes = 10
          if (reminderAuthorization.authorized) {
            reminderAuthorization.requestAuthorization()
          } else {
            showReminderPermissionExplanation = true
          }
        },
        onSave = { doSave() },
        onCancel = { requestDismiss() },
        onDelete = { doDelete() },
        onRestoreOccurrenceAdjustment = { adjustment ->
          modelState.stageOccurrenceRestore(adjustment.recurrenceId)
        },
        onToggleCompleted = onToggleCompleted,
        onEditModeChanged = onEditModeChanged,
      )
    }
  }
  val resolveDismissDecision: (Boolean) -> Unit = { allowDismiss ->
    val decision = pendingDismissDecision
    pendingDismissDecision = null
    showUnsavedExit = false
    decision?.complete(allowDismiss)
  }
  val showThisAndFollowing = remember(editSchedule, recurrenceId) {
    editSchedule != null && recurrenceId != null &&
      SeriesSplitter.canSplitAt(editSchedule, recurrenceId)
  }
  val overlayContent: @Composable (requestAnimatedDismiss: () -> Unit) -> Unit = { requestAnimatedDismiss ->
    // 平台权限引导也必须跟随当前 Window 根节点，不能在课表外部宿主内再创建独立 Dialog。
    reminderAuthorization.overlayContent()

    // 三态选择
    EditScopeChooserSheet(
      show = scopeChooser != null,
      isDelete = scopeChooser == ScopeAction.DELETE,
      showThisAndFollowing = showThisAndFollowing,
      onDismiss = { scopeChooser = null },
      onChoose = { scope ->
        when (scopeChooser) {
          ScopeAction.SAVE -> {
            scopeChooser = null
            finishSave(scope, requestAnimatedDismiss)
          }
          ScopeAction.DELETE -> {
            scopeChooser = null
            val deletesLast = scope == EditScope.THIS_ONLY && editSchedule != null && recurrenceId != null &&
              RecurrenceEngine.isOnlyRemainingOccurrence(
                editSchedule,
                repositorySnapshot.occurrenceAdjustments.filter { it.scheduleId == editSchedule.id },
                recurrenceId,
              )
            deletingLastOccurrence = deletesLast
            pendingDeleteScope = if (deletesLast) EditScope.ALL else scope
          }
          null -> {}
        }
      },
    )

    ScheduleConfirmDialog(
      show = pendingDeleteScope != null,
      title = "删除日程",
      message = when {
        deletingLastOccurrence ->
          "这是该重复日程最后一个未删除的日期，继续删除将同时删除整个重复日程。"
        pendingDeleteScope == EditScope.THIS_ONLY ->
          "是否确认删除？(“仅删除此次”可在重复设置的调整中恢复)"
        else -> "删除后将无法恢复，是否确认删除？"
      },
      confirmText = "删除",
      dismissText = "取消",
      embeddedInWindow = true,
      onConfirm = {
        val scope = pendingDeleteScope ?: return@ScheduleConfirmDialog
        pendingDeleteScope = null
        deletingLastOccurrence = false
        onDelete?.invoke(scope)
        requestAnimatedDismiss()
      },
      onDismiss = {
        pendingDeleteScope = null
        deletingLastOccurrence = false
      },
    )

    // 确认结果会恢复挂起的关闭请求：继续编辑返回 false 并由宿主回弹，放弃返回 true 后完成收起。
    ScheduleConfirmDialog(
      show = showUnsavedExit,
      title = "未保存",
      message = "当前修改未保存，是否放弃？",
      confirmText = "放弃",
      dismissText = "继续编辑",
      embeddedInWindow = true,
      onConfirm = { resolveDismissDecision(true) },
      onDismiss = { resolveDismissDecision(false) },
    )

    ScheduleConfirmDialog(
      show = showReminderPermissionExplanation,
      title = "开启日历权限？",
      message = "日程提醒借助手机系统日历实现。授权后，掌邮会把日程和提醒写入自己管理的“掌邮日程”日历。",
      confirmText = "去授权",
      dismissText = "暂不使用",
      embeddedInWindow = true,
      onConfirm = {
        // 先关闭说明层，避免 ScheduleConfirmDialog 随后的 onDismiss 把本次确认误判为拒绝。
        showReminderPermissionExplanation = false
        reminderAuthorization.requestAuthorization()
      },
      onDismiss = {
        if (showReminderPermissionExplanation) {
          showReminderPermissionExplanation = false
          if (resetReminderAfterRejectedAuthorization) modelState.remindMinutes = -1
          resetReminderAfterRejectedAuthorization = false
        }
      },
    )
  }
  if (embeddedInExternalHost) {
    sheetContent(onDismiss)
    val currentOverlayContent = rememberUpdatedState(overlayContent)
    DisposableEffect(onWindowOverlayContentChanged) {
      // 外部宿主在同一个 Window 的根布局末尾绘制，避免弹层跟随被拖下去的内容一起隐藏。
      onWindowOverlayContentChanged { currentOverlayContent.value(onDismiss) }
      onDispose { onWindowOverlayContentChanged(null) }
    }
  } else {
    ScheduleBottomSheet(
      show = true,
      onDismiss = onDismiss,
      scrimColor = scrimColor,
      onDismissRequest = dismissGate,
      overlayContent = overlayContent,
      content = sheetContent,
    )
  }
}

/** 范围选择弹层当前要提交的动作；保存与删除共用范围状态机，但最终命令入口严格分离。 */
private enum class ScopeAction { SAVE, DELETE }

/** 统一弹窗的查看态与编辑子区域状态；仅控制展示流转，不承载仓库写入状态。 */
private sealed interface ScheduleUi {
  data object Show : ScheduleUi

  /**
   * 编辑弹窗内部区域的状态机，统一描述标题下方当前展示的子编辑器。
   * [Note] 为默认备注区，[Date]/[Time]/[Repeat]/[Remind]/[Category] 分别承载日期、时间、重复、提醒与分类编辑；
   * 课表关联是信息栏中的直接开关，不需要额外子区域。
   * 除关联开关外，点击信息栏会切换子区域；所有改动都立即写回同一个 [EditScheduleModelState]，返回 [Note] 不回滚。
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
  uiState: ScheduleUi,
  onUiStateChanged: (ScheduleUi) -> Unit,
  firstMonday: Date?,
  categories: List<ScheduleCategory>,
  repositorySnapshot: ScheduleSnapshot,
  occurrenceAdjustments: List<ScheduleOccurrenceAdjustment>,
  showCourseRelation: Boolean,
  reminderAuthorized: Boolean,
  onRequestReminderAuthorization: (resetOnFailure: Boolean) -> Unit,
  onSave: () -> Unit,
  onCancel: () -> Unit,
  onDelete: () -> Unit,
  onRestoreOccurrenceAdjustment: (ScheduleOccurrenceAdjustment) -> Unit,
  onToggleCompleted: ((Boolean) -> Unit)?,
  onEditModeChanged: (Boolean) -> Unit,
) {
  val colors = LocalAppColors.current
  LaunchedEffect(uiState is ScheduleUi.Edit) {
    onEditModeChanged(uiState is ScheduleUi.Edit)
  }
  DisposableEffect(Unit) {
    onDispose { onEditModeChanged(false) }
  }
  // 标题或备注获得焦点时，标题、信息栏和备注作为一个整体露出到键盘上方。
  Column(modifier = Modifier.imePaddingTarget()) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      if (modelState.origin?.todoState != null && onToggleCompleted != null) {
        ScheduleCompletionCircle(
          completed = modelState.isOccurrenceCompleted,
          enabled = uiState is ScheduleUi.Show,
          onClick = { onToggleCompleted(!modelState.isOccurrenceCompleted) },
        )
        Spacer(modifier = Modifier.width(12.dp))
      }
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
        onEdit = { onUiStateChanged(ScheduleUi.Edit.Note) },
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
      showCourseRelation = showCourseRelation,
      reminderAuthorized = reminderAuthorized,
      editable = uiState is ScheduleUi.Edit,
      onClickDate = { onUiStateChanged(ScheduleUi.Edit.Date) },
      onClickTime = { onUiStateChanged(ScheduleUi.Edit.Time) },
      onClickRepeat = { onUiStateChanged(ScheduleUi.Edit.Repeat) },
      onClickRemind = {
        if (modelState.effectiveTiming == ScheduleTiming.Unscheduled) {
          "请先设置时间后再开启提醒".toast()
          return@InfoRow
        }
        val editing = uiState is ScheduleUi.Edit
        // 只有“从不提醒切换为提醒”的新选择在授权失败后回退；其他设备同步来的提醒必须保留业务值。
        val enablingNewReminder = editing && modelState.remindMinutes < 0
        if (editing) onUiStateChanged(ScheduleUi.Edit.Remind)
        onRequestReminderAuthorization(enablingNewReminder)
      },
      onClickCategory = { onUiStateChanged(ScheduleUi.Edit.Category) },
      onClickRelation = {
        if (modelState.effectiveTiming == ScheduleTiming.Unscheduled) {
          "请先设置时间后再关联课表".toast()
        } else {
          modelState.toggleCourseRelation()
        }
      },
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
    ScheduleUi.Edit.Date -> EditScheduleCalendarArea(
      state = modelState,
      snapshot = repositorySnapshot,
    )
    // 时间：下方可切换全天、时间段或时间点；全天不展示时分滚轮。
    ScheduleUi.Edit.Time -> EditScheduleTimeArea(state = modelState)
    // 重复：内容较多，外层弹窗会增高；结束条件固定在底部，主体选择区内部滚动。
    ScheduleUi.Edit.Repeat -> EditScheduleRecurrenceArea(
      draft = modelState.recurrence,
      anchorDate = modelState.recurrenceAnchorDate,
      firstMonday = firstMonday,
      schedule = modelState.origin,
      occurrenceAdjustments = occurrenceAdjustments,
      onRestoreOccurrenceAdjustment = onRestoreOccurrenceAdjustment,
      onChange = { modelState.recurrence = it },
      modifier = Modifier.fillMaxWidth(),
    )
    // 提醒：下方就地变提前分钟选项，实时写回 state.remindMinutes
    ScheduleUi.Edit.Remind -> EditScheduleRemindArea(
      current = modelState.remindMinutes,
      onChoose = { modelState.remindMinutes = it },
      onEnableRequested = { onRequestReminderAuthorization(true) },
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
 * 标题左侧的清单完成按钮。
 *
 * 查看态直接执行快速完成命令；编辑态只保留视觉状态并禁用点击，避免日程从当前列表消失时丢失未保存输入。
 */
@Composable
private fun ScheduleCompletionCircle(
  completed: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  val colors = LocalAppColors.current
  Box(
    modifier = Modifier
      .size(24.dp)
      .then(if (enabled) Modifier.clickableNoIndicator(onClick = onClick) else Modifier),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = if (completed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
      contentDescription = if (completed) "恢复未完成" else "标记完成",
      tint = when {
        !MaterialTheme.colors.isLight -> colors.tvLv3.copy(alpha = 0.46f)
        completed -> ScheduleTodoCompletedIndicatorColor
        else -> ScheduleTodoPendingIndicatorColor
      },
      modifier = Modifier.size(if (completed) 22.dp else 24.dp),
    )
  }
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
  showCourseRelation: Boolean,
  reminderAuthorized: Boolean,
  editable: Boolean,
  onClickDate: () -> Unit = {},
  onClickTime: () -> Unit = {},
  onClickRepeat: () -> Unit = {},
  onClickRemind: () -> Unit = {},
  onClickCategory: () -> Unit = {},
  onClickRelation: () -> Unit = {},
) {
  val date = modelState.anchorDate
  val colors = LocalAppColors.current
  val placeholderColor = colors.tvLv2.copy(alpha = 0.4f)
  // 未排期日程使用今天初始化日期选择器，但该日期尚未进入领域 timing，只能按占位态展示。
  val hasEffectiveDate = modelState.effectiveTiming != ScheduleTiming.Unscheduled
  // 📅日期 第几周 周几
  val dateIcon = rememberIcAddtodoCalendar()
  val dateSegment = remember(
    colors,
    editable,
    firstMonday,
    date,
    hasEffectiveDate,
    dateIcon,
    onClickDate
  ) {
    InfoTextSegment(
      id = "date",
      suggestedRow = 0,
      text = if (hasEffectiveDate) {
        buildString {
          append(formatInfoDate(date))
          formatWeekOfTerm(firstMonday, date)?.let { append(' ').append(it) }
          append(' ').append(formatWeekday(date))
        }
      } else {
        "未设置日期"
      },
      icon = dateIcon,
      color = if (hasEffectiveDate) colors.tvLv2 else placeholderColor,
      onClick = if (editable) onClickDate else null,
    )
  }
  // 🕙时间段
  val timeIcon = rememberIcAddtodoTime()
  val timeSegment = remember(
    colors,
    editable,
    modelState.isAllDay,
    modelState.startMinuteOfDay,
    modelState.endMinuteOfDay,
    placeholderColor,
    timeIcon,
    onClickTime
  ) {
    val text = if (modelState.isAllDay) {
      "全天"
    } else {
      formatTimeRange(modelState.startMinuteOfDay, modelState.endMinuteOfDay)
    }
    InfoTextSegment(
      id = "time",
      suggestedRow = 0,
      text = text ?: "未设置时间",
      icon = timeIcon,
      color = if (text == null) placeholderColor else colors.tvLv2,
      onClick = if (editable) onClickTime else null,
    )
  }
  // ⏰提醒时间
  val remindIcon = rememberIcAddtodoNotice()
  val remindSegment =
    remember(
      colors,
      editable,
      modelState.remindMinutes,
      reminderAuthorized,
      remindIcon,
      onClickRemind,
    ) {
      val text = formatRemindAhead(modelState.remindMinutes)
      val unauthorized = text != null && !reminderAuthorized
      InfoTextSegment(
        id = "remind",
        suggestedRow = 0,
        text = text ?: "不提醒",
        icon = remindIcon,
        color = if (unauthorized) placeholderColor else colors.tvLv2,
        suffix = if (unauthorized) "(未授权)" else null,
        suffixColor = colors.tvLv2,
        // 只读态仅开放“已有提醒但当前设备未授权”的修复入口，不把普通信息项变成编辑入口。
        onClick = if (editable || unauthorized) onClickRemind else null,
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
        suggestedRow = 1,
        text = text ?: "仅一次",
        icon = repeatIcon,
        color = colors.tvLv2,
        onClick = if (editable) onClickRepeat else null,
      )
    }
  // 🔗课表/清单关联；来源类型固定，只展示当前关系，不把领域枚举泄漏到课表模块。
  val relationIcon = rememberIcAddtodoRelation()
  val relationSegment = remember(
    colors,
    editable,
    modelState.kind,
    modelState.isInTodoList,
    modelState.linkedToCourse,
    relationIcon,
    onClickRelation,
  ) {
    val linked = when (modelState.kind) {
      ScheduleKind.TODO -> modelState.linkedToCourse
      ScheduleKind.AFFAIR -> modelState.isInTodoList
    }
    val text = when (modelState.kind) {
      ScheduleKind.TODO -> if (linked) "已关联课表" else "关联课表"
      ScheduleKind.AFFAIR -> if (linked) "已关联清单" else "关联清单"
    }
    InfoTextSegment(
      id = "relation",
      suggestedRow = 1,
      text = text,
      icon = relationIcon,
      color = if (linked) colors.tvLv2 else placeholderColor,
      onClick = if (editable) onClickRelation else null,
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
      suggestedRow = 1,
      text = selectedName ?: "未分组",
      icon = categoryIcon,
      color = if (selectedName == null) placeholderColor else colors.tvLv2,
      onClick = if (editable && categories.isNotEmpty()) onClickCategory else null,
    )
  }
  StableInfoFlowRow(
    modifier = Modifier.fillMaxWidth().clipToBounds(),
    horizontalSpacing = 10.dp,
    verticalSpacing = 5.dp,
  ) {
    buildList {
      // 日期、时间、提醒固定排在最前，只有实际宽度不足时才由 FlowRow 顺序换行。
      add(dateSegment)
      add(timeSegment)
      add(remindSegment)
      add(repeatSegment)
      if (showCourseRelation) add(relationSegment)
      // 原生事务没有清单分组；关联清单后才开放分组选择，并以默认灰色进入清单体系。
      if (modelState.kind == ScheduleKind.TODO || modelState.isInTodoList) add(categorySegment)
    }.forEach {
      BasicText(
        text = it.annotatedText,
        style = TextStyle(fontSize = 13.sp, lineHeight = 13.sp, color = colors.tvLv2),
        inlineContent = it.inlineContent,
        maxLines = 1,
        modifier = Modifier.infoRowSuggestedRow(it.suggestedRow),
      )
    }
  }
}

/**
 * 行归属稳定的信息栏流式布局。
 *
 * 首次布局时从每个 Item 的建议行开始自然换行；前一行即使有剩余空间，也不会把后续 Item 提前。
 * 之后容器宽度、Item 数量、建议行或间距变化时才整体重新分行。
 * 单个文案变长并导致本行溢出时，只允许该 Item 临时移动到后续可容纳的行，其他 Item 保持基准行；
 * 文案缩短后该 Item 会优先回到基准行。极端情况下单项仍超宽时由 [Modifier.clipToBounds] 裁剪。
 */
@Composable
private fun StableInfoFlowRow(
  modifier: Modifier,
  horizontalSpacing: Dp,
  verticalSpacing: Dp,
  content: @Composable () -> Unit,
) {
  val rowCache = remember { StableInfoRowLayoutCache() }
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
      val rowIndexes = rowCache.rowsFor(
        itemWidths = placeables.map { it.width },
        suggestedRows = measurables.map {
          (it.parentData as? InfoRowParentData)?.suggestedRow ?: 0
        },
        horizontalSpacing = horizontalSpacingPx,
        maxWidth = constraints.maxWidth,
      )
      // 临时后移的 Item 可能使行号不再按 Item 顺序单调递增，必须取最大行号。
      val rowCount = rowIndexes.max() + 1
      val rowHeights = IntArray(rowCount)
      val rowWidths = IntArray(rowCount)
      placeables.forEachIndexed { index, placeable ->
        val row = rowIndexes[index]
        rowHeights[row] = maxOf(rowHeights[row], placeable.height)
        if (rowWidths[row] > 0) rowWidths[row] += horizontalSpacingPx
        rowWidths[row] += placeable.width
      }
      val contentHeight = rowHeights.sum() + verticalSpacingPx * (rowCount - 1)
      val layoutWidth = rowWidths.max().coerceIn(constraints.minWidth, constraints.maxWidth)
      val layoutHeight = contentHeight.coerceIn(constraints.minHeight, constraints.maxHeight)

      layout(layoutWidth, layoutHeight) {
        val rowY = IntArray(rowCount)
        for (row in 1 until rowCount) {
          rowY[row] = rowY[row - 1] + rowHeights[row - 1] + verticalSpacingPx
        }
        val rowX = IntArray(rowCount)
        placeables.forEachIndexed { index, placeable ->
          val row = rowIndexes[index]
          placeable.placeRelative(
            x = rowX[row],
            y = rowY[row] + (rowHeights[row] - placeable.height) / 2,
          )
          rowX[row] += placeable.width + horizontalSpacingPx
        }
      }
    }
  }
}

/**
 * 缓存信息栏的基准行归属；建议行是最早可展示位置，仅允许溢出的 Item 向后临时换行。
 */
internal class StableInfoRowLayoutCache {
  private var cachedMaxWidth = -1
  private var cachedItemCount = -1
  private var cachedHorizontalSpacing = -1
  private var cachedSuggestedRows: List<Int> = emptyList()
  private var cachedItemWidths: List<Int> = emptyList()
  private var cachedRows: List<Int> = emptyList()

  /**
   * 返回每个 Item 对应的行号。
   *
   * 容器边界未改变时从基准行开始重新尝试：增长项仅在本行溢出时独自后移，缩短后可自动返回；
   * 其他 Item 不会因为相邻文案变化而跟随换行。
   */
  fun rowsFor(
    itemWidths: List<Int>,
    suggestedRows: List<Int>,
    horizontalSpacing: Int,
    maxWidth: Int,
  ): List<Int> {
    require(itemWidths.all { it >= 0 }) { "itemWidths must not contain negative values" }
    require(suggestedRows.size == itemWidths.size) { "suggestedRows must match itemWidths" }
    require(suggestedRows.all { it >= 0 }) { "suggestedRows must not contain negative values" }
    require(horizontalSpacing >= 0) { "horizontalSpacing must not be negative" }
    require(maxWidth >= 0) { "maxWidth must not be negative" }
    val needsRecalculate = cachedMaxWidth != maxWidth ||
        cachedItemCount != itemWidths.size ||
        cachedHorizontalSpacing != horizontalSpacing ||
        cachedSuggestedRows != suggestedRows
    if (needsRecalculate) {
      var row = 0
      var rowWidth = 0L
      var rowHasItem = false
      cachedRows = itemWidths.mapIndexed { index, itemWidth ->
        val suggestedRow = suggestedRows[index]
        if (row < suggestedRow) {
          row = suggestedRow
          rowWidth = 0L
          rowHasItem = false
        }
        val width = itemWidth.coerceAtMost(maxWidth).toLong()
        val nextWidth = if (rowHasItem) {
          rowWidth + horizontalSpacing + width
        } else {
          width
        }
        if (rowHasItem && nextWidth > maxWidth.toLong()) {
          row++
          rowWidth = width
        } else {
          rowWidth = nextWidth
        }
        rowHasItem = true
        row
      }
      cachedMaxWidth = maxWidth
      cachedItemCount = itemWidths.size
      cachedHorizontalSpacing = horizontalSpacing
      cachedSuggestedRows = suggestedRows
      cachedItemWidths = itemWidths
      return cachedRows
    }

    val result = cachedRows.toMutableList()

    fun rowWidth(row: Int, excludedIndex: Int? = null): Long {
      val widths = result.indices.filter { index ->
        index != excludedIndex && result[index] == row
      }.map { itemWidths[it].coerceAtMost(maxWidth).toLong() }
      return widths.sum() + horizontalSpacing.toLong() * (widths.size - 1).coerceAtLeast(0)
    }

    itemWidths.indices
      .filter { index -> itemWidths[index] > cachedItemWidths[index] }
      .forEach { index ->
        val baseRow = cachedRows[index]
        if (rowWidth(baseRow) <= maxWidth.toLong()) return@forEach

        // 只移动发生增长的当前项；其他项始终保留在基准行，避免连续级联换行。
        result[index] = -1
        val itemWidth = itemWidths[index].coerceAtMost(maxWidth).toLong()
        val lastExistingRow = result.max()
        val targetRow = (baseRow + 1..lastExistingRow).firstOrNull { row ->
          val occupiedWidth = rowWidth(row)
          val spacing = if (occupiedWidth == 0L) 0 else horizontalSpacing
          occupiedWidth + spacing + itemWidth <= maxWidth.toLong()
        } ?: (lastExistingRow + 1)
        result[index] = targetRow
      }
    return result
  }
}

/** 信息项声明的建议行；父布局只能将其向后移动，不能利用前面空位提前。 */
private data class InfoRowParentData(val suggestedRow: Int)

/** 把信息项的建议行传递给 [StableInfoFlowRow]。 */
private data class InfoRowSuggestedRowModifier(
  val suggestedRow: Int,
) : ParentDataModifier {
  override fun Density.modifyParentData(parentData: Any?): Any = InfoRowParentData(suggestedRow)
}

private fun Modifier.infoRowSuggestedRow(row: Int): Modifier =
  this.then(InfoRowSuggestedRowModifier(row))

private data class InfoTextSegment(
  val id: String,
  /** 建议展示行；空间不足时允许后移，但不会自动提前到更靠前的行。 */
  val suggestedRow: Int,
  val text: String?,
  val icon: ImageVector,
  val color: Color,
  val suffix: String? = null,
  val suffixColor: Color = color,
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
        modifier = Modifier.size(13.dp),
        // InlineTextContent 不会自动继承外层 AnnotatedString 的 SpanStyle，需显式同步文字颜色。
        tint = color,
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
    val mainEnd = length
    onClick?.let { onClick ->
      addLink(
        LinkAnnotation.Clickable(
          tag = id,
          styles = TextLinkStyles(style = SpanStyle(color = color)),
        ) {
          onClick()
        },
        start,
        mainEnd,
      )
    }
    suffix?.let { suffix ->
      val suffixStart = length
      append(suffix)
      addStyle(SpanStyle(color = suffixColor), suffixStart, length)
      onClick?.let { onClick ->
        addLink(
          LinkAnnotation.Clickable(
            tag = "${id}_suffix",
            styles = TextLinkStyles(style = SpanStyle(color = suffixColor)),
          ) { onClick() },
          suffixStart,
          length,
        )
      }
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
  showThisAndFollowing: Boolean,
  onDismiss: () -> Unit,
  onChoose: (EditScope) -> Unit,
) {
  if (!show) return
  val colors = LocalAppColors.current
  // 调用入口已提供 Window；范围选择只叠加新的 BottomSheet，避免创建第二个窗口改变层级。
  ScheduleBottomSheet(show = true, onDismiss = onDismiss) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
      Text(
        text = if (isDelete) "删除重复日程" else "保存对重复日程的修改",
        fontSize = 14.sp, color = colors.tvLv3.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth().padding(20.dp),
      )
      ScopeRow(if (isDelete) "仅删除此次" else "仅此次") { onChoose(EditScope.THIS_ONLY) }
      if (showThisAndFollowing) {
        ScopeRow(if (isDelete) "删除此次及以后" else "此次及以后") {
          onChoose(EditScope.THIS_AND_FOLLOWING)
        }
      }
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
