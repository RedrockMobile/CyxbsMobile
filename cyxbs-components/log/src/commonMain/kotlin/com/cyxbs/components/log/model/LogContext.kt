package com.cyxbs.components.log

/**
 * 可选的结构化诊断上下文。字段集合固定，禁止用任意 Map 承载个人数据、请求体、请求头或动态 URL。
 *
 * [route] 只能填写归一化路由模板（例如 `/courses/{id}`），query 与 fragment 会在日志边界移除。
 * [correlationId] 只接受 1..64 位 ASCII 字母、数字及 `._:-`，不得使用学号、手机号或设备标识。
 * [eventTimestampMillis] 用于描述此前发生、现在才观察到的事件；省略时与 observed timestamp 相同。
 */
data class LogContext(
  val operation: String? = null,
  val route: String? = null,
  val method: LogHttpMethod? = null,
  val statusCode: Int? = null,
  val durationMillis: Long? = null,
  val result: LogResult? = null,
  val retryCount: Int? = null,
  val fromState: String? = null,
  val toState: String? = null,
  val correlationId: String? = null,
  val eventTimestampMillis: Long? = null,
)

enum class LogHttpMethod {
  Get,
  Head,
  Post,
  Put,
  Patch,
  Delete,
  Options,
}

enum class LogResult {
  Success,
  Failure,
  Cancelled,
  Degraded,
  Skipped,
}
