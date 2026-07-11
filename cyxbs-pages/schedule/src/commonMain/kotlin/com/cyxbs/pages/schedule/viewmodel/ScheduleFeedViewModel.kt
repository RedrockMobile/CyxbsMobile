package com.cyxbs.pages.schedule.viewmodel

import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.schedule.api.ScheduleMainNavArgument
import com.cyxbs.pages.schedule.data.model.ScheduleEntity
import com.cyxbs.pages.schedule.data.model.ScheduleMutations
import com.cyxbs.pages.schedule.data.model.ScheduleOccurrences
import com.cyxbs.pages.schedule.data.repository.ScheduleSyncRepository
import com.cyxbs.pages.schedule.recurrence.OccurrenceStatus
import com.cyxbs.pages.schedule.ui.feed.ScheduleFeedItemUi
import com.cyxbs.pages.schedule.ui.feed.ScheduleFeedUiState
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * 邮子清单 feed ViewModel（commonMain 直接实现，无需 expect/actual）。
 *
 * - [refresh]：加载本地快照 + 后台同步
 * - [onCardClick]：跳邮子清单主页
 * - [onItemClick]：跳详情页
 * - [onItemCheck]：按重复提醒规则推进或删除
 *
 * @author 985892345
 */
class ScheduleFeedViewModel : BaseViewModel() {

  private val repository = ScheduleSyncRepository()

  private val _uiState = MutableStateFlow<ScheduleFeedUiState>(ScheduleFeedUiState.Empty)
  val uiState: StateFlow<ScheduleFeedUiState> = _uiState.asStateFlow()

  init {
    // 订阅仓库的 todos，自动刷新 UI 状态
    launchByViewModelScope {
      repository.todos.collect { todos ->
        updateList(todos)
      }
    }
  }

  /** 拉取/刷新待办列表（对齐旧 ScheduleFeedFragment.onResume 的刷新时机） */
  fun refresh() {
    launchByViewModelScope {
      repository.initialize()
    }
  }

  /** 点击整张卡片：跳转邮子清单主页 */
  fun onCardClick() {
    ScheduleMainNavArgument.navigate()
  }

  /** 点击某条待办标题：跳转邮子清单主页（详情页已并入主页底部弹窗，列表内点击该条即可编辑） */
  fun onItemClick(id: Long) {
    ScheduleMainNavArgument.navigate()
  }

  /** 勾选某条待办完成；重复型必须携带该次的原始 occurrence 锚点。 */
  fun onItemCheck(id: Long, recurrenceId: Date?) {
    launchByViewModelScope {
      repository.completeSchedule(id, recurrenceId)
    }
  }

  /** 过滤未完成、非新手教程项（todoId > 3）的前 3 条，映射成 UI 状态。 */
  private fun updateList(todos: List<ScheduleEntity>) {
    val today = Date.now()
    val visible = todos.asSequence()
      .filter { it.isDone == 0 && it.todoId > 3 }
      .mapNotNull { todo ->
        if (!ScheduleMutations.isRecurring(todo)) {
          todo.toFeedItemUi()
        } else {
          ScheduleOccurrences.expandInRange(todo, today, today.plusYears(1))
            .firstOrNull { it.status != OccurrenceStatus.COMPLETED }
            ?.let { todo.toFeedItemUi(it.recurrenceId) }
        }
      }
      .take(3)
      .toList()
    _uiState.value = if (visible.isEmpty()) {
      ScheduleFeedUiState.Empty
    } else {
      ScheduleFeedUiState.Data(visible)
    }
  }
}

/**
 * 把 [ScheduleEntity] 映射成 feed UI 模型，时间文案与超时判断逻辑搬自旧
 * androidMain `Schedule.toFeedItemUi()`。
 */
private fun ScheduleEntity.toFeedItemUi(recurrenceId: Date? = null): ScheduleFeedItemUi {
  val now = Clock.System.now().toEpochMilliseconds()
  val notify = remindMode.notifyDateTime
  // endTime 与 notifyDateTime 同时为空串则不展示时间行
  val hideTime = endTime.isNullOrEmpty() && notify.isNullOrEmpty()
  val timeText = if (hideTime) {
    null
  } else {
    val raw = notify?.takeIf { it.isNotBlank() } ?: endTime
    raw?.replace("日", "日  ")?.takeIf { it.isNotBlank() }
  }
  // 超时判断：endTime 优先，否则 notifyDateTime（解析失败按当前时间兜底，对齐旧逻辑）
  val itemTime = when {
    !endTime.isNullOrEmpty() -> parseFeedTimeOrNull(endTime) ?: now
    !notify.isNullOrEmpty() -> parseFeedTimeOrNull(notify) ?: now
    else -> 0L
  }
  val isOverTime = timeText != null && now > itemTime && itemTime != 0L
  return ScheduleFeedItemUi(
    id = todoId,
    recurrenceId = recurrenceId,
    title = title,
    timeText = timeText,
    isOverTime = isOverTime,
  )
}

/**
 * 解析 "yyyy年MM月dd日 HH:mm" 或 "yyyy年MM月dd日HH:mm" 格式，
 * 失败返回 null。
 */
private fun parseFeedTimeOrNull(text: String): Long? {
  val regex = Regex("""(\d{4})年(\d{1,2})月(\d{1,2})日\s*(\d{1,2}):(\d{2})""")
  val match = regex.matchEntire(text.trim()) ?: return null
  val (year, month, day, hour, minute) = match.destructured
  return try {
    val dt = kotlinx.datetime.LocalDateTime(
      year.toInt(), month.toInt(), day.toInt(), hour.toInt(), minute.toInt(),
    )
    dt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
  } catch (e: Exception) {
    null
  }
}
