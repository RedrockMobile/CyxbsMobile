package com.cyxbs.components.config.sp

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings

/**
 * 多平台简单 Key-Value 对象本地存储
 * https://github.com/russhwolf/multiplatform-settings
 *
 * @author 985892345
 * @date 2025/1/11
 */
actual val defaultSettings: Settings = NSUserDefaultsSettings.Factory().create("defaultSettings")