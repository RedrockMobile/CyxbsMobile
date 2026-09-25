package com.cyxbs.pages.mine.setting

/**
 * 设置页可展示的条目，由具体平台按自己的能力与顺序提供。
 *
 * [key] 在同一页面内必须唯一，用于保留开关的本地交互状态。
 */
sealed class SettingItem(open val key: String, open val title: String) {

  /**
   * 带持久化回调的开关项。
   *
   * [readChecked] 在条目首次进入组合时读取平台值；[onCheckedChange] 负责保存新值。
   */
  class Switch(
    override val key: String,
    override val title: String,
    val readChecked: () -> Boolean,
    val onCheckedChange: (Boolean) -> Unit,
  ) : SettingItem(key, title)

  /** 点击后由平台执行导航或业务动作的普通条目。 */
  class Action(
    override val key: String,
    override val title: String,
    val requiresLogin: Boolean = false,
    val onClick: () -> Unit,
  ) : SettingItem(key, title)
}

/**
 * 平台条目需要展示公共确认弹窗时使用的页面回调。
 *
 * 回调只请求展示 UI，清理数据和保存周数仍由 [SettingPlatform] 执行。
 */
class SettingItemActions(
  val showClearDataDialog: () -> Unit,
  val showCourseMaxWeekDialog: () -> Unit,
)
