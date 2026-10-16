package com.cyxbs.components.log

/**
 * 平台控制台输出配置。宿主负责按构建类型和用户策略配置。
 *
 * 默认输出 Info 及以上等级；[enabled] 为 false 时，消息 lambda 不会求值。
 * 当前不包含文件保存、自动脱敏或上传，调用方不得传入敏感信息。
 */
data class LogConfig(
  val enabled: Boolean = true,
  val minLevel: LogLevel = LogLevel.Info,
  val defaultTag: String = "CyxbsLog",
)
