package com.cyxbs.pages.mine.setting

import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.edit
import com.cyxbs.components.config.service.startActivity
import com.cyxbs.components.config.sp.SP_COURSE_SHOW_STATE
import com.cyxbs.components.config.sp.defaultSp
import com.cyxbs.components.init.appContext
import com.cyxbs.components.utils.utils.config.PhoneCalendar
import com.cyxbs.pages.course.api.ICourseService
import com.cyxbs.pages.mine.page.security.activity.SecurityActivity
import com.g985892345.provider.api.annotation.ImplProvider
import com.mredrock.cyxbs.common.config.SP_WIDGET_NEED_FRESH
import com.mredrock.cyxbs.common.config.WIDGET_COURSE

/** Android 设置能力实现，集中隔离系统 API 与尚未迁移的账号安全 Activity。 */
@ImplProvider(clazz = SettingPlatform::class)
object SettingPlatformImpl : SettingPlatform {

  override val showCourseFirst: Boolean
    get() = defaultSp.getBoolean(SP_COURSE_SHOW_STATE, false)

  override val courseMaxWeek: Int
    get() = ICourseService.maxWeek

  override fun setShowCourseFirst(enabled: Boolean) {
    defaultSp.edit { putBoolean(SP_COURSE_SHOW_STATE, enabled) }
  }

  override fun openAccountSecurity() {
    startActivity(SecurityActivity::class)
  }

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
