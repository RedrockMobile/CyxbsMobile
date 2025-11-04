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

/*
* 请在下面写上传递的 key 值，以 SP_模块名_作用名 开头命名，后面还可以细分
* */

// 隐私条例是否同意
const val SP_PRIVACY_AGREED = "privacy_agreed"