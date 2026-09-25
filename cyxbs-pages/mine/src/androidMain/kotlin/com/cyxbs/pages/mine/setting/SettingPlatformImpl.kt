package com.cyxbs.pages.mine.setting

import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.edit
import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.components.config.service.startActivity
import com.cyxbs.components.config.sp.SP_COURSE_SHOW_STATE
import com.cyxbs.components.config.sp.defaultSp
import com.cyxbs.components.init.appContext
import com.cyxbs.components.utils.utils.config.PhoneCalendar
import com.cyxbs.pages.course.api.ICourseService
import com.cyxbs.pages.mine.page.security.activity.SecurityActivity
import com.cyxbs.pages.widget.api.IWidgetEntryService
import com.g985892345.provider.api.annotation.ImplProvider
import com.mredrock.cyxbs.common.config.SP_WIDGET_NEED_FRESH
import com.mredrock.cyxbs.common.config.WIDGET_COURSE

/** Android 设置能力实现，集中隔离系统 API 与尚未迁移的账号安全 Activity。 */
@ImplProvider(clazz = SettingPlatform::class)
object SettingPlatformImpl : SettingPlatform {

  override fun settingItems(actions: SettingItemActions): List<SettingItem> = buildList {
    add(
      SettingItem.Switch(
        key = "show_course_first",
        title = "启动 App 优先显示课表",
        readChecked = { defaultSp.getBoolean(SP_COURSE_SHOW_STATE, false) },
        onCheckedChange = { enabled ->
          defaultSp.edit { putBoolean(SP_COURSE_SHOW_STATE, enabled) }
        },
      )
    )
    add(
      SettingItem.Action(
        key = "account_security",
        title = "账号与安全",
        requiresLogin = true,
        onClick = { startActivity(SecurityActivity::class) },
      )
    )
    // 小组件入口由 widget 模块决定是否提供，设置页不依赖其具体实现。
    IWidgetEntryService::class.implOrNull()?.takeIf { it.isSettingEntryVisible }?.let { widget ->
      add(
        SettingItem.Action(
          key = "desktop_widget",
          title = "桌面小组件",
          onClick = widget::navigateToWidgetPage,
        )
      )
    }
    add(
      SettingItem.Action(
        key = "clear_app_data",
        title = "清理软件数据",
        onClick = actions.showClearDataDialog,
      )
    )
    add(
      SettingItem.Action(
        key = "course_max_week",
        title = "设置课表最大周数",
        onClick = actions.showCourseMaxWeekDialog,
      )
    )
  }

  override val courseMaxWeek: Int
    get() = ICourseService.maxWeek

  override fun clearApplicationData(): Boolean {
    val manager = appContext.getSystemService(ActivityManager::class.java) ?: return false
    return manager.clearApplicationUserData()
  }

  override fun openApplicationDetails() {
    val intent = Intent(
      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
      Uri.parse("package:${appContext.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    appContext.startActivity(intent)
  }

  override fun setCourseMaxWeek(maxWeek: Int) {
    ICourseService.setMaxWeek(maxWeek)
  }

  override fun clearPlatformDataBeforeLogout() {
    // 兼容仍读取旧 key 的桌面组件，避免退出后继续展示上一个账号的数据。
    defaultSp.edit {
      putString(WIDGET_COURSE, "")
      putBoolean(SP_WIDGET_NEED_FRESH, true)
    }
    PhoneCalendar.getCalendarAccount()?.let(PhoneCalendar::deleteCalendarAccount)
  }
}
