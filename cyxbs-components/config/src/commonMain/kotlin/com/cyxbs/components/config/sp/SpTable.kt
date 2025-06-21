package com.cyxbs.components.config.sp

import com.russhwolf.settings.Settings

/**
 * 多平台简单 Key-Value 对象本地存储
 * https://github.com/russhwolf/multiplatform-settings
 *
 * @author 985892345
 * @date 2025/1/11
 */

// 设备维度的 Key-Value 对象本地存储
expect val defaultSettings: Settings

// 当前登录人的 Key-Value 对象本地存储
val accountSettings: AccountSettings
  get() = AccountSettings.now
