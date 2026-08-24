package com.cyxbs.pages.schedule.ui.course

import com.cyxbs.pages.schedule.domain.model.ScheduleId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 清单到课表的进程内关联状态。
 *
 * 关联以系列 [ScheduleId] 为单位，同一重复系列的新实例会自动沿用选择。状态按账号隔离，但不会写入
 * Settings、数据库或网络；进程重启后自然清空，后续课表事务与清单融合时可直接移除此临时边界。
 */
internal object ScheduleCourseProjectionMemory {

  internal data class State(
    val accountId: String? = null,
    val scheduleIds: Set<ScheduleId> = emptySet(),
  )

  private val mutableState = MutableStateFlow(State())
  val state = mutableState.asStateFlow()

  /**
   * 切换指定系列的课表投射状态。
   *
   * @return 切换后是否处于已关联状态；[accountId] 变化时会先丢弃上一账号的临时选择。
   */
  fun toggle(accountId: String, scheduleId: ScheduleId): Boolean {
    var selected = false
    mutableState.update { old ->
      val ids = old.scheduleIds.takeIf { old.accountId == accountId }.orEmpty()
      selected = scheduleId !in ids
      State(
        accountId = accountId,
        scheduleIds = if (selected) ids + scheduleId else ids - scheduleId,
      )
    }
    return selected
  }
}
