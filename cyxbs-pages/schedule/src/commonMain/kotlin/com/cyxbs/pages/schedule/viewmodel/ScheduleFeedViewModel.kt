package com.cyxbs.pages.schedule.viewmodel

import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.time.toLocalDate
import com.cyxbs.components.config.time.toLocalDateTime
import com.cyxbs.components.config.time.toMinuteTimeDate
import com.cyxbs.pages.schedule.api.ScheduleTodoNavArgument
import com.cyxbs.pages.schedule.data.repository.v2.ScheduleRepositoryProvider
import com.cyxbs.pages.schedule.domain.model.*
import com.cyxbs.pages.schedule.domain.repository.*
import com.cyxbs.pages.schedule.ui.feed.*
import com.cyxbs.pages.schedule.ui.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.*
import kotlin.time.Clock

/**
 * Feed 卡片的状态与命令持有者，与主页面、课表观察同一个 Schedule v2 快照。
 * 只展开从当前时刻起一年的有限窗口并最多展示三项，避免 Feed 为摘要场景进行无界重复展开。
 */
class ScheduleFeedViewModel(
  private val repository: ScheduleRepository = ScheduleRepositoryProvider.repository,
  private val clock: Clock = ScheduleRepositoryProvider.clock,
) : BaseViewModel() {
  private val _uiState = MutableStateFlow<ScheduleFeedUiState>(ScheduleFeedUiState.Loading)
  val uiState: StateFlow<ScheduleFeedUiState> = _uiState.asStateFlow()

  init { launchByViewModelScope { repository.snapshot.collect(::updateList) } }
  fun refresh() = launchByViewModelScope { repository.initialize() }
  /** 从首页摘要进入独立邮子清单，不再跳转到课表时间轴页面。 */
  fun onCardClick() { ScheduleTodoNavArgument().navigate() }
  /** 从摘要打开清单并定位精确实例；清单消费 identity 后展示共享的日程编辑底部弹窗。 */
  fun onItemClick(id: ScheduleId, recurrenceId: RecurrenceId?) {
    ScheduleTodoNavArgument(scheduleId = id, recurrenceId = recurrenceId).navigate()
  }

  /**
   * 在 ViewModel scope 中完成被点击的精确实例。重复项按 recurrenceId 写完成例外，并保留既有移动/覆盖 patch；
   * 非重复项才写系列完成命令，不会通过删除或“推进到下一次”改变重复规则。
   */
  fun onItemCheck(id: ScheduleId, recurrenceId: RecurrenceId?) = launchByViewModelScope {
    if (recurrenceId == null) repository.execute(ScheduleCommand.CompleteNonRepeating(id, true))
    else {
      val now = clock.now()
      val existing = repository.snapshot.value.exceptions.firstOrNull {
        it.scheduleId == id && it.recurrenceId == recurrenceId
      }
      repository.execute(ScheduleCommand.UpsertOccurrenceException(
        existing?.copy(status = OccurrenceStatus.COMPLETED, updatedAt = now)
          ?: ScheduleOccurrenceException(
            id, recurrenceId, 0, OccurrenceStatus.COMPLETED, null, now, now,
          )
      ))
    }
  }

  /**
   * 把仓库快照映射为 Feed：先展开当前至一年后目标日期次日零点的本地半开窗口，仅保留 ACTIVE，再按
   * 可执行时间排序。该结束边界保留“直到一年后完整自然日”的既有语义；有明确时间的排在未排期之前，
   * 最后截取三项并一次性发布状态。
   */
  private fun updateList(snapshot: ScheduleSnapshot) {
    val zone = TimeZone.currentSystemDefault()
    val now = clock.now()
    val start = now.toLocalDateTime(zone).toMinuteTimeDate()
    val endExclusive = com.cyxbs.components.config.time.MinuteTimeDate(
      start.date.plusYears(1).plusDays(1),
      0,
      0,
    )
    val visible = snapshot.occurrencesInRange(start, endExclusive, includeUnscheduled = true)
      .asSequence()
      .filter { it.status == OccurrenceStatus.ACTIVE }
      .sortedWith(compareBy<ScheduleUiOccurrence> { it.sortInstant(zone) == null }
        .thenBy { it.sortInstant(zone) }.thenBy { it.scheduleId.value }.thenBy { it.recurrenceId?.originalDateTime })
      .take(3)
      .map { it.toFeedItem(now, zone) }
      .toList()
    _uiState.value = if (visible.isEmpty()) ScheduleFeedUiState.Empty else ScheduleFeedUiState.Data(visible)
  }
}

/** 将四态 timing 转为排序 Instant；全天按查看者时区零点，未排期返回 null 并由调用方排到末尾。 */
private fun ScheduleUiOccurrence.sortInstant(viewerZone: TimeZone) = when (val value = timing) {
  is ScheduleTiming.Timed -> value.start.toLocalDateTime().toInstant(TimeZone.of(value.timeZoneId))
  is ScheduleTiming.Deadline -> value.due.toLocalDateTime().toInstant(TimeZone.of(value.timeZoneId))
  is ScheduleTiming.AllDay -> value.startDate.toLocalDate().atStartOfDayIn(viewerZone)
  ScheduleTiming.Unscheduled -> null
}

/** 映射 Feed 轻量模型；保留系列与实例双重 identity，过期样式复用统一四态边界判断。 */
private fun ScheduleUiOccurrence.toFeedItem(now: kotlin.time.Instant, zone: TimeZone) = ScheduleFeedItemUi(
  id = scheduleId,
  recurrenceId = recurrenceId,
  title = title,
  timeText = when (val value = timing) {
    is ScheduleTiming.Timed -> value.start.toString()
    is ScheduleTiming.Deadline -> value.due.toString()
    is ScheduleTiming.AllDay -> value.startDate.toString()
    ScheduleTiming.Unscheduled -> null
  },
  isOverTime = isExpired(now, zone),
)
