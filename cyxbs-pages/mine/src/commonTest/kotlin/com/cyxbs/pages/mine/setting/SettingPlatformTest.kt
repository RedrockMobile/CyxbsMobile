package com.cyxbs.pages.mine.setting

import kotlin.test.Test
import kotlin.test.assertEquals

/** 验证公共与平台设置项的分类合并顺序。 */
class SettingPlatformTest {

  /** 不追加平台开关时仅有公共启动偏好，桌面及网页不会继承移动端提醒。 */
  @Test
  fun commonSettingsDoNotIncludeMobileReminders() {
    val platform = object : SettingPlatform() {
      override fun openAccountSecurity() = Unit
    }
    assertEquals(listOf("show_course_first"), platform.switchItems().map(SettingItem::key))
  }

  /** 平台追加条目不能打乱公共条目顺序，最终应统一先展示开关，再展示点击项。 */
  @Test
  fun platformItemsAreAppendedWithinTheirOwnCategory() {
    val platform = TestSettingPlatform()
    val actions = SettingItemActions(
      showCourseMaxWeekDialog = {},
    )

    assertEquals(
      listOf(
        "show_course_first",
        "platform_switch",
        "account_security",
        "course_max_week",
        "platform_action",
      ),
      platform.settingItems(actions).map(SettingItem::key),
    )
  }

  /** 测试用平台实现，只追加各一项以验证父类的合并规则。 */
  private class TestSettingPlatform : SettingPlatform() {

    override fun switchItems(): List<SettingItem.Switch> = super.switchItems() +
      SettingItem.Switch(
        key = "platform_switch",
        title = "平台开关",
        readChecked = { false },
        rememberOnCheckedChange = { {} },
      )

    override fun actionItems(actions: SettingItemActions): List<SettingItem.Action> =
      super.actionItems(actions) + SettingItem.Action(
        key = "platform_action",
        title = "平台点击项",
        rememberOnClick = { {} },
      )

    override fun openAccountSecurity() = Unit
  }
}
