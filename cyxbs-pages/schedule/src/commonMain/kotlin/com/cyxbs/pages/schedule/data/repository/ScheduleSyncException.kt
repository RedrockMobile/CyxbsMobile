package com.cyxbs.pages.schedule.data.repository

/**
 * todo 同步异常基类。
 */
sealed class ScheduleSyncException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * 网络连接异常，可重试。
 *
 * 包括超时、连接失败、DNS 解析失败等底层网络问题。
 */
class ScheduleNetworkException(
  message: String = "网络连接失败",
  cause: Throwable? = null,
) : ScheduleSyncException(message, cause)

/**
 * 数据异常，需要全量重建。
 *
 * 包括 sync_time 不存在、数据格式错误、服务端数据损坏等。
 */
class ScheduleDataException(
  message: String = "数据异常",
  cause: Throwable? = null,
) : ScheduleSyncException(message, cause)

/**
 * 同步冲突异常，需要全量重建。
 *
 * 上传 pending 时 sync_time 冲突。
 */
class ScheduleSyncConflictException(
  message: String = "同步冲突",
  cause: Throwable? = null,
) : ScheduleSyncException(message, cause)
