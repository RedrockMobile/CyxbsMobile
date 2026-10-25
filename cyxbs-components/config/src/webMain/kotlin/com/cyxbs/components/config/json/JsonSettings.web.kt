package com.cyxbs.components.config.json

import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.sp.PreferencesSettings
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * Web端无法做到json的本地保存，故这里内部直接复用[com.cyxbs.components.config.sp.PreferencesSettings]
 * 类似于：
 * JsonSettings_map-map_info
 * JsonSettings_map-map_search_history
 */
actual class JsonSettings internal actual constructor(name: String) {

  private  val settings = PreferencesSettings.get("JsonSettings_$name")

  actual suspend fun <T> put(
    key: String,
    value: T,
    serializer: KSerializer<T>
  ): Boolean {
    return runCatching {
      settings.putString(
        key,
        defaultJson.encodeToString(serializer, value)
      )
      true
    }.getOrDefault(false)
  }

  actual suspend fun <T> getOrNull(
    key: String,
    serializer: KSerializer<T>
  ): T? {
    return runCatching {
      settings.getStringOrNull(key)?.let {
        defaultJson.decodeFromString(serializer, it)
      }
    }.getOrNull()
  }

  actual suspend fun remove(key: String): Boolean {
    settings.remove(key)
    return true
  }

  actual suspend fun clear(): Boolean {
    settings.clear()
    return true
  }
}
