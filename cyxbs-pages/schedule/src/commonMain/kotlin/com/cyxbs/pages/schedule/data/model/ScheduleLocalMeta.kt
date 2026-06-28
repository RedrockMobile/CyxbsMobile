package com.cyxbs.pages.schedule.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 分片 Settings 本地缓存的元信息。
 *
 * todo 列表本体不会作为一个大 JSON 存储，meta 只保存同步版本、chunk 数量和恢复标记等小字段。
 */
@Serializable
data class ScheduleLocalMeta(
  /** 本地缓存结构版本；版本不匹配时可直接触发 `/list` 全量重建。 */
  @SerialName("schema_version")
  val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
  /** 当前本地快照对应的后端全局 sync_time。 */
  @SerialName("sync_time")
  val syncTime: Long = 0L,
  /** 本地缓存损坏或 sync_time 失效时置 true，下一次同步优先走 `/list` 全量重建。 */
  @SerialName("needs_full_rebuild")
  val needsFullRebuild: Boolean = false,
  /** 当前索引内的 todo 数量，用于快速判断和调试。 */
  @SerialName("todo_count")
  val todoCount: Int = 0,
  /** todoId 索引分片数量，对应 `SP_SCHEDULE_CMP_TODO_IDS_CHUNK_{index}`。 */
  @SerialName("todo_chunk_count")
  val todoChunkCount: Int = 0,
  /** 当前 pending 操作数量。 */
  @SerialName("pending_count")
  val pendingCount: Int = 0,
  /** pendingId 索引分片数量，对应 `SP_SCHEDULE_CMP_PENDING_IDS_CHUNK_{index}`。 */
  @SerialName("pending_chunk_count")
  val pendingChunkCount: Int = 0,
  /** 客户端生成 todoId 的单调基准，避免重启后 id 回退。 */
  @SerialName("next_local_id")
  val nextLocalId: Long = 0L,
  /** 最近一次本地变更时间，用于调试、排序兜底和后续冲突策略扩展。 */
  @SerialName("last_local_mutation_time")
  val lastLocalMutationTime: Long = 0L,
  /** 最近一次成功与后端同步后拿到的 sync_time。 */
  @SerialName("last_successful_sync_time")
  val lastSuccessfulSyncTime: Long = 0L,
) {
  companion object {
    /** 当前分片 Settings 缓存结构版本。 */
    const val CURRENT_SCHEMA_VERSION = 1
  }
}
