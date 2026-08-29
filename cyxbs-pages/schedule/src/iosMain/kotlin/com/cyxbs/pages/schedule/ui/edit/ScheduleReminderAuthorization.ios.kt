package com.cyxbs.pages.schedule.ui.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.schedule.calendar.IosEventKitFullAccessGateway
import com.cyxbs.pages.schedule.calendar.IosEventKitFullAccessStatus
import com.cyxbs.pages.schedule.calendar.IosEventKitPermissionResult
import com.cyxbs.pages.schedule.calendar.IosScheduleCalendarExportRuntimeRegistry
import com.cyxbs.pages.schedule.calendar.IosScheduleCalendarExportSettings
import kotlinx.coroutines.launch

/**
 * iOS 端请求 EventKit 完整权限。
 *
 * 已经选择过日历账户时会同步恢复导出；首次尚未选择账户时只完成权限授权，账户仍由现有设置页显式选择，
 * 避免在多个系统日历账户之间替用户猜测。
 */
@Composable
internal actual fun rememberScheduleReminderAuthorization(
  onResult: (Boolean) -> Unit,
): ScheduleReminderAuthorization {
  val accountService = IAccountService::class.impl()
  val session = accountService.session.collectAsState().value.takeIf { it.accountId != null }
  val currentSession by rememberUpdatedState(session)
  val currentOnResult by rememberUpdatedState(onResult)
  val gateway = remember(session) {
    session?.accountId?.let { accountId ->
      IosEventKitFullAccessGateway(IosScheduleCalendarExportSettings.scopeForAccount(accountId))
    }
  }
  var authorized by remember(session, gateway) {
    mutableStateOf(gateway?.fullAccessStatus() == IosEventKitFullAccessStatus.FULL_ACCESS)
  }

  /** 切换账号后重新读取权限；普通组合与恢复不会触发系统授权弹窗。 */
  LaunchedEffect(session, gateway) {
    authorized = gateway?.fullAccessStatus() == IosEventKitFullAccessStatus.FULL_ACCESS
  }

  return ScheduleReminderAuthorization(
    authorized = authorized,
    requestAuthorization = {
      val exactSession = currentSession
      val accountId = exactSession?.accountId
      val exactGateway = gateway
      val scope = exactSession?.let(accountService::accountCoroutineScopeFor)
      if (exactSession == null || accountId == null || exactGateway == null || scope == null) {
        currentOnResult(false)
      } else {
        scope.launch {
          val granted = when (exactGateway.fullAccessStatus()) {
            IosEventKitFullAccessStatus.FULL_ACCESS -> true
            else -> exactGateway.requestFullAccess() is IosEventKitPermissionResult.Granted
          }
          if (currentSession !== exactSession) return@launch
          authorized = granted
          if (granted) {
            val preference = IosScheduleCalendarExportSettings.get(accountId)
            if (preference.sourceIdentifier != null) {
              IosScheduleCalendarExportSettings.setEnabled(accountId, true)
              IosScheduleCalendarExportRuntimeRegistry.signal(exactSession)
            }
          }
          currentOnResult(granted)
        }
      }
    },
  )
}
