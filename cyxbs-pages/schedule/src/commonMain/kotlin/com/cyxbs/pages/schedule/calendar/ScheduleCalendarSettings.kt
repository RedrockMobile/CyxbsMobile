package com.cyxbs.pages.schedule.calendar

import com.cyxbs.components.config.sp.AccountSettings

/**
 * Schedule 系统日历导出的账号级本地设置。
 *
 * 自动导入开关和 `projectionKey → eventId` 映射都存入 [AccountSettings]，避免不同学号互相更新或
 * 删除系统事件。这里只保存 Schedule 自己创建过的 eventId；“清理已导入”据此精确删除，不能按日历
 * 账户全量删除，否则可能误伤同账户下其他业务写入的事件。
 */
internal object ScheduleCalendarSettings {

  private const val AUTO_IMPORT = "schedule_calendar_auto_import"
  private const val EVENT_PREFIX = "schedule_calendar_event_"

  /** 用户是否允许数据变化后自动对账系统日历；默认关闭，授权成功后才可开启。 */
  var autoImport: Boolean
    get() = AccountSettings.now.getBoolean(AUTO_IMPORT, false)
    set(value) = AccountSettings.now.putBoolean(AUTO_IMPORT, value)

  fun getEventId(projectionKey: String): Long? =
    AccountSettings.now.getLongOrNull(EVENT_PREFIX + projectionKey)

  /** 记录一次成功创建/恢复后的系统 eventId，后续更新和删除均以此为索引。 */
  fun putEventId(projectionKey: String, eventId: Long) {
    AccountSettings.now.putLong(EVENT_PREFIX + projectionKey, eventId)
  }

  fun removeEventId(projectionKey: String) {
    AccountSettings.now.remove(EVENT_PREFIX + projectionKey)
  }

  /** 扫描当前账号的全部映射；供 reconcile 计算目标与存量事件的差集。 */
  fun allEventMappings(): Map<String, Long> = AccountSettings.now.keys
    .asSequence()
    .filter { it.startsWith(EVENT_PREFIX) }
    .mapNotNull { storageKey ->
      AccountSettings.now.getLongOrNull(storageKey)?.let {
        storageKey.removePrefix(EVENT_PREFIX) to it
      }
    }.toMap()
}
