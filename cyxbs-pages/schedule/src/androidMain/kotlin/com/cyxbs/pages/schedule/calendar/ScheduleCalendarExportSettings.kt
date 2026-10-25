package com.cyxbs.pages.schedule.calendar

import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.pages.schedule.domain.calendar.CalendarExportScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Schedule 系统日历导出的账号级显式授权设置与稳定账号 scope。 */
object ScheduleCalendarExportSettings {
  private const val ENABLED_KEY = "schedule.calendar_export_enabled"
  /** 开关值附带单调修订号，使异步失败写回相同布尔值时也能通知乐观 UI。 */
  data class EnabledState(val enabled: Boolean, val revision: Long)

  private val enabledRegistry = MutableStateFlow<Map<String, EnabledState>>(emptyMap())

  /**
   * 返回账号开关流；所有账号共享单一 registry holder，账号 entry 被淘汰时 collector 仍能接收后续重登状态。
   */
  fun enabledFlow(accountId: String): Flow<EnabledState> = enabledRegistry
    .map { values -> values[accountId] ?: loadState(accountId) }
    .distinctUntilChanged()

  /** 只有用户明确开启成功后才返回 true；系统权限本身不代表同意。 */
  fun isEnabled(accountId: String): Boolean =
    enabledRegistry.value[accountId]?.enabled ?: loadState(accountId).enabled

  /** 持久化当前账号选择并同步通知 UI；关闭只停止后续同步，不静默删除已导出事件。 */
  fun setEnabled(accountId: String, enabled: Boolean) {
    AccountSettings.get(accountId).putBoolean(ENABLED_KEY, enabled)
    enabledRegistry.update { current ->
      val revision = current[accountId]?.revision ?: 0L
      current + (accountId to EnabledState(enabled, revision + 1))
    }
  }

  /** 账号退出后释放 registry 中的学号 key；持久化用户意图仍由 AccountSettings 保存。 */
  internal fun releaseObservableState(accountId: String) {
    enabledRegistry.update { current -> current - accountId }
  }

  private fun loadState(accountId: String): EnabledState =
    EnabledState(AccountSettings.get(accountId).getBoolean(ENABLED_KEY, false), revision = 0)

  /**
   * 直接使用学号作为稳定导出空间。
   *
   * scope 只是 Calendar Provider 事件的账号命名空间，不是授权凭据或 Deep Link token。使用学号可让卸载重装后
   * 重新认领同一日历内的既有事件，避免安装随机 secret 变化造成重复导出与历史数据无法清理。
   */
  fun scopeForAccount(accountId: String): CalendarExportScope {
    require(accountId.isNotBlank()) { "accountId must not be blank" }
    return CalendarExportScope(accountId.lowercase())
  }
}
