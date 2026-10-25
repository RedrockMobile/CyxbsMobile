package com.cyxbs.pages.schedule.ui.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * 当前设备的系统日历提醒授权入口。
 *
 * [authorized] 只表示当前平台已具备日历提醒所需权限；[requestAuthorization] 只能由用户明确操作触发，
 * 完成后通过回调报告本次授权是否成功。[overlayContent] 由调用方放到当前 Window 根节点，承载 Android
 * 永久拒绝后的设置引导，避免权限回调期间创建独立窗口导致外部 BottomSheet 宿主离场。
 */
@Immutable
internal data class ScheduleReminderAuthorization(
  val authorized: Boolean,
  val requestAuthorization: () -> Unit,
  val overlayContent: @Composable () -> Unit = {},
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
