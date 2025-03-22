package com.cyxbs.components.config.sp

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.StateFlow

/**
 * 多平台简单 Key-Value 对象本地存储
 * https://github.com/russhwolf/multiplatform-settings
 *
 * @author 985892345
 * @date 2025/1/11
 */

// 默认的 Key-Value 对象本地存储
expect val defaultSettings: Settings

// 当前登录人的 Key-Value 对象本地存储
val accountSettings: AccountSettings
  get() = AccountSettings.now

// 当前登录人的 Key-Value 对象本地存储
val accountSettingsFlow: StateFlow<AccountSettings>
  get() = AccountSettings.nowState
