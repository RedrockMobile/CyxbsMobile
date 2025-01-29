package com.cyxbs.components.config

import com.cyxbs.components.init.appApplication

/**
 * .
 *
 * @author 985892345
 * @date 2025/1/4
 */
actual val appName: String = appApplication.getString(R.string.config_app_name)

actual fun isDebug(): Boolean {
  return BuildConfig.DEBUG
}