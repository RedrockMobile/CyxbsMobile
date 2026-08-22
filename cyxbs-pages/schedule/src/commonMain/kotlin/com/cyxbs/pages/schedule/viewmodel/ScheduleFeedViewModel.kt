package com.cyxbs.pages.schedule.viewmodel

import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.sp.accountSettings
import com.cyxbs.pages.schedule.api.ScheduleTodoNavArgument
import com.cyxbs.pages.schedule.data.repository.v2.ScheduleRepositoryProvider
import com.cyxbs.pages.schedule.domain.model.*
import com.cyxbs.pages.schedule.domain.repository.*
import com.cyxbs.pages.schedule.ui.edit.EditScope
import com.cyxbs.pages.schedule.ui.edit.applyScheduleDelete
import com.cyxbs.pages.schedule.ui.feed.*
import com.cyxbs.pages.schedule.ui.todo.loadScheduleTodoPinnedIds
import com.cyxbs.pages.schedule.ui.todo.saveScheduleTodoPinnedIds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.*
import kotlin.time.Clock

/**
 * Feed 卡片的状态与命令持有者，与主页面、课表观察同一个 Schedule v2 快照。
 * 复用邮子清单的有限窗口投影并最多展示三项，保证重复实例、临期与超期口径在两个入口一致。
 */
class ScheduleFeedViewModel(
  private val repository: ScheduleRepository = ScheduleRepositoryProvider.repository,
  private val clock: Clock = ScheduleRepositoryProvider.clock,
) : BaseViewModel() {
  private val _uiState = MutableStateFlow<ScheduleFeedUiState>(ScheduleFeedUiState.Loading)
  val uiState: StateFlow<ScheduleFeedUiState> = _uiState.asStateFlow()
  private var pinnedSettingsAccountId: String? = null
  private var pinnedIds: List<ScheduleId> = emptyList()

  init { launchByViewModelScope { repository.snapshot.collect(::updateList) } }
  fun refresh() = launchByViewModelScope { repository.initialize() }
  /** 从首页摘要进入独立邮子清单，不再跳转到课表时间轴页面。 */
  fun onCardClick() { ScheduleTodoNavArgument().navigate() }
  /** 从摘要打开清单并定位精确实例；清单消费 identity 后滚动并短暂高亮对应卡片。 */
  fun onItemClick(id: ScheduleId, recurrenceId: RecurrenceId?) {
    ScheduleTodoNavArgument(scheduleId = id, recurrenceId = recurrenceId).navigate()
  }

  /**
   * 切换首页事项的端上置顶状态并立即刷新 Feed 排序。
   *
   * 置顶顺序只写当前精确账号的 Settings，不进入仓库或网络请求；账号快照与 Settings 不一致时拒绝写入。
   */
  fun onTogglePin(id: ScheduleId) {
    val snapshot = repository.snapshot.value
    val settings = accountSettings
    if (snapshot.accountId == null || snapshot.accountId != settings.stuNum) return
    if (pinnedSettingsAccountId != settings.stuNum) {
      pinnedSettingsAccountId = settings.stuNum
      pinnedIds = loadScheduleTodoPinnedIds(settings)
    }
    pinnedIds = if (id in pinnedIds) {
      pinnedIds.filterNot { it == id }
    } else {
      listOf(id) + pinnedIds
    }
    saveScheduleTodoPinnedIds(settings, pinnedIds)
    updateList(snapshot)
  }

  /**
   * 删除 Feed 中的精确事项。
   *
   * 非重复事项删除整条日程；重复事项只删除当前 recurrence 实例，沿用清单详情页的范围路由与本地优先语义。
   */
  fun onDelete(id: ScheduleId, recurrenceId: RecurrenceId?) = launchByViewModelScope {
    repository.applyScheduleDelete(
      scheduleId = id,
      scope = if (recurrenceId == null) EditScope.ALL else EditScope.THIS_ONLY,
      recurrenceId = recurrenceId,
      clock = clock,
    )
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
   * 把仓库快照映射为 Feed。这里直接复用清单页投影，避免旧实现从“当前时刻”开始展开后遗漏已经
   * 超期但仍未完成的事项；横条计数基于完整未完成集合，卡片列表才截取前三项。
   */
  private fun updateList(snapshot: ScheduleSnapshot) {
    val settings = accountSettings
    if (snapshot.accountId != null && snapshot.accountId == settings.stuNum) {
      if (pinnedSettingsAccountId != settings.stuNum) {
        pinnedSettingsAccountId = settings.stuNum
        pinnedIds = loadScheduleTodoPinnedIds(settings)
      }
      if (snapshot.status !is ScheduleRepositoryStatus.Loading &&
        snapshot.status !is ScheduleRepositoryStatus.Corrupted
      ) {
        // 删除系列后同步清掉失效置顶项；重复实例删除仍保留该系列的置顶偏好。
        val existingIds = snapshot.schedules.mapTo(hashSetOf()) { it.id }
        val retainedIds = pinnedIds.filter { it in existingIds }
        if (retainedIds != pinnedIds) {
          pinnedIds = retainedIds
          saveScheduleTodoPinnedIds(settings, retainedIds)
        }
      }
    }
    val zone = TimeZone.currentSystemDefault()
    val now = clock.now()
    _uiState.value = projectScheduleFeed(snapshot, now, zone, pinnedIds)
  }
}
