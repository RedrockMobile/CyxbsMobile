package com.cyxbs.pages.mine.setting

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.components.config.service.startActivity
import com.cyxbs.components.init.appContext
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.utils.utils.config.PhoneCalendar
import com.cyxbs.components.view.ui.ChooseDialogCompose
import com.cyxbs.pages.course.api.ICourseCalendarReminderService
import com.cyxbs.pages.course.api.ICourseService
import com.cyxbs.pages.mine.page.security.activity.SecurityActivity
import com.cyxbs.pages.widget.api.IWidgetEntryService
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.CompletableDeferred

/** Android 设置能力实现，集中隔离系统 API 与尚未迁移的账号安全 Activity。 */
@ImplProvider(clazz = SettingPlatform::class)
object AndroidSettingPlatformImpl : SettingPlatform() {

  /** 日历提醒只属于 Android，不让公共列表或其他平台感知日历能力。 */
  override fun switchItems(): List<SettingItem.Switch> = super.switchItems() +
    SettingItem.Switch(
      key = "course_calendar_reminder",
      title = "上课前提醒我",
      readChecked = ::readCourseCalendarReminder,
      rememberOnCheckedChange = {
        val requestPermission = rememberCalendarPermissionRequest()
        remember(requestPermission) {
          { enabled ->
            if (!enabled || requestPermission()) writeCourseCalendarReminder(enabled)
          }
        }
      },
    )

  /** 读取当前账号的日历提醒状态，缺少服务或日历权限时保持关闭。 */
  private fun readCourseCalendarReminder(): Boolean =
    ICourseCalendarReminderService::class.implOrNull()?.isEnabled() == true

  /** 切换当前账号的日历同步意图；拒绝开启时不把界面显示成已开启。 */
  private fun writeCourseCalendarReminder(enabled: Boolean) {
    val service = ICourseCalendarReminderService::class.implOrNull() ?: return
    if (enabled) {
      if (!service.enable()) toast("课前提醒开启失败，请确认已登录并授予日历权限")
    } else {
      service.disable()
    }
  }

  /** 仅供此提醒开关申请日历权限；协程随条目离开组合取消，迟到的结果不能保存设置。 */
  @Composable
  private fun rememberCalendarPermissionRequest(): suspend () -> Boolean {
    val pendingResult = remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val launcher = rememberLauncherForActivityResult(
      ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
      val granted = CALENDAR_PERMISSIONS.all { permission ->
        result[permission] == true ||
          appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
      }
      pendingResult.value?.complete(granted)
      pendingResult.value = null
      if (!granted) toast("需要日历权限才能写入课程并提供课前提醒")
    }
    return remember(launcher) {
      suspend {
        if (CALENDAR_PERMISSIONS.all { appContext.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
          true
        } else {
          // 单个开关在等待期间不可重复点击；取消时释放等待者，不把旧授权结果交给新请求。
          check(pendingResult.value == null) { "Calendar permission request is already pending" }
          val result = CompletableDeferred<Boolean>()
          pendingResult.value = result
          try {
            launcher.launch(CALENDAR_PERMISSIONS)
            result.await()
          } finally {
            if (pendingResult.value === result) pendingResult.value = null
            result.cancel()
          }
        }
      }
    }
  }

  override fun actionItems(actions: SettingItemActions): List<SettingItem.Action> =
    super.actionItems(actions) + buildList {
      // 小组件入口由 widget 模块决定是否提供，设置页不依赖其具体实现。
      IWidgetEntryService::class.implOrNull()?.takeIf { it.isSettingEntryVisible }?.let { widget ->
        add(
          SettingItem.Action(
            key = "desktop_widget",
            title = "桌面小组件",
            rememberOnClick = { widget::navigateToWidgetPage },
          )
        )
      }
      add(
        SettingItem.Action(
          key = "clear_app_data",
          title = "清理软件数据",
          rememberOnClick = { rememberClearApplicationDataAction() },
        )
      )
    }

  override fun openAccountSecurity() {
    startActivity(SecurityActivity::class)
  }

  override val courseMaxWeek: Int
    get() = ICourseService.maxWeek

  /** 安卓独有的清除应用数据入口；成功会结束当前应用，失败由条目引导到系统详情页。 */
  private fun clearApplicationData(): Boolean {
    val manager = appContext.getSystemService(ActivityManager::class.java) ?: return false
    return manager.clearApplicationUserData()
  }

  /** 清理请求失败时打开 Android 系统应用详情页，公共设置协议无需暴露此能力。 */
  private fun openApplicationDetails() {
    val intent = Intent(
      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
      "package:${appContext.packageName}".toUri(),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    appContext.startActivity(intent)
  }

  /** 清理条目自行持有二次确认弹窗；取消后复位，不把破坏性操作状态放在公共页面。 */
  @Composable
  private fun rememberClearApplicationDataAction(): () -> Unit {
    val showDialog = remember { mutableStateOf(false) }
    var confirmArmed by remember { mutableStateOf(false) }
    val dismiss = {
      confirmArmed = false
      showDialog.value = false
    }
    ChooseDialogCompose(
      showState = showDialog,
      positiveBtnText = if (confirmArmed) "再次确定" else "确定",
      negativeBtnText = "取消",
      onDismissRequest = dismiss,
      onClickNegativeBtn = dismiss,
      onClickPositiveBtn = {
        if (!confirmArmed) {
          confirmArmed = true
          toast("请再次点击进行确定")
        } else if (!clearApplicationData()) {
          dismiss()
          toast("清理失败，请在应用详情页中手动清理数据")
          openApplicationDetails()
        }
      },
    ) {
      DialogMessage(
        title = "清理软件数据",
        content = "清理后将重新登录并还原所有本地设置，请慎重选择！",
      )
    }
    return remember {
      {
        confirmArmed = false
        showDialog.value = true
      }
    }
  }

  override fun setCourseMaxWeek(maxWeek: Int) {
    ICourseService.setMaxWeek(maxWeek)
  }

  override fun clearPlatformDataBeforeLogout() {
    ICourseCalendarReminderService::class.implOrNull()?.clearBeforeLogout()
    PhoneCalendar.getCalendarAccount()?.let(PhoneCalendar::deleteCalendarAccount)
  }

  private val CALENDAR_PERMISSIONS = arrayOf(
    Manifest.permission.READ_CALENDAR,
    Manifest.permission.WRITE_CALENDAR,
  )

}
