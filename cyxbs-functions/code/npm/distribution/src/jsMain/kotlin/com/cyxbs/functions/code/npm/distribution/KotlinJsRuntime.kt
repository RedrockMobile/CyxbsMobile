package com.cyxbs.functions.code.npm.distribution

/**
 * 共享 Kotlin/JS Runtime 聚合编译的版本标记。
 *
 * 该函数只用于给 distribution 保留明确的 Kotlin 源集入口；分包任务会将 npm main 指向
 * Kotlin stdlib Module。业务代码不应依赖此函数判断兼容性，实际版本以 package.json 为准。
 */
fun kotlinJsRuntimeVersion(): String = "0.1.0"
