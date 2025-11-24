package com.cyxbs.pages.map.model

import com.russhwolf.settings.Settings

/**
 * @Desc : Map信息的本地存储
 * @Author : zzx
 * @Date : 2025/11/24 18:27
 */

class MapSettings {

  private val mPlatformMapSettings = PlatformMapSettings()

  fun clear() {
    mPlatformMapSettings.clear()
  }

  fun remove(key: String) {
    mPlatformMapSettings.settings.remove(mPlatformMapSettings.keyMap(key))
  }

  fun hasKey(key: String): Boolean {
    return mPlatformMapSettings.settings.hasKey(mPlatformMapSettings.keyMap(key))
  }

  fun putInt(key: String, value: Int) {
    mPlatformMapSettings.settings.putInt(mPlatformMapSettings.keyMap(key), value)
  }

  fun getInt(key: String, defaultValue: Int): Int {
    return mPlatformMapSettings.settings.getInt(mPlatformMapSettings.keyMap(key), defaultValue)
  }

  fun getIntOrNull(key: String): Int? {
    return mPlatformMapSettings.settings.getIntOrNull(mPlatformMapSettings.keyMap(key))
  }

  fun putLong(key: String, value: Long) {
    mPlatformMapSettings.settings.putLong(mPlatformMapSettings.keyMap(key), value)
  }

  fun getLong(key: String, defaultValue: Long): Long {
    return mPlatformMapSettings.settings.getLong(mPlatformMapSettings.keyMap(key), defaultValue)
  }

  fun getLongOrNull(key: String): Long? {
    return mPlatformMapSettings.settings.getLongOrNull(mPlatformMapSettings.keyMap(key))
  }

  fun putString(key: String, value: String) {
    mPlatformMapSettings.settings.putString(mPlatformMapSettings.keyMap(key), value)
  }

  fun getString(key: String, defaultValue: String): String {
    return mPlatformMapSettings.settings.getString(mPlatformMapSettings.keyMap(key), defaultValue)
  }

  fun getStringOrNull(key: String): String? {
    return mPlatformMapSettings.settings.getStringOrNull(mPlatformMapSettings.keyMap(key))
  }

  fun putFloat(key: String, value: Float) {
    mPlatformMapSettings.settings.putFloat(mPlatformMapSettings.keyMap(key), value)
  }

  fun getFloat(key: String, defaultValue: Float): Float {
    return mPlatformMapSettings.settings.getFloat(mPlatformMapSettings.keyMap(key), defaultValue)
  }

  fun getFloatOrNull(key: String): Float? {
    return mPlatformMapSettings.settings.getFloatOrNull(mPlatformMapSettings.keyMap(key))
  }

  fun putDouble(key: String, value: Double) {
    mPlatformMapSettings.settings.putDouble(mPlatformMapSettings.keyMap(key), value)
  }

  fun getDouble(key: String, defaultValue: Double): Double {
    return mPlatformMapSettings.settings.getDouble(mPlatformMapSettings.keyMap(key), defaultValue)
  }

  fun getDoubleOrNull(key: String): Double? {
    return mPlatformMapSettings.settings.getDoubleOrNull(mPlatformMapSettings.keyMap(key))
  }

  fun putBoolean(key: String, value: Boolean) {
    mPlatformMapSettings.settings.putBoolean(mPlatformMapSettings.keyMap(key), value)
  }

  fun getBoolean(key: String, defaultValue: Boolean): Boolean {
    return mPlatformMapSettings.settings.getBoolean(mPlatformMapSettings.keyMap(key), defaultValue)
  }

  fun getBooleanOrNull(key: String): Boolean? {
    return mPlatformMapSettings.settings.getBooleanOrNull(mPlatformMapSettings.keyMap(key))
  }

}

internal expect class PlatformMapSettings() {
  val settings: Settings
  fun keyMap(originKey: String): String
  fun clear()
}