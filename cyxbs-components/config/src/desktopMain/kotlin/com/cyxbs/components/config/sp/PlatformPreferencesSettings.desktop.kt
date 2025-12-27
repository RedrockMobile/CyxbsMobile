package com.cyxbs.components.config.sp

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings

internal actual class PlatformPreferencesSettings actual constructor(actual val key: String) {

  actual val settings: Settings = PreferencesSettings.Factory().create(key)

  actual fun keyMap(originKey: String): String {
    return originKey
  }

  actual fun clear() {
    settings.clear()
  }
}