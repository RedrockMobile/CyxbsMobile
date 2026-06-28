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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.TodayNoEffect
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_SCHEDULE_MAIN
import com.cyxbs.components.view.calendar.CalendarCompose
import com.cyxbs.components.view.calendar.layout.createCalendarContentOffsetMeasurePolicy
import com.cyxbs.components.view.calendar.state.rememberCalendarState
import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.schedule.api.ScheduleMainNavArgument
import com.cyxbs.pages.schedule.data.model.ScheduleEntity
import com.cyxbs.pages.schedule.data.repository.ScheduleSyncState
import com.cyxbs.pages.schedule.ui.edit.EditScheduleDialog
import com.cyxbs.pages.schedule.ui.timeline.HourHeight
import com.cyxbs.pages.schedule.ui.timeline.ScheduleTimelinePane
import com.cyxbs.pages.schedule.ui.timeline.allDaySchedulesForDate
import com.cyxbs.pages.schedule.ui.timeline.fullDayBlocks
import com.cyxbs.pages.schedule.ui.timeline.timedSchedulesForDate
import com.cyxbs.pages.schedule.ui.timeline.unscheduledSchedules
import com.cyxbs.pages.schedule.viewmodel.ScheduleMainViewModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * 日程（邮子清单）主页面导航入口。
 *
 * 本阶段(4b)只做骨架：周 Header + 可展开折叠日历 + 按天时间轴；
 * 编辑/新增入口暂置空（toast 占位），完整编辑 UI 见阶段5（affair 风格 + 重复规则编辑器）。
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
    viewModel { ScheduleMainViewModel() }
    SchedulePage(argument = argument)
  }
}

/**
 * 日程主页面：周 Header + 可展开折叠日历 + 按天时间轴 三层结构。
 */
@Composable
fun SchedulePage(
  argument: ScheduleMainNavArgument,
) {
  val viewModel = viewModel(ScheduleMainViewModel::class)
  val colors = LocalAppColors.current
  val syncState by viewModel.syncState.collectAsState()
  val allSchedules by viewModel.allSchedules.collectAsState()

  LaunchedEffect(Unit) {
    viewModel.initialize()
  }

  // 统一编辑弹窗状态：editingEntity=null 表示新建；editingDate 为点击那一天（重复系列三态用）。
  var showEdit by remember { mutableStateOf(false) }
  var editingEntity by remember { mutableStateOf<ScheduleEntity?>(null) }
  var editingDate by remember { mutableStateOf<Date?>(null) }

  val calendarState = rememberCalendarState(
    initialClickDate = TodayNoEffect,
  )
  val clickDate = calendarState.clickDate

  // 当天事件 = 整日块（全天 + 未排期）+ 有时刻事件，一起进时间轴并排展示。
  val dayEvents = remember(allSchedules, clickDate) {
    val timed = timedSchedulesForDate(allSchedules, clickDate)
    val allDay = allDaySchedulesForDate(allSchedules, clickDate)
    val unscheduled = unscheduledSchedules(allSchedules)
    fullDayBlocks(allDay) + fullDayBlocks(unscheduled) + timed
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
      )

      CalendarCompose(
        modifier = Modifier.weight(1f).background(colors.bottomBg),
        state = calendarState,
      ) {
        ScheduleTimelinePane(
          modifier = Modifier.layout(calendarState.createCalendarContentOffsetMeasurePolicy()),
          timed = dayEvents,
          scrollState = scrollState,
          // 点击某条日程：打开统一编辑弹窗，并带上「点击那一天」用于重复系列三态。
          onScheduleClick = { entity ->
            editingEntity = entity
            editingDate = clickDate
            showEdit = true
          },
        )
      }
    }

    FloatingActionButton(
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(end = 24.dp, bottom = 36.dp),
      onClick = {
        editingEntity = null
        editingDate = null
        showEdit = true
      },
      backgroundColor = colors.positive,
    ) {
      Icon(Icons.Default.Add, contentDescription = "添加日程", tint = Color.White)
    }
  }

  // 同步状态提示
  SyncStateIndicator(syncState)

  // 统一编辑弹窗（新建 / 编辑，含重复规则编辑器与三态）
  val currentEditing = editingEntity
  EditScheduleDialog(
    show = showEdit,
    editSchedule = currentEditing,
    occurrenceDate = editingDate,
    onDismiss = { showEdit = false },
    onConfirm = { state, scope -> viewModel.saveSchedule(state, scope, editingDate) },
    onDelete = if (currentEditing != null) {
      { scope -> viewModel.deleteScheduleScoped(currentEditing.todoId, scope, editingDate) }
    } else null,
  )
}

/**
 * 同步状态指示器。
 */
@Composable
private fun SyncStateIndicator(syncState: ScheduleSyncState) {
  when (syncState) {
    is ScheduleSyncState.Syncing -> {
      LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colors.primary,
      )
    }

    is ScheduleSyncState.Error -> {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFFFEBEE),
      ) {
        Text(
          text = "同步失败: ${syncState.message}",
          modifier = Modifier.padding(8.dp),
          color = Color(0xFFC62828),
          style = MaterialTheme.typography.caption,
        )
      }
    }

    else -> {
      // Idle 或 Success，不显示
    }
  }
}
