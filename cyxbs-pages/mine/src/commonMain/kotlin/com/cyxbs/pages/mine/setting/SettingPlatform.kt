package com.cyxbs.pages.mine.setting

/**
 * CMP 设置页依赖的平台能力。
 *
 * 当前仅 Android 提供实现；iOS 仍由原生设置页承载，因此 commonMain 页面不得直接依赖 Activity、
 * SharedPreferences 或系统清理接口。
 */
interface SettingPlatform {

  /**
   * 提供当前平台可展示的设置项；顺序即页面顺序，不支持的能力不应出现在列表中。
   * [actions] 用于请求公共确认弹窗，具体系统动作仍由平台实现。
   */
  fun settingItems(actions: SettingItemActions): List<SettingItem>

  /** 当前课表最大周数。 */
  val courseMaxWeek: Int

  /**
   * 请求系统清除应用数据。
   *
   * @return 系统是否已接受请求；失败时调用方应引导用户前往应用详情页手动清理。
   */
  fun clearApplicationData(): Boolean

  /** 打开当前应用的系统详情页。 */
  fun openApplicationDetails()

  /** 保存合法范围内的课表最大周数。 */
  fun setCourseMaxWeek(maxWeek: Int)

  /** 退出登录前清除 Android 日历等平台缓存。 */
  fun clearPlatformDataBeforeLogout()
}
