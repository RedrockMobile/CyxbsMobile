package com.cyxbs.pages.schedule.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.schedule.calendar.IosEventKitFullAccessGateway
import com.cyxbs.pages.schedule.calendar.IosScheduleCalendarSettingsController
import com.cyxbs.pages.schedule.calendar.IosScheduleCalendarSettingsState
import com.cyxbs.pages.schedule.calendar.IosScheduleCalendarSettingsStatus

/**
 * iOS 的显式系统日历设置入口。
 *
 * 此 UI 只触发用户点击后的 full-access 请求与 source 选择；不创建 calendar、不运行 reconcile/CRUD，也不接入
 * initializer、repository factory 或后台 runtime。关闭仅停止未来 intent，既有系统日历数据保持不变。
 */
@Composable
internal actual fun ScheduleCalendarExportSetting(modifier: Modifier) {
  val accountService = IAccountService::class.impl()
  val session = accountService.session.collectAsState().value
  val controller = remember(accountService) {
    IosScheduleCalendarSettingsController(
      accountService = accountService,
      gatewayFactory = { scope ->
        // 构造 gateway 不会请求权限；只有下方用户点击会进入 requestFullAccess。
        IosEventKitFullAccessGateway(scope)
      },
    )
  }
  val state by controller.state.collectAsState()
  var sourcePickerOpen by remember(session) { mutableStateOf(false) }

  /** 每次 session identity 变化只做只读恢复；旧 session 的迟到结果由 controller gate 丢弃。 */
  LaunchedEffect(session) {
    sourcePickerOpen = false
    controller.refresh()
  }

  if (session.accountId != null) {
    Column(modifier) {
      ScheduleSettingSwitchRow(
        title = "同步到系统日历",
        summary = state.toUserSummary(),
        checked = state.exportEnabled,
        enabled = state.status != IosScheduleCalendarSettingsStatus.PERMISSION_REQUESTING,
        onCheckedChange = { enabled ->
          if (!enabled) {
            controller.disable()
          } else {
            when (state.status) {
              IosScheduleCalendarSettingsStatus.PERMISSION_NOT_REQUESTED,
              IosScheduleCalendarSettingsStatus.PERMISSION_DENIED,
              IosScheduleCalendarSettingsStatus.PERMISSION_RESTRICTED,
              IosScheduleCalendarSettingsStatus.PERMISSION_WRITE_ONLY,
              IosScheduleCalendarSettingsStatus.PERMISSION_CANCELLED -> controller.requestFullAccess()

              IosScheduleCalendarSettingsStatus.UNCONFIGURED,
              IosScheduleCalendarSettingsStatus.SOURCE_MISSING,
              IosScheduleCalendarSettingsStatus.CALENDAR_MISSING,
              IosScheduleCalendarSettingsStatus.CALENDAR_MOVED_TO_OTHER_SOURCE,
              IosScheduleCalendarSettingsStatus.AMBIGUOUS_REQUIRES_RESELECTION -> {
                sourcePickerOpen = true
                controller.refresh()
              }

              else -> Unit
            }
          }
        },
      )
      ScheduleSettingActionRow(
        title = "选择日历账户",
        summary = state.selectedSourceIdentifier?.let { "已选择一个日历账户，可重新选择" }
          ?: "选择用于以后创建“掌邮日程”日历的账户",
        enabled = state.status !in setOf(
          IosScheduleCalendarSettingsStatus.PERMISSION_NOT_REQUESTED,
          IosScheduleCalendarSettingsStatus.PERMISSION_REQUESTING,
          IosScheduleCalendarSettingsStatus.PERMISSION_DENIED,
          IosScheduleCalendarSettingsStatus.PERMISSION_RESTRICTED,
          IosScheduleCalendarSettingsStatus.PERMISSION_WRITE_ONLY,
          IosScheduleCalendarSettingsStatus.ACCOUNT_UNAVAILABLE,
        ),
        onClick = {
          // 此操作仅打开明确 picker 并刷新只读 source，不会申请权限或创建默认 calendar。
          sourcePickerOpen = true
          controller.refresh()
        },
      )
      if (sourcePickerOpen) {
        state.sources.forEach { source ->
          ScheduleSettingActionRow(
            title = source.displayName,
            summary = "选择此日历账户",
            onClick = {
              sourcePickerOpen = false
              controller.selectSource(source.identifier)
            },
          )
        }
      }
    }
  }
}

/** 将 controller 的 fail-closed 状态收窄为不泄露 identifier 或系统错误文本的恢复提示。 */
private fun IosScheduleCalendarSettingsState.toUserSummary(): String = when (status) {
  IosScheduleCalendarSettingsStatus.UNCONFIGURED -> "请选择日历账户后再开启；首次导出才会创建“掌邮日程”日历"
  IosScheduleCalendarSettingsStatus.PERMISSION_NOT_REQUESTED -> "需要完整日历权限，点击开启后才会请求"
  IosScheduleCalendarSettingsStatus.PERMISSION_REQUESTING -> "正在等待系统日历权限"
  IosScheduleCalendarSettingsStatus.PERMISSION_CANCELLED -> "已取消权限请求，可再次点击开启"
  IosScheduleCalendarSettingsStatus.PERMISSION_DENIED -> "未获得完整日历权限，可在系统设置中授权后重试"
  IosScheduleCalendarSettingsStatus.PERMISSION_RESTRICTED -> "此设备限制了完整日历权限"
  IosScheduleCalendarSettingsStatus.PERMISSION_WRITE_ONLY -> "仅写入权限无法安全恢复和管理日历，需要完整权限"
  IosScheduleCalendarSettingsStatus.SOURCE_MISSING -> "已选日历账户不可用，请重新选择"
  IosScheduleCalendarSettingsStatus.CALENDAR_MISSING -> "缓存的“掌邮日程”日历已不存在，请重新选择账户"
  IosScheduleCalendarSettingsStatus.CALENDAR_MOVED_TO_OTHER_SOURCE -> "缓存日历已移动到其他账户，请重新选择"
  IosScheduleCalendarSettingsStatus.AMBIGUOUS_REQUIRES_RESELECTION -> "无法安全确认日历归属，请重新选择日历账户"
  IosScheduleCalendarSettingsStatus.CONFIGURED -> "已开启；关闭后会保留系统日历中已有日程"
  IosScheduleCalendarSettingsStatus.ACCOUNT_UNAVAILABLE -> "登录后可配置系统日历导出"
}
