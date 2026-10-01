package com.cyxbs.pages.mine.setting

import com.cyxbs.pages.course.api.CourseUtils
import com.cyxbs.pages.mine.api.MineSettings

/**
 * CMP 设置页的平台能力与公共设置项基类。
 *
 * 公共设置项只在此处声明，平台实现通过 [switchItems]、[actionItems] 调用 `super` 后追加自己的
 * 条目，无需感知公共列表的具体内容。最终页面固定先展示开关项，再展示点击项。
 */
abstract class SettingPlatform {

  /**
   * 提供公共开关项。平台重写时应返回 `super.switchItems() + platformItems`，保持公共顺序稳定。
   */
  open fun switchItems(): List<SettingItem.Switch> = listOf(
    SettingItem.Switch(
      key = "show_course_first",
      title = "启动 App 优先显示课表",
      readChecked = MineSettings::isShowCourseFirst,
      rememberOnCheckedChange = { { enabled -> MineSettings.setShowCourseFirst(enabled) } },
    ),
  )

  /**
   * 提供公共点击项。平台重写时应返回 `super.actionItems(actions) + platformItems`。
   */
  open fun actionItems(actions: SettingItemActions): List<SettingItem.Action> = listOf(
    SettingItem.Action(
      key = "account_security",
      title = "账号与安全",
      requiresLogin = true,
      rememberOnClick = { ::openAccountSecurity },
    ),
    SettingItem.Action(
      key = "course_max_week",
      title = "设置课表最大周数",
      rememberOnClick = { actions.showCourseMaxWeekDialog },
    ),
  )

  /**
   * 合并最终设置项。类型分组在这里统一完成，平台添加条目不会改变公共项内部的顺序。
   */
  fun settingItems(actions: SettingItemActions): List<SettingItem> =
    switchItems() + actionItems(actions)

  /** 打开当前平台的账号与安全页面。 */
  protected abstract fun openAccountSecurity()

  /** 当前课表最大周数。 */
  open val courseMaxWeek: Int
    get() = CourseUtils.maxWeek

  /** 保存合法范围内的课表最大周数。 */
  open fun setCourseMaxWeek(maxWeek: Int) {
    CourseUtils.setMaxWeek(maxWeek)
  }

  /** 退出登录前清理当前平台的提醒或缓存，不让公共页面感知具体实现。 */
  open fun clearPlatformDataBeforeLogout() = Unit
}
