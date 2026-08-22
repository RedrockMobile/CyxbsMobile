package com.cyxbs.pages.schedule.ui.todo

import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.pages.schedule.domain.model.ScheduleId

private const val SCHEDULE_TODO_PINNED_IDS_KEY = "schedule_todo_pinned_ids"

/**
 * 从当前账号 Settings 恢复清单置顶顺序。
 *
 * 置顶是端上展示偏好，不会进入 Schedule v2 请求。旧值损坏时会移除该字段并回到空列表，避免非核心偏好
 * 阻断首页 Feed 或清单页面启动。
 */
internal fun loadScheduleTodoPinnedIds(settings: AccountSettings): List<ScheduleId> {
  val json = settings.getStringOrNull(SCHEDULE_TODO_PINNED_IDS_KEY) ?: return emptyList()
  return runCatching {
    defaultJson.decodeFromString<List<String>>(json)
      .map { requireNotNull(ScheduleId.parseOrNull(it)) }
      .distinct()
  }.onFailure {
    settings.remove(SCHEDULE_TODO_PINNED_IDS_KEY)
  }.getOrDefault(emptyList())
}

/**
 * 将清单置顶顺序立即写入当前账号 Settings。
 *
 * [pinnedIds] 为空时直接移除字段；该数据只供清单页与首页 Feed 排序，不参与任何网络请求。
 */
internal fun saveScheduleTodoPinnedIds(settings: AccountSettings, pinnedIds: List<ScheduleId>) {
  if (pinnedIds.isEmpty()) {
    settings.remove(SCHEDULE_TODO_PINNED_IDS_KEY)
    return
  }
  settings.putString(
    SCHEDULE_TODO_PINNED_IDS_KEY,
    defaultJson.encodeToString<List<String>>(pinnedIds.map { it.value }),
  )
}
