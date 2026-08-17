package com.cyxbs.pages.schedule.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.schedule.calendar.ScheduleCalendarExportController
import com.cyxbs.pages.schedule.calendar.ScheduleCalendarExportCoordinator
import com.cyxbs.pages.schedule.calendar.ScheduleCalendarExportCoordinatorProvider
import com.cyxbs.pages.schedule.calendar.ScheduleCalendarExportSettings
import com.cyxbs.pages.schedule.data.repository.v2.ScheduleRepositoryProvider
import com.cyxbs.pages.schedule.ui.dialog.ScheduleConfirmDialog

private enum class CalendarPermissionPurpose { ENABLE, DELETE }

/** Compose key 使用 AccountSession 对象 identity，阻止同账号重登复用旧权限回调。 */
private class AccountSessionIdentityKey(private val session: AccountSession?) {
  override fun equals(other: Any?) = other is AccountSessionIdentityKey && other.session === session

  override fun hashCode() = System.identityHashCode(session)
}

/** 权限请求冻结发起时的 exact session 与用户操作类型。 */
private data class CalendarPermissionRequest(
  val session: AccountSession,
  val purpose: CalendarPermissionPurpose,
)

/**
 * Android 账号级系统日历单向导出设置。
 *
 * 页面只暴露“Schedule 写入系统日历”的开关，以及用户明确确认后的受管日历清理；不再提供从系统日历刷新、
 * 冲突观察或任一方向优先的选择入口。
 */
@Composable
internal actual fun ScheduleCalendarExportSetting(modifier: Modifier) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val accountService = IAccountService::class.impl()
  val session = accountService.session.collectAsState().value.takeIf { it.accountId != null }
  val sessionIdentityKey = AccountSessionIdentityKey(session)
  val currentSession by rememberUpdatedState(session)
  val accountId = session?.accountId
  val initialEnabled = accountId?.let(ScheduleCalendarExportSettings::isEnabled) == true
  val enabledFlow = remember(sessionIdentityKey) {
    accountId?.let(ScheduleCalendarExportSettings::enabledFlow)
  }
  val enabledState = enabledFlow
    ?.collectAsState(ScheduleCalendarExportSettings.EnabledState(initialEnabled, revision = 0))?.value
  val persistedEnabled = enabledState?.enabled ?: false
  val exportScope = remember(sessionIdentityKey) {
    accountId?.let(ScheduleCalendarExportSettings::scopeForAccount)
  }
  val exportStatus = exportScope
    ?.let(ScheduleCalendarExportCoordinatorProvider::status)
    ?.collectAsState(ScheduleCalendarExportCoordinator.ExportStatus.Idle)?.value
  val permissions = remember {
    arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
  }

  fun hasPermissions(): Boolean = permissions.all {
    context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
  }

  var enabled by remember(sessionIdentityKey) {
    mutableStateOf(persistedEnabled && hasPermissions())
  }
  var message by remember(sessionIdentityKey) {
    mutableStateOf(
      if (initialEnabled && !hasPermissions()) "系统日历权限缺失，点击重新授权" else null,
    )
  }
  var deleteConfirmationSession by remember { mutableStateOf<AccountSession?>(null) }
  var permissionRequest by remember { mutableStateOf<CalendarPermissionRequest?>(null) }

  /** 持久化状态或授权状态变化后修正 UI，不保留乐观开关。 */
  LaunchedEffect(enabledState, sessionIdentityKey) {
    enabled = persistedEnabled && hasPermissions()
  }

  /** 账号代次变化时丢弃旧权限与删除确认上下文。 */
  LaunchedEffect(sessionIdentityKey) {
    permissionRequest = null
    deleteConfirmationSession = null
  }

  /** 当前 exact session 获得权限后开启并立即启动单向导出。 */
  fun enableForSession(requestSession: AccountSession) {
    enabled = true
    message = null
    ScheduleCalendarExportController.enable(
      context = context,
      repository = ScheduleRepositoryProvider.repository,
      session = requestSession,
    )
  }

  /** 当前 exact session 获得权限后删除受管 Calendar row。 */
  fun deleteForSession(requestSession: AccountSession) {
    enabled = false
    message = "已提交系统日历清理，自动导出已关闭"
    ScheduleCalendarExportController.clearAndDelete(context, requestSession)
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions(),
  ) { result ->
    val request = permissionRequest.also { permissionRequest = null }
      ?: return@rememberLauncherForActivityResult
    if (currentSession !== request.session) return@rememberLauncherForActivityResult
    val granted = permissions.all {
      result[it] == true || context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }
    if (!granted) {
      ScheduleCalendarExportController.disable(request.session)
      enabled = false
      message = if (request.purpose == CalendarPermissionPurpose.DELETE) {
        "未获得日历权限，自动导出已关闭，但系统日历尚未删除"
      } else {
        "系统日历权限缺失，点击重新授权"
      }
      return@rememberLauncherForActivityResult
    }
    when (request.purpose) {
      CalendarPermissionPurpose.ENABLE -> enableForSession(request.session)
      CalendarPermissionPurpose.DELETE -> deleteForSession(request.session)
    }
  }

  /** 应用回到前台时发现权限已被系统撤销，则停止 worker 并同步关闭开关。 */
  DisposableEffect(lifecycleOwner, sessionIdentityKey) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME && session != null && !hasPermissions()) {
        enabled = false
        message = "系统日历权限缺失，点击重新授权"
        ScheduleCalendarExportController.disable(session)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  if (session != null && accountId != null) {
    Column(modifier) {
      ScheduleSettingSwitchRow(
        title = "同步到系统日历",
        summary = message ?: exportStatus.toUserSummary(enabled),
        checked = enabled,
        enabled = true,
        onCheckedChange = { checked ->
          if (checked) {
            permissionRequest = CalendarPermissionRequest(session, CalendarPermissionPurpose.ENABLE)
            if (hasPermissions()) enableForSession(session) else permissionLauncher.launch(permissions)
          } else {
            enabled = false
            ScheduleCalendarExportController.disable(session)
          }
        },
      )
      ScheduleSettingActionRow(
        title = "清空并删除系统日历",
        summary = "删除当前学号的邮子清单日历及其中全部日程，并关闭自动导出",
        enabled = true,
        danger = true,
        onClick = { deleteConfirmationSession = session },
      )
    }

    ScheduleConfirmDialog(
      show = deleteConfirmationSession === session,
      title = "清空并删除系统日历？",
      message = "将清空当前学号的“邮子清单”日历及其中全部日程，同时关闭自动导出。" +
        "不会影响 Schedule 数据、其他日历或其他账号。",
      confirmText = "清空并删除",
      onConfirm = {
        val requestSession = deleteConfirmationSession?.takeIf { it === session }
          ?: return@ScheduleConfirmDialog
        deleteConfirmationSession = null
        permissionRequest = CalendarPermissionRequest(requestSession, CalendarPermissionPurpose.DELETE)
        if (hasPermissions()) deleteForSession(requestSession) else {
          ScheduleCalendarExportController.disable(requestSession)
          permissionLauncher.launch(permissions)
        }
      },
      onDismiss = {
        if (deleteConfirmationSession === session) deleteConfirmationSession = null
      },
    )
  }
}

/** 将最小单向导出状态映射为设置页摘要。 */
private fun ScheduleCalendarExportCoordinator.ExportStatus?.toUserSummary(enabled: Boolean): String? = when {
  !enabled -> "已关闭；系统日历中已有日程会保留"
  this == null || this is ScheduleCalendarExportCoordinator.ExportStatus.Idle -> "等待同步"
  this is ScheduleCalendarExportCoordinator.ExportStatus.Running -> "正在同步系统日历"
  this is ScheduleCalendarExportCoordinator.ExportStatus.Completed && stats.unsupported > 0 ->
    "部分日程未同步：${stats.unsupported} 项暂不支持"
  this is ScheduleCalendarExportCoordinator.ExportStatus.Completed -> "系统日历已同步"
  this is ScheduleCalendarExportCoordinator.ExportStatus.PartiallyFailed ->
    "部分日程同步失败：${stats.failures.size} 项失败，${stats.unsupported} 项暂不支持"
  this is ScheduleCalendarExportCoordinator.ExportStatus.CorruptedSnapshot ->
    "本地日程数据异常，已安全停止系统日历同步"
  this is ScheduleCalendarExportCoordinator.ExportStatus.Failed -> "系统日历同步失败"
  else -> null
}
