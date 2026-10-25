package com.cyxbs.pages.schedule.ui.todo.main

import com.cyxbs.components.config.sp.AccountSettings

private const val SCHEDULE_TODO_VIEW_MODE_KEY = "schedule_todo_view_mode"

/** 清单页下半区的本地展示方式；该偏好按账号隔离，不进入 Schedule 协议。 */
internal enum class ScheduleTodoViewMode {
  LIST,
  TIMELINE,
}

/**
 * 恢复当前账号上次选择的清单展示方式。
 *
 * 未保存或遇到未来版本写入的未知值时回退到列表，保证升级、降级都不会阻断页面启动。
 */
internal fun loadScheduleTodoViewMode(settings: AccountSettings): ScheduleTodoViewMode =
  settings.getStringOrNull(SCHEDULE_TODO_VIEW_MODE_KEY)
    ?.let { value -> ScheduleTodoViewMode.entries.firstOrNull { it.name == value } }
    ?: ScheduleTodoViewMode.LIST

/** 立即保存当前账号的展示方式；调用方切换 UI 后无需等待页面退出。 */
internal fun saveScheduleTodoViewMode(
  settings: AccountSettings,
  mode: ScheduleTodoViewMode,
) {
  settings.putString(SCHEDULE_TODO_VIEW_MODE_KEY, mode.name)
}
