package com.cyxbs.components.config

/**
 * .
 *
 * @author 985892345
 * @date 2025/1/4
 */
actual val appName: String
  get() = "掌上重邮"

var isIOSDebug = false // 由 applications 模块设置

actual fun isDebug(): Boolean {
  return isIOSDebug
}