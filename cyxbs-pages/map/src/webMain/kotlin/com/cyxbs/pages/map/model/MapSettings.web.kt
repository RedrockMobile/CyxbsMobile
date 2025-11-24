package com.cyxbs.pages.map.model

import com.cyxbs.components.config.sp.defaultSettings
import com.russhwolf.settings.Settings

internal actual class PlatformMapSettings actual constructor() {

  private val mapSettingsName = "MapSettings"
  actual val settings: Settings
    get() = defaultSettings

  actual fun keyMap(originKey: String): String {
    return "$mapSettingsName-$originKey"
  }

  actual fun clear() {
    settings.keys.filter { it.startsWith(mapSettingsName) }.forEach {
      settings.remove(it)
    }
  }
}