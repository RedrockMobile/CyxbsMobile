package com.cyxbs.components.config.sp

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/15
 */

internal actual class PlatformAccountSettings actual constructor(actual val stuNum: String?) {

  private val accountSettingsName = "AccountSettings-${stuNum}"

  actual val settings: Settings = NSUserDefaultsSettings.Factory().create(accountSettingsName)

  actual fun keyMap(originKey: String): String {
    return originKey
  }

  actual fun clear() {
    settings.clear()
  }
}