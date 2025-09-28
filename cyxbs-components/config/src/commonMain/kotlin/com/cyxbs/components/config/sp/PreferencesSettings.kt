package com.cyxbs.components.config.sp

import com.russhwolf.settings.Settings
import com.russhwolf.settings.nullableInt
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Key-Value 对象本地存储
 *
 * @author 985892345
 * @date 2025/3/15
 */
open class PreferencesSettings internal constructor(val key: String) : Settings {

  companion object {

    private val map = mutableMapOf<String, PreferencesSettings>()

    private val mapSynchronized = SynchronizedObject()

    fun get(key: String): PreferencesSettings {
      return synchronized(mapSynchronized) {
        // 因为 kotlin 官方未提供线程安全的 map 实现，所以这里暂时使用锁来实现线程安全
        map.getOrPut(key) { PreferencesSettings(key) }
      }
    }
  }

  private val mPlatformAccountSettings = PlatformPreferencesSettings(key)

  override val keys: Set<String>
    get() = mPlatformAccountSettings.settings.keys

  override val size: Int
    get() = mPlatformAccountSettings.settings.size

  override fun clear() {
    mPlatformAccountSettings.clear()
    mPlatformAccountSettings.settings.nullableInt()
  }

  override fun remove(key: String) {
    mPlatformAccountSettings.settings.remove(mPlatformAccountSettings.keyMap(key))
  }

  override fun hasKey(key: String): Boolean {
    return mPlatformAccountSettings.settings.hasKey(mPlatformAccountSettings.keyMap(key))
  }

  override fun putInt(key: String, value: Int) {
    mPlatformAccountSettings.settings.putInt(mPlatformAccountSettings.keyMap(key), value)
  }

  override fun getInt(key: String, defaultValue: Int): Int {
    return mPlatformAccountSettings.settings.getInt(mPlatformAccountSettings.keyMap(key), defaultValue)
  }

  override fun getIntOrNull(key: String): Int? {
    return mPlatformAccountSettings.settings.getIntOrNull(mPlatformAccountSettings.keyMap(key))
  }

  override fun putLong(key: String, value: Long) {
    mPlatformAccountSettings.settings.putLong(mPlatformAccountSettings.keyMap(key), value)
  }

  override fun getLong(key: String, defaultValue: Long): Long {
    return mPlatformAccountSettings.settings.getLong(mPlatformAccountSettings.keyMap(key), defaultValue)
  }

  override fun getLongOrNull(key: String): Long? {
    return mPlatformAccountSettings.settings.getLongOrNull(mPlatformAccountSettings.keyMap(key))
  }

  override fun putString(key: String, value: String) {
    mPlatformAccountSettings.settings.putString(mPlatformAccountSettings.keyMap(key), value)
  }

  override fun getString(key: String, defaultValue: String): String {
    return mPlatformAccountSettings.settings.getString(mPlatformAccountSettings.keyMap(key), defaultValue)
  }

  override fun getStringOrNull(key: String): String? {
    return mPlatformAccountSettings.settings.getStringOrNull(mPlatformAccountSettings.keyMap(key))
  }

  override fun putFloat(key: String, value: Float) {
    mPlatformAccountSettings.settings.putFloat(mPlatformAccountSettings.keyMap(key), value)
  }

  override fun getFloat(key: String, defaultValue: Float): Float {
    return mPlatformAccountSettings.settings.getFloat(mPlatformAccountSettings.keyMap(key), defaultValue)
  }

  override fun getFloatOrNull(key: String): Float? {
    return mPlatformAccountSettings.settings.getFloatOrNull(mPlatformAccountSettings.keyMap(key))
  }

  override fun putDouble(key: String, value: Double) {
    mPlatformAccountSettings.settings.putDouble(mPlatformAccountSettings.keyMap(key), value)
  }

  override fun getDouble(key: String, defaultValue: Double): Double {
    return mPlatformAccountSettings.settings.getDouble(mPlatformAccountSettings.keyMap(key), defaultValue)
  }

  override fun getDoubleOrNull(key: String): Double? {
    return mPlatformAccountSettings.settings.getDoubleOrNull(mPlatformAccountSettings.keyMap(key))
  }

  override fun putBoolean(key: String, value: Boolean) {
    mPlatformAccountSettings.settings.putBoolean(mPlatformAccountSettings.keyMap(key), value)
  }

  override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
    return mPlatformAccountSettings.settings.getBoolean(mPlatformAccountSettings.keyMap(key), defaultValue)
  }

  override fun getBooleanOrNull(key: String): Boolean? {
    return mPlatformAccountSettings.settings.getBooleanOrNull(mPlatformAccountSettings.keyMap(key))
  }
}

internal expect class PlatformPreferencesSettings(key: String) {
  val key: String
  val settings: Settings
  fun keyMap(originKey: String): String
  fun clear()
}
