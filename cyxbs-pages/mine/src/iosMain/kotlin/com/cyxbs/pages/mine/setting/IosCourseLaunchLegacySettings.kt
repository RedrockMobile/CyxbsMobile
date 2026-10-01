package com.cyxbs.pages.mine.setting

import com.cyxbs.pages.mine.api.CourseLaunchLegacySettings
import com.g985892345.provider.api.annotation.ImplProvider
import platform.Foundation.NSUserDefaults

/** 旧原生设置存于标准 defaults，与 KMP 的 defaultSettings suite 不同。 */
private const val LEGACY_SHOW_COURSE_FIRST_KEY = "PRSENT_SCHEDULE_WHEN_OPEN_APP"

/** iOS 独有的旧原生设置兼容实现，注册后供公共设置读写方法按需调用。 */
@ImplProvider(clazz = CourseLaunchLegacySettings::class)
internal object IosCourseLaunchLegacySettings : CourseLaunchLegacySettings {

  /** 旧 key 缺失时返回 false，由 KMP 值决定开关状态。 */
  override fun isShowCourseFirst(): Boolean =
    NSUserDefaults.standardUserDefaults.boolForKey(LEGACY_SHOW_COURSE_FIRST_KEY)

  /** 关闭开关时移除旧 key；开启时不再写回原生存储。 */
  override fun clearShowCourseFirst() {
    NSUserDefaults.standardUserDefaults.removeObjectForKey(LEGACY_SHOW_COURSE_FIRST_KEY)
  }
}
