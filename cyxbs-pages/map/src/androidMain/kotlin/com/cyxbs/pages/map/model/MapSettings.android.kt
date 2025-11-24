package com.cyxbs.pages.map.model

import androidx.core.content.edit
import com.cyxbs.components.init.appApplication
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

internal actual class PlatformMapSettings actual constructor() {

  private val mapSettingsName = "MapSettings"

  private val mapSp = appApplication.getSharedPreferences(mapSettingsName, 0)

  actual val settings: Settings = SharedPreferencesSettings(mapSp)

  actual fun keyMap(originKey: String): String {
    return originKey
  }

  actual fun clear() {
    mapSp.edit {
      clear() // SharedPreferencesSettings#clear 内部实现采取遍历 remove，所以修改为原生 clear()
    }
  }
}