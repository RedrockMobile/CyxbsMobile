package com.cyxbs.components.config.sp

import com.russhwolf.settings.Settings

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/15
 */

internal actual class PlatformAccountSettings actual constructor(actual val stuNum: String?) {

  private val accountSettingsName = "AccountSettings-${stuNum}"

  actual val settings: Settings
    get() = defaultSettings // wasmJs 不支持多文件配置，只能通过转换 key 的形式来实现

  actual fun keyMap(originKey: String): String {
    return "$accountSettingsName-$originKey"
  }

  actual fun clear() {
    settings.keys.filter { it.startsWith(accountSettingsName) }.forEach {
      settings.remove(it)
    }
  }
}