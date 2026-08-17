package com.cyxbs.pages.schedule.data.local.room3

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import com.cyxbs.pages.schedule.data.remote.v3.CategoryCurrent
import com.cyxbs.pages.schedule.data.remote.v3.CategoryInput
import com.cyxbs.pages.schedule.data.remote.v3.OccurrenceOverrideCurrent
import com.cyxbs.pages.schedule.data.remote.v3.OccurrenceOverrideInput
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleCurrent
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleInput

/** 待提交侧仅有的操作类型；没有逐条 outbox、回执或重试阶段。 */
internal object ScheduleV2PendingOperation {
  const val UPSERT = "UPSERT"
  const val DELETE = "DELETE"
}

/**
 * 每个账号的本地修订号分配器。
 *
 * 该值只服务 pending compare-and-clear，和服务端资源 version、原子字段 modifiedAt 互不关联。
 */
@Entity(tableName = "schedule_v2_account_metadata", primaryKeys = ["account_id"])
data class ScheduleV2AccountMetadataEntity(
  @ColumnInfo(name = "account_id") val accountId: String,
  /** 纯本地 revision 计数器；每次事务内先递增再分配，不上传，也不代表服务端 version。 */
  @ColumnInfo(name = "local_revision_counter") val localRevisionCounter: Long = 0,
)

/**
 * Category identity 的 remote/pending 双快照状态行。
 *
 * Snapshot 只在严格 typed codec 边界编码、解码；Room 通过 TypeConverter 存为 JSON。DELETE 时 pending snapshot 为 null，
 * 只使用 identity、localModifiedAt、localRevision 与可选 localBatchId。
 */
@Entity(
  tableName = "schedule_v2_category_state",
  primaryKeys = ["account_id", "category_id"],
  indices = [Index(value = ["account_id", "pending_operation", "local_batch_id"])],
)
data class ScheduleV2CategoryStateEntity(
  @ColumnInfo(name = "account_id") val accountId: String,
  @ColumnInfo(name = "category_id") val categoryId: String,
  /** 服务端确认的完整快照；构造 confirmed 时读取，不原样上传。 */
  @ColumnInfo(name = "remote_snapshot_json") val remoteSnapshot: CategoryCurrent?,
  /** 本地待提交操作，仅 UPSERT 或 DELETE；不作为 wire 字段上传。 */
  @ColumnInfo(name = "pending_operation") val pendingOperation: String?,
  /** UPSERT 要上传的完整输入；DELETE 时为 null。 */
  @ColumnInfo(name = "pending_snapshot_json") val pendingSnapshot: CategoryInput?,
  /** DELETE 的本地操作时刻；仅 DELETE 请求上传。 */
  @ColumnInfo(name = "pending_local_modified_at") val pendingLocalModifiedAt: Long?,
  /**
   * 纯本地 compare-and-clear 标识；不上传且不同于服务端 version。
   * R 的响应只可清除它上传的旧 revision；更高 revision 的 U 始终保留并作为 effective 值，下一轮同步后收敛。
   */
  @ColumnInfo(name = "local_revision") val localRevision: Long?,
  /** 本地持久分组键，capture 时映射为当次 AtomicBatch.batchId；不是 receipt 或处理证明。 */
  @ColumnInfo(name = "local_batch_id") val localBatchId: String?,
)

/**
 * Schedule identity 的 remote/pending 双快照状态行。
 *
 * recurrence、reminders 和 AtomicField 都属于 typed snapshot，不拆出 child table 或 JSON 以外的持久化状态机。
 */
@Entity(
  tableName = "schedule_v2_schedule_state",
  primaryKeys = ["account_id", "schedule_id"],
  indices = [Index(value = ["account_id", "pending_operation", "local_batch_id"])],
)
data class ScheduleV2ScheduleStateEntity(
  @ColumnInfo(name = "account_id") val accountId: String,
  @ColumnInfo(name = "schedule_id") val scheduleId: String,
  /** 服务端确认的完整快照；构造 confirmed 时读取，不原样上传。 */
  @ColumnInfo(name = "remote_snapshot_json") val remoteSnapshot: ScheduleCurrent?,
  /** 本地待提交操作，仅 UPSERT 或 DELETE；不作为 wire 字段上传。 */
  @ColumnInfo(name = "pending_operation") val pendingOperation: String?,
  /** UPSERT 要上传的完整输入；DELETE 时为 null。 */
  @ColumnInfo(name = "pending_snapshot_json") val pendingSnapshot: ScheduleInput?,
  /** DELETE 的本地操作时刻；仅 DELETE 请求上传。 */
  @ColumnInfo(name = "pending_local_modified_at") val pendingLocalModifiedAt: Long?,
  /**
   * 纯本地 compare-and-clear 标识；不上传且不同于服务端 version。
   * R 的响应只可清除它上传的旧 revision；更高 revision 的 U 始终保留并作为 effective 值，下一轮同步后收敛。
   */
  @ColumnInfo(name = "local_revision") val localRevision: Long?,
  /** 本地持久分组键，capture 时映射为当次 AtomicBatch.batchId；不是 receipt 或处理证明。 */
  @ColumnInfo(name = "local_batch_id") val localBatchId: String?,
)

/**
 * OccurrenceOverride identity（scheduleId + occurrenceDate）的 remote/pending 双快照状态行。
 *
 * 表中不保留旧 exception 的时区、timing 或 category 覆盖；完整四原子 Override 只存在于严格 typed snapshot 内。
 */
@Entity(
  tableName = "schedule_v2_occurrence_override_state",
  primaryKeys = ["account_id", "schedule_id", "occurrence_date"],
  indices = [Index(value = ["account_id", "pending_operation", "local_batch_id"])],
)
data class ScheduleV2OccurrenceOverrideStateEntity(
  @ColumnInfo(name = "account_id") val accountId: String,
  @ColumnInfo(name = "schedule_id") val scheduleId: String,
  @ColumnInfo(name = "occurrence_date") val occurrenceDate: Long,
  /** 服务端确认的完整快照；构造 confirmed 时读取，不原样上传。 */
  @ColumnInfo(name = "remote_snapshot_json") val remoteSnapshot: OccurrenceOverrideCurrent?,
  /** 本地待提交操作，仅 UPSERT 或 DELETE；不作为 wire 字段上传。 */
  @ColumnInfo(name = "pending_operation") val pendingOperation: String?,
  /** UPSERT 要上传的完整输入；DELETE 时为 null。 */
  @ColumnInfo(name = "pending_snapshot_json") val pendingSnapshot: OccurrenceOverrideInput?,
  /** DELETE 的本地操作时刻；仅 DELETE 请求上传。 */
  @ColumnInfo(name = "pending_local_modified_at") val pendingLocalModifiedAt: Long?,
  /**
   * 纯本地 compare-and-clear 标识；不上传且不同于服务端 version。
   * R 的响应只可清除它上传的旧 revision；更高 revision 的 U 始终保留并作为 effective 值，下一轮同步后收敛。
   */
  @ColumnInfo(name = "local_revision") val localRevision: Long?,
  /** 本地持久分组键，capture 时映射为当次 AtomicBatch.batchId；不是 receipt 或处理证明。 */
  @ColumnInfo(name = "local_batch_id") val localBatchId: String?,
)
