package com.cyxbs.components.config

import com.cyxbs.components.config.service.impl

/**
 * .
 *
 * @author 985892345
 * @date 2025/1/4
 */
actual val appName: String
  get() = "掌上重邮"

interface IOSDebug {
  fun isDebug(): Boolean
}

actual fun isDebug(): Boolean {
  return IOSDebug::class.impl().isDebug()
}