package com.cyxbs.components.config.sp

import com.russhwolf.settings.Settings

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/15
 */

internal actual class PlatformPreferencesSettings actual constructor(actual val key: String) {

  actual val settings: Settings
    get() = defaultSettings // wasmJs 不支持多文件配置，只能通过转换 key 的形式来实现

  actual fun keyMap(originKey: String): String {
    return "$key-$originKey"
  }

  actual fun clear() {
    settings.keys.filter { it.startsWith(key) }.forEach {
      settings.remove(it)
    }
  }
}