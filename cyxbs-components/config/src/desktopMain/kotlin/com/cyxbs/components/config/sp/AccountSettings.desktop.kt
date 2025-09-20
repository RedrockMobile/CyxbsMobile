package com.cyxbs.components.config.sp

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings

internal actual class PlatformAccountSettings actual constructor(actual val stuNum: String?) {

  private val accountSettingsName = "AccountSettings-${stuNum}"

  actual val settings: Settings = PreferencesSettings.Factory().create(accountSettingsName)

  actual fun keyMap(originKey: String): String {
    return originKey
  }

  actual fun clear() {
    settings.clear()
  }
}