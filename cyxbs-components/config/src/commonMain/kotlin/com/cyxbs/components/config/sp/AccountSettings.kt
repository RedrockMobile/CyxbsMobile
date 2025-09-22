package com.cyxbs.components.config.sp

import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.russhwolf.settings.Settings

/**
 * 获取指定学号的 Key-Value 对象本地存储
 *
 * @author 985892345
 * @date 2025/3/15
 */
class AccountSettings private constructor(val stuNum: String?) {

  companion object {

    // 获取当前登录人的 AccountSettings
    var now: AccountSettings = get(IAccountService::class.impl().stuNum)

    // 获取指定学号的 AccountSettings
    // stuNum 为 null 表示未登录
    fun get(stuNum: String?): AccountSettings {
      return AccountSettings(stuNum)
    }
  }

  private val mPlatformAccountSettings = PlatformAccountSettings(stuNum)

  fun clear() {
    mPlatformAccountSettings.clear()
  }

  fun remove(key: String) {
    mPlatformAccountSettings.settings.remove(mPlatformAccountSettings.keyMap(key))
  }

  fun hasKey(key: String): Boolean {
    return mPlatformAccountSettings.settings.hasKey(mPlatformAccountSettings.keyMap(key))
  }

  fun putInt(key: String, value: Int) {
    mPlatformAccountSettings.settings.putInt(mPlatformAccountSettings.keyMap(key), value)
  }

  fun getInt(key: String, defaultValue: Int): Int {
    return mPlatformAccountSettings.settings.getInt(mPlatformAccountSettings.keyMap(key), defaultValue)
  }

  fun getIntOrNull(key: String): Int? {
    return mPlatformAccountSettings.settings.getIntOrNull(mPlatformAccountSettings.keyMap(key))
  }

  fun putLong(key: String, value: Long) {
    mPlatformAccountSettings.settings.putLong(mPlatformAccountSettings.keyMap(key), value)
  }

  fun getLong(key: String, defaultValue: Long): Long {
    return mPlatformAccountSettings.settings.getLong(mPlatformAccountSettings.keyMap(key), defaultValue)
  }

  fun getLongOrNull(key: String): Long? {
    return mPlatformAccountSettings.settings.getLongOrNull(mPlatformAccountSettings.keyMap(key))
  }

  fun putString(key: String, value: String) {
    mPlatformAccountSettings.settings.putString(mPlatformAccountSettings.keyMap(key), value)
  }

  fun getString(key: String, defaultValue: String): String {
    return mPlatformAccountSettings.settings.getString(mPlatformAccountSettings.keyMap(key), defaultValue)
  }

  fun getStringOrNull(key: String): String? {
    return mPlatformAccountSettings.settings.getStringOrNull(mPlatformAccountSettings.keyMap(key))
  }

  fun putFloat(key: String, value: Float) {
    mPlatformAccountSettings.settings.putFloat(mPlatformAccountSettings.keyMap(key), value)
  }

  fun getFloat(key: String, defaultValue: Float): Float {
    return mPlatformAccountSettings.settings.getFloat(mPlatformAccountSettings.keyMap(key), defaultValue)
  }

  fun getFloatOrNull(key: String): Float? {
    return mPlatformAccountSettings.settings.getFloatOrNull(mPlatformAccountSettings.keyMap(key))
  }

  fun putDouble(key: String, value: Double) {
    mPlatformAccountSettings.settings.putDouble(mPlatformAccountSettings.keyMap(key), value)
  }

  fun getDouble(key: String, defaultValue: Double): Double {
    return mPlatformAccountSettings.settings.getDouble(mPlatformAccountSettings.keyMap(key), defaultValue)
  }

  fun getDoubleOrNull(key: String): Double? {
    return mPlatformAccountSettings.settings.getDoubleOrNull(mPlatformAccountSettings.keyMap(key))
  }

  fun putBoolean(key: String, value: Boolean) {
    mPlatformAccountSettings.settings.putBoolean(mPlatformAccountSettings.keyMap(key), value)
  }

  fun getBoolean(key: String, defaultValue: Boolean): Boolean {
    return mPlatformAccountSettings.settings.getBoolean(mPlatformAccountSettings.keyMap(key), defaultValue)
  }

  fun getBooleanOrNull(key: String): Boolean? {
    return mPlatformAccountSettings.settings.getBooleanOrNull(mPlatformAccountSettings.keyMap(key))
  }
}

internal expect class PlatformAccountSettings(stuNum: String?) {
  val stuNum: String?
  val settings: Settings
  fun keyMap(originKey: String): String
  fun clear()
}
