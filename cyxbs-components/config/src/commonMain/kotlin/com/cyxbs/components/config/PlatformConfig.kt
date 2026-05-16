package com.cyxbs.components.config

/**
 * .
 *
 * @author 985892345
 * @date 2025/1/4
 */

// 应用名称
expect val appName: String

fun isDebug(): Boolean = ConfigApplicationInfoImpl.isDebug()
