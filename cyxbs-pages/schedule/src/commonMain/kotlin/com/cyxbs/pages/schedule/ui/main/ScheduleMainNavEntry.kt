package com.cyxbs.pages.schedule.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.components.config.time.TodayNoEffect
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_SCHEDULE_MAIN
import com.cyxbs.components.view.calendar.CalendarCompose
import com.cyxbs.components.view.calendar.layout.createCalendarContentOffsetMeasurePolicy
import com.cyxbs.components.view.calendar.state.rememberCalendarState
import com.cyxbs.components.view.ui.Window
import com.cyxbs.pages.schedule.api.ScheduleMainNavArgument
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrence
import com.cyxbs.pages.schedule.domain.recurrence.RecurrenceEngine
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryMutationMode
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryStatus
import com.cyxbs.pages.schedule.domain.repository.canSubmitScheduleMutation
import com.cyxbs.pages.schedule.ui.edit.EditScheduleDialog
import com.cyxbs.pages.schedule.ui.model.occurrencesInRange
import com.cyxbs.pages.schedule.ui.settings.ScheduleSettingsNavArgument
import com.cyxbs.pages.schedule.ui.timeline.HourHeight
import com.cyxbs.pages.schedule.ui.timeline.ScheduleTimelinePane
import com.cyxbs.pages.schedule.ui.timeline.timelineSchedulesForDate
import com.cyxbs.pages.schedule.viewmodel.ScheduleMainViewModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * 日程（邮子清单）主页面导航入口。
 *
 * 页面已整合周 Header、可折叠日历、按天时间轴以及统一的新建/查看/范围编辑弹窗；导航参数可携带
 * 系列 ID 与稳定 recurrence identity，待共享仓库快照就绪后直接定位对应实例。
 */
@AppNav(route = NAV_SCHEDULE_MAIN)
class ScheduleMainNavEntry : AppNavEntry<ScheduleMainNavArgument>() {

  override fun isNeedLogin(argument: ScheduleMainNavArgument): Boolean {
    return true // 日程页面需要登录
  }

  override fun getContentKey(argument: ScheduleMainNavArgument): String {
    return "schedule_main_singleton"
  }

  @Composable
  override fun Content(argument: ScheduleMainNavArgument) {
    val viewModel = viewModel { ScheduleMainViewModel() }
    SchedulePage(argument = argument, viewModel = viewModel)
  }
}

/**
 * 主页面编辑入口的展示门禁。
 *
 * 它与 ViewModel 共享账号能力规则：local-first 始终可编辑，read-only 始终关闭新建、深链和时间轴编辑；同步状态
 * 只用于展示健康度，不改变编辑能力。
 */
internal fun isScheduleMainEditorEnabled(
  mutationMode: ScheduleRepositoryMutationMode,
): Boolean = mutationMode.canSubmitScheduleMutation()

/**
 * 日程主页面：周 Header + 可展开折叠日历 + 按天时间轴 三层结构。
 *
 * [viewModel] 默认由正式导航入口提供；Desktop 开发预览可注入纯内存仓库，以展示真实页面而不污染账号数据库。
 */
@Composable
fun SchedulePage(
  argument: ScheduleMainNavArgument,
  viewModel: ScheduleMainViewModel,
) {
  val colors = LocalAppColors.current
  val snapshot by viewModel.snapshot.collectAsState()
  val editorEnabled = isScheduleMainEditorEnabled(viewModel.mutationMode)

  LaunchedEffect(Unit) {
    viewModel.initialize()
  }

  // 统一弹窗状态：schedule=null 表示新建；occurrence/recurrenceId 保留点击实例的覆盖值与稳定范围编辑锚点。
  var showEdit by remember { mutableStateOf(false) }
  var editingSchedule by remember { mutableStateOf<Schedule?>(null) }
  var editingOccurrence by remember { mutableStateOf<ScheduleOccurrence?>(null) }
  var editingRecurrenceId by remember { mutableStateOf<RecurrenceId?>(null) }
  var consumedDeepLink by remember(argument.scheduleId, argument.recurrenceId) { mutableStateOf(false) }

  /** 登出或切号进入 read-only 后立即撤销已打开的编辑会话，确认/删除回调仍由 ViewModel 再次复核。 */
  LaunchedEffect(editorEnabled) {
    if (!editorEnabled) showEdit = false
  }

  /**
   * 等待深链中的 UUIDv7 系列进入共享快照，再打开目标实例。
   *
   * 按完整 recurrence identity 直接定位目标，并在 engine 内先验证原规则生成性、例外关系和取消状态；
   * 不再构造可能为空、也可能排除 effective start 的伪窗口。无效或已取消的 identity 不打开弹窗，
   * consumedDeepLink 保证 snapshot 后续刷新不会重复处理同一导航。目标暂未同步到本地时保持等待，不产生写入副作用。
   */
  LaunchedEffect(argument.scheduleId, argument.recurrenceId, snapshot, editorEnabled) {
    if (!editorEnabled) return@LaunchedEffect
    val scheduleId = argument.scheduleId ?: return@LaunchedEffect
    if (consumedDeepLink) return@LaunchedEffect
    snapshot.schedules.firstOrNull { it.id == scheduleId }?.let { schedule ->
      val target = argument.recurrenceId
      val occurrence = target?.let {
        // 远端或手工构造的导航参数可能伪造 identity；定位失败只表示没有可打开的实例，不能让 UI 崩溃。
        runCatching {
          RecurrenceEngine.resolveOccurrenceByIdentity(
            schedule,
            snapshot.exceptions.filter { exception -> exception.scheduleId == scheduleId },
            it,
          )
        }.getOrNull()
      }
      if (target != null && occurrence == null) {
        // 取消、伪造或尚不存在的 identity 都不退化成“编辑整条系列”。
        consumedDeepLink = true
        return@LaunchedEffect
      }
      editingSchedule = schedule
      editingOccurrence = occurrence
      editingRecurrenceId = target
      showEdit = true
      consumedDeepLink = true
    }
  }

  val calendarState = rememberCalendarState(
    initialClickDate = TodayNoEffect,
    endDate = TodayNoEffect.plusYears(8).lastDate,
  )
  val clickDate = calendarState.clickDate

  // 日窗口严格覆盖当天 00:00 到次日 00:00，结束边界不属于当天。
  val visibleOccurrences = remember(snapshot, clickDate) {
    snapshot.occurrencesInRange(
      MinuteTimeDate(clickDate, 0, 0),
      MinuteTimeDate(clickDate.plusDays(1), 0, 0),
    )
  }
  val dayEvents = remember(visibleOccurrences, clickDate) {
    timelineSchedulesForDate(visibleOccurrences, clickDate)
  }

  val scrollState = rememberScrollState()
  val density = LocalDensity.current
  LaunchedEffect(Unit) {
    val hour = Clock.System.now()
      .toLocalDateTime(TimeZone.currentSystemDefault()).hour
    val target = with(density) { (HourHeight * (hour - 1).coerceAtLeast(0)).toPx() }
    scrollState.scrollTo(target.toInt())
  }

  Box(
    modifier = Modifier.fillMaxSize()
      .background(colors.bottomBg)
      .navigationBarsPadding()
      .clipToBounds(),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      ScheduleWeekHeader(
        clickDate = clickDate,
        onBack = { argument.popBackStack() },
        onBackToday = { calendarState.updateClickDate(TodayNoEffect) },
        onSettings = { ScheduleSettingsNavArgument.navigate() },
      )

      CalendarCompose(
        modifier = Modifier.weight(1f).background(colors.bottomBg),
        state = calendarState,
      ) {
        ScheduleTimelinePane(
          modifier = Modifier.layout(calendarState.createCalendarContentOffsetMeasurePolicy()),
          timed = dayEvents,
          scrollState = scrollState,
          // 点击某条日程：只在当前可写时打开统一编辑弹窗，并带上「点击那一天」用于重复系列三态。
          onScheduleClick = { entity ->
            if (!editorEnabled) return@ScheduleTimelinePane
            editingSchedule = snapshot.schedules.firstOrNull { it.id == entity.scheduleId }
            editingOccurrence = visibleOccurrences.firstOrNull {
              it.scheduleId == entity.scheduleId && it.recurrenceId == entity.recurrenceId
            }?.let { ui ->
              ScheduleOccurrence(
                ui.scheduleId, ui.recurrenceId, ui.timing, ui.title, ui.description,
                ui.categoryId, ui.reminders, ui.status, ui.isOverridden,
              )
            }
            editingRecurrenceId = entity.recurrenceId
            showEdit = true
          },
        )
      }
    }

    if (editorEnabled) {
      FloatingActionButton(
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(end = 24.dp, bottom = 36.dp),
        onClick = {
          editingSchedule = null
          editingOccurrence = null
          editingRecurrenceId = null
          showEdit = true
        },
        backgroundColor = colors.positive,
      ) {
        Icon(Icons.Default.Add, contentDescription = "添加日程", tint = MaterialTheme.colors.onPrimary)
      }
    }
  }

  // 无当前账号 delegate 时明确提示只读；local-first 保留状态展示与离线写入行为。
  SyncStateIndicator(snapshot.status, viewModel.mutationMode)

  // 统一编辑弹窗（新建 / 编辑，含重复规则编辑器与三态）；状态失去 Ready 后不再组合 callback。
  if (editorEnabled && showEdit) {
    val currentEditing = editingSchedule
    Window(dismissOnBackPress = null) {
      Box(modifier = Modifier.fillMaxSize()) {
        EditScheduleDialog(
          show = true,
          editSchedule = currentEditing,
          editOccurrence = editingOccurrence,
          recurrenceId = editingRecurrenceId,
          categoryRepository = viewModel.repository,
          onDismiss = { showEdit = false },
          onConfirm = { state, scope, newCategory ->
            viewModel.saveSchedule(state, scope, editingRecurrenceId, newCategory)
          },
          onDelete = if (currentEditing != null) {
            { scope -> viewModel.deleteScheduleScoped(currentEditing.id, scope, editingRecurrenceId) }
          } else null,
          onToggleCompleted = currentEditing?.takeIf { it.todoState != null }?.let { schedule ->
            { completed -> viewModel.completeSchedule(schedule.id, editingRecurrenceId, completed) }
          },
        )
      }
    }
  }
}

/**
 * 同步状态与账号只读说明。
 *
 * local-first 的同步错误不关闭本地写入；没有当前账号 delegate 时统一提示只读，不再根据 Ready 动态开关编辑入口。
 */
@Composable
private fun SyncStateIndicator(
  syncState: ScheduleRepositoryStatus,
  mutationMode: ScheduleRepositoryMutationMode,
) {
  if (mutationMode == ScheduleRepositoryMutationMode.READ_ONLY) {
    Surface(
      modifier = Modifier.fillMaxWidth(),
      color = MaterialTheme.colors.error.copy(alpha = 0.1f),
    ) {
      Text(
        text = "当前没有可编辑的登录账号，仅可查看日程。",
        modifier = Modifier.padding(8.dp),
        color = MaterialTheme.colors.error,
        style = MaterialTheme.typography.caption,
      )
    }
    return
  }

  when (syncState) {
    ScheduleRepositoryStatus.Loading -> {
      LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colors.primary,
      )
    }

    is ScheduleRepositoryStatus.Corrupted -> {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colors.error.copy(alpha = 0.1f),
      ) {
        Text(
          text = "同步失败: ${syncState.cause.message}",
          modifier = Modifier.padding(8.dp),
          color = MaterialTheme.colors.error,
          style = MaterialTheme.typography.caption,
        )
      }
    }

    else -> Unit
  }
}
