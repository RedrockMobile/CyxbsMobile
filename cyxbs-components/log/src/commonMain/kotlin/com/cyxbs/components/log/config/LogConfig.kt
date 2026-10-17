package com.cyxbs.components.log

/**
 * 平台控制台输出配置。宿主负责按构建类型和用户策略配置。
 *
 * 默认输出 Info 及以上等级；[enabled] 为 false 时，消息 lambda 不会求值。
 * 当前不包含文件保存或上传；门面会执行有界脱敏，但调用方仍不得传入敏感信息。
 */
data class LogConfig(
  val enabled: Boolean = true,
  val minLevel: LogLevel = LogLevel.Info,
  val defaultTag: String = "CyxbsLog",
)
