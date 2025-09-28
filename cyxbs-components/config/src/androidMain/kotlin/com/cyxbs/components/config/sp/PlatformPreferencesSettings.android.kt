package com.cyxbs.components.config.sp

import androidx.core.content.edit
import com.cyxbs.components.init.appApplication
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

internal actual class PlatformPreferencesSettings actual constructor(actual val key: String) {

  private val accountSp = appApplication.getSharedPreferences(key, 0)

  actual val settings: Settings = SharedPreferencesSettings(accountSp)

  actual fun keyMap(originKey: String): String {
    return originKey
  }

  actual fun clear() {
    accountSp.edit {
      clear() // SharedPreferencesSettings#clear 内部实现采取遍历 remove，所以修改为原生 clear()
    }
  }
}