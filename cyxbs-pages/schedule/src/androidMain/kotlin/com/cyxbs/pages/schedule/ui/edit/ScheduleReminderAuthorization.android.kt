package com.cyxbs.pages.schedule.ui.edit

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.schedule.calendar.ScheduleCalendarExportController
import com.cyxbs.pages.schedule.data.repository.v2.ScheduleRepositoryProvider
import com.cyxbs.pages.schedule.ui.dialog.ScheduleConfirmDialog

private val CalendarReminderPermissions = arrayOf(
  Manifest.permission.READ_CALENDAR,
  Manifest.permission.WRITE_CALENDAR,
)

/** Android 端复用系统日历读写权限，并在授权成功后立即恢复当前账号的单向导出。 */
@Composable
internal actual fun rememberScheduleReminderAuthorization(
  onResult: (Boolean) -> Unit,
): ScheduleReminderAuthorization {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val accountService = IAccountService::class.impl()
  val session = accountService.session.collectAsState().value.takeIf { it.accountId != null }
  val currentSession by rememberUpdatedState(session)
  val currentOnResult by rememberUpdatedState(onResult)
  var requestedSession by remember { mutableStateOf<AccountSession?>(null) }
  var settingsSession by remember { mutableStateOf<AccountSession?>(null) }
  var showSettingsGuidance by remember { mutableStateOf(false) }
  var authorized by remember(session) {
    mutableStateOf(hasCalendarReminderPermissions(context))
  }

  /** 权限存在时同时开启导出，保证“授权成功”确实能够在系统日历中投递提醒。 */
  fun enableForSession(exactSession: AccountSession) {
    if (currentSession !== exactSession) return
    authorized = true
    ScheduleCalendarExportController.enable(
      context = context,
      repository = ScheduleRepositoryProvider.repository,
      session = exactSession,
    )
    currentOnResult(true)
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions(),
  ) { result ->
    val exactSession = requestedSession.also { requestedSession = null }
      ?: return@rememberLauncherForActivityResult
    if (currentSession !== exactSession) return@rememberLauncherForActivityResult
    val granted = CalendarReminderPermissions.all { permission ->
      result[permission] == true ||
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }
    authorized = granted
    if (granted) {
      enableForSession(exactSession)
    } else {
      ScheduleCalendarExportController.disable(exactSession)
      if (requiresApplicationSettings(context)) showSettingsGuidance = true
      else currentOnResult(false)
    }
  }

  /** 从系统设置返回时复查权限；仅处理本组件主动打开设置页对应的一次返回。 */
  DisposableEffect(lifecycleOwner, session) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        val granted = hasCalendarReminderPermissions(context)
        authorized = granted
        val exactSession = settingsSession.also { settingsSession = null }
        if (exactSession != null && currentSession === exactSession) {
          if (granted) {
            enableForSession(exactSession)
          } else {
            ScheduleCalendarExportController.disable(exactSession)
            currentOnResult(false)
          }
        }
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  ScheduleConfirmDialog(
    show = showSettingsGuidance,
    title = "需要日历权限",
    message = "系统已不再弹出日历权限请求，请前往应用设置，为掌邮单独开启日历权限。",
    confirmText = "去设置",
    dismissText = "暂不使用",
    onConfirm = {
      showSettingsGuidance = false
      val exactSession = currentSession
      if (exactSession == null) {
        currentOnResult(false)
      } else {
        settingsSession = exactSession
        context.startActivity(
          Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
          ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
      }
    },
    onDismiss = {
      // ScheduleConfirmDialog 确认后还会调用 onDismiss，先关闭状态可区分“去设置”和真正取消。
      if (showSettingsGuidance) {
        showSettingsGuidance = false
        currentOnResult(false)
      }
    },
  )

  return ScheduleReminderAuthorization(
    authorized = authorized,
    requestAuthorization = {
      val exactSession = currentSession
      if (exactSession == null) {
        currentOnResult(false)
      } else if (hasCalendarReminderPermissions(context)) {
        enableForSession(exactSession)
      } else {
        requestedSession = exactSession
        permissionLauncher.launch(CalendarReminderPermissions)
      }
    },
  )
}

/** 提醒依赖对受管日历的读写，两项权限缺一不可。 */
private fun hasCalendarReminderPermissions(context: Context): Boolean =
  CalendarReminderPermissions.all { permission ->
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
  }

/**
 * 仅在一次系统权限请求已经返回拒绝后调用。
 *
 * 此时缺失权限且系统不再给出 rationale，表示再次 request 不会出现权限框，应改为引导应用设置。
 */
private fun requiresApplicationSettings(context: Context): Boolean {
  val activity = context.findActivity() ?: return true
  return CalendarReminderPermissions
    .filter { permission ->
      context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
    }
    .any { permission -> !activity.shouldShowRequestPermissionRationale(permission) }
}

/** Dialog/主题包装可能改变 LocalContext，沿 ContextWrapper 找到真正承载权限请求的 Activity。 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
