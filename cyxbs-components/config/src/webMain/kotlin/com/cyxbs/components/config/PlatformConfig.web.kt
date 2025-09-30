package com.cyxbs.components.config

/**
 * .
 *
 * @author 985892345
 * @date 2025/1/4
 */
actual val appName: String
  get() = "网上重邮"

actual fun isDebug(): Boolean {
  return true // todo 后续再配置，考虑使用 buildConfig 插件生成 https://github.com/gmazzo/gradle-buildconfig-plugin
}