package com.cyxbs.components.config

/**
 * .
 *
 * @author 985892345
 * @date 2025/1/4
 */
actual val appName: String
  get() = "桌上重邮"

actual val appPlatform: Platform
  get() = Platform.Desktop

/**
 * 来自 jb 官方源码
 *
 * @author 985892345
 * 2024/4/2 21:40
 */
enum class DesktopPlatform {
  Linux,
  Windows,
  MacOS,
  Unknown;

  companion object {
    /**
     * Identify OS on which the application is currently running.
     */
    val Current: DesktopPlatform by lazy {
      val name = System.getProperty("os.name")
      when {
        name?.startsWith("Linux") == true -> Linux
        name?.startsWith("Win") == true -> Windows
        name == "Mac OS X" -> MacOS
        else -> Unknown
      }
    }
  }
}