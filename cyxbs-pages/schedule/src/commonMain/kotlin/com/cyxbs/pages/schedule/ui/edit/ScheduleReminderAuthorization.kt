package com.cyxbs.pages.schedule.ui.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * 当前设备的系统日历提醒授权入口。
 *
 * [authorized] 只表示当前平台已具备日历提醒所需权限；[requestAuthorization] 只能由用户明确操作触发，
 * 完成后通过回调报告本次授权是否成功。平台实现可在权限已存在时顺便恢复当前账号的日历导出。
 */
@Immutable
internal data class ScheduleReminderAuthorization(
  val authorized: Boolean,
  val requestAuthorization: () -> Unit,
)

/**
 * 记住当前平台的提醒授权状态。
 *
 * [onResult] 仅对应一次用户主动发起的授权尝试；系统权限被外部撤销只更新 [ScheduleReminderAuthorization.authorized]，
 * 不会擅自改写正在编辑的提醒草稿。
 */
@Composable
internal expect fun rememberScheduleReminderAuthorization(
  onResult: (Boolean) -> Unit,
): ScheduleReminderAuthorization
