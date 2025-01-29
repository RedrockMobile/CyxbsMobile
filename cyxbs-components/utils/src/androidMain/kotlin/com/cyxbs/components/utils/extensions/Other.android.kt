package com.cyxbs.components.utils.extensions

import android.content.res.Configuration
import com.cyxbs.components.init.appContext

/**
 * 是否是日间模式，否则为夜间模式
 */
fun isDaytimeMode(): Boolean {
  val uiMode = appContext.resources.configuration.uiMode
  return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_NO
}

/**
 * 是否是夜间模式
 */
fun isDarkMode() = !isDaytimeMode()




