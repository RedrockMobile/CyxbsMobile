package com.cyxbs.pages.map.model

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings

internal actual class PlatformMapSettings actual constructor() {

  private val mapSettingsName = "MapSettings"

  actual val settings: Settings = PreferencesSettings.Factory().create(mapSettingsName)
  actual fun keyMap(originKey: String): String {
    return originKey
  }

  actual fun clear() {
    settings.clear()
  }
}