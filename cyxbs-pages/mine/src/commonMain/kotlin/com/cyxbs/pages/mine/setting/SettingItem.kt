package com.cyxbs.pages.mine.setting

import androidx.compose.runtime.Composable

/**
 * 设置页可展示的条目，由具体平台按自己的能力与顺序提供。
 *
 * [key] 在同一页面内必须唯一，用于保留开关的本地交互状态。
 */
sealed class SettingItem(open val key: String, open val title: String) {

  /**
   * 带持久化回调的开关项。
   *
   * [readChecked] 读取实际持久化状态；[rememberOnCheckedChange] 在组合内创建本条目的切换回调，
   * 可自行持有权限 launcher 或其他页面资源。回调完成后页面重新读取状态，拒绝授权时不乐观开启。
   */
  class Switch(
    override val key: String,
    override val title: String,
    val readChecked: () -> Boolean,
    val rememberOnCheckedChange: @Composable () -> suspend (Boolean) -> Unit,
  ) : SettingItem(key, title)

  /** 普通点击项；[rememberOnClick] 可在组合内持有该条目自己的确认弹窗等页面资源。 */
  class Action(
    override val key: String,
    override val title: String,
    val requiresLogin: Boolean = false,
    val rememberOnClick: @Composable () -> () -> Unit,
  ) : SettingItem(key, title)
}

/**
 * 平台条目需要展示公共确认弹窗时使用的页面回调。
 *
 * 只包含共有弹窗的请求；平台独有弹窗由对应条目自行持有，不加入公共页面协议。
 */
class SettingItemActions(
  val showCourseMaxWeekDialog: () -> Unit,
)
