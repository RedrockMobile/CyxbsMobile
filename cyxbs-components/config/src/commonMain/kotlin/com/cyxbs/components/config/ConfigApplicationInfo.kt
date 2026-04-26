package com.cyxbs.components.config

import com.cyxbs.components.config.service.implOrNull

/**
 * .
 *
 * @author 985892345
 * @date 2026/4/26
 */

val ConfigApplicationInfoImpl by lazy {
  ConfigApplicationInfo::class.implOrNull() ?: error("application 类型模块必须实现 ConfigApplicationInfo")
}

// 由 application 模块实现，注入一些特殊的配置
interface ConfigApplicationInfo {

  fun isDebug(): Boolean
}