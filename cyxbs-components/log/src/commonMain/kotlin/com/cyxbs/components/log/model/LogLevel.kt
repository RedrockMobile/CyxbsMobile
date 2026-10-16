package com.cyxbs.components.log

/** 日志等级，按严重程度从低到高排列，不向业务暴露第三方类型。 */
enum class LogLevel {
  Verbose,
  Debug,
  Info,
  Warn,
  Error,
  Assert,
}
