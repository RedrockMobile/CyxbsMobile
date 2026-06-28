package com.cyxbs.pages.schedule.data.repository

/**
 * todo 同步状态。
 *
 * 封装当前同步进度、错误信息、最后同步时间等状态，供 UI 展示。
 */
sealed interface ScheduleSyncState {

  /** 空闲状态，未开始同步或上次同步已完成。 */
  data object Idle : ScheduleSyncState

  /** 正在同步中。 */
  data class Syncing(
    /** 当前同步阶段描述，例如 "pulling" / "pushing" / "flushing pending"。 */
    val phase: String,
  ) : ScheduleSyncState

  /** 同步成功。 */
  data class Success(
    /** 最后成功同步时间戳。 */
    val lastSyncTime: Long,
  ) : ScheduleSyncState

  /** 同步失败。 */
  data class Error(
    /** 错误消息。 */
    val message: String,
    /** 错误类型名，用于调试。 */
    val errorType: String? = null,
  ) : ScheduleSyncState
}
