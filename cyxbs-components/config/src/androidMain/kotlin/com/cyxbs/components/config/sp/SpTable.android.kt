package com.cyxbs.components.config.sp

import android.content.Context
import android.content.SharedPreferences
import com.cyxbs.components.init.appApplication
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

/**
 * 这里面保存需要跨模块全局使用的 SP，重要数据以及大量数据请不要使用该 SP 保存
 *
 * 注意：有些数据根本就没有放在全局中使用，自己模块中使用到的 SP 请放在自己的模块中！！！
 */


/**
 * 全局通用的 Sp，用于整个应用内传递数据，重要数据以及大量数据请不要使用该 SP 保存
 */
val defaultSp: SharedPreferences
  get() = appApplication.getSharedPreferences("share_data", Context.MODE_PRIVATE)

// 多平台的全局通用 Sp
actual val defaultSettings: Settings = SharedPreferencesSettings(defaultSp)
