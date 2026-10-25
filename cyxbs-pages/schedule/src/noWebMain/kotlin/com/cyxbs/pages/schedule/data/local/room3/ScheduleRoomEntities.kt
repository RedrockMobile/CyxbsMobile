package com.cyxbs.pages.schedule.data.local.room3

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import com.cyxbs.pages.schedule.data.remote.CategoryInput
import com.cyxbs.pages.schedule.data.remote.OccurrenceAdjustmentInput
import com.cyxbs.pages.schedule.data.remote.ScheduleInput

/** 待提交侧仅有的操作类型；没有逐条 outbox、回执或重试阶段。 */
internal object SchedulePendingOperation {
  const val UPSERT = "UPSERT"
  const val DELETE = "DELETE"
}

/**
 * 每个账号的本地修订号分配器。
 *
 * 该值只服务 pending compare-and-clear，和服务端资源 version、原子字段 modifiedAt 互不关联。
 */
@Entity(tableName = "schedule_account_metadata", primaryKeys = ["account_id"])
data class ScheduleAccountMetadataEntity(
  @ColumnInfo(name = "account_id") val accountId: String,
  /** 纯本地 revision 计数器；每次事务内先递增再分配，不上传，也不代表服务端 version。 */
  @ColumnInfo(name = "local_revision_counter") val localRevisionCounter: Long = 0,
)

/**
 * Category identity 的 remote/pending 双快照状态行。
 *
 * Snapshot 只在严格 typed codec 边界编码、解码；Room 通过 TypeConverter 存为 JSON。DELETE 时 pending snapshot 为 null，
 * 只使用 identity、localModifiedAt 与 localRevision。
 */
@Entity(
  tableName = "schedule_category_state",
  primaryKeys = ["account_id", "category_id"],
  indices = [Index(value = ["account_id", "pending_operation"])],
)
data class ScheduleCategoryStateEntity(
  @ColumnInfo(name = "account_id") val accountId: String,
  @ColumnInfo(name = "category_id") val categoryId: String,
  /** 服务端确认的完整快照；构造 confirmed 时读取，不原样上传。 */
  @ColumnInfo(name = "remote_snapshot_json") val remoteSnapshot: CategoryInput?,
  /** 本地待提交操作，仅 UPSERT 或 DELETE；不作为 wire 字段上传。 */
  @ColumnInfo(name = "pending_operation") val pendingOperation: String?,
  /** UPSERT 要上传的完整输入；DELETE 时为 null。 */
  @ColumnInfo(name = "pending_snapshot_json") val pendingSnapshot: CategoryInput?,
  /** DELETE 的本地操作时刻；只用于失败记录和诊断，不进入服务端删除合同。 */
  @ColumnInfo(name = "pending_local_modified_at") val pendingLocalModifiedAt: Long?,
  /**
   * 纯本地 compare-and-clear 标识；不上传且不同于服务端 version。
   * R 的响应只可清除它上传的旧 revision；更高 revision 的 U 始终保留并作为 effective 值，下一轮同步后收敛。
   */
  @ColumnInfo(name = "local_revision") val localRevision: Long?,
)

/**
 * Schedule identity 的 remote/pending 双快照状态行。
 *
 * recurrence、reminder 和 AtomicField 都属于 typed snapshot，不拆出 child table 或 JSON 以外的持久化状态机。
 */
@Entity(
  tableName = "schedule_state",
  primaryKeys = ["account_id", "schedule_id"],
  indices = [Index(value = ["account_id", "pending_operation"])],
)
data class ScheduleStateEntity(
  @ColumnInfo(name = "account_id") val accountId: String,
  @ColumnInfo(name = "schedule_id") val scheduleId: String,
  /** 服务端确认的完整快照；构造 confirmed 时读取，不原样上传。 */
  @ColumnInfo(name = "remote_snapshot_json") val remoteSnapshot: ScheduleInput?,
  /** 本地待提交操作，仅 UPSERT 或 DELETE；不作为 wire 字段上传。 */
  @ColumnInfo(name = "pending_operation") val pendingOperation: String?,
  /** UPSERT 要上传的完整输入；DELETE 时为 null。 */
  @ColumnInfo(name = "pending_snapshot_json") val pendingSnapshot: ScheduleInput?,
  /** DELETE 的本地操作时刻；只用于失败记录和诊断，不进入服务端删除合同。 */
  @ColumnInfo(name = "pending_local_modified_at") val pendingLocalModifiedAt: Long?,
  /**
   * 纯本地 compare-and-clear 标识；不上传且不同于服务端 version。
   * R 的响应只可清除它上传的旧 revision；更高 revision 的 U 始终保留并作为 effective 值，下一轮同步后收敛。
   */
  @ColumnInfo(name = "local_revision") val localRevision: Long?,
)

/**
 * OccurrenceAdjustment 的 remote/pending 双快照状态行。
 *
 * [localId] 是端内唯一主键，只在首次创建请求中临时上传以关联响应，服务端不会保存；逻辑槽单独建唯一索引，
 * 避免同一账号在本地生成两个有效调整。服务端物理删除确认后该行会被移除。
 */
@Entity(
  tableName = "schedule_occurrence_adjustment_state",
  primaryKeys = ["account_id", "local_id"],
  indices = [
    Index(value = ["account_id", "pending_operation"]),
    Index(value = ["account_id", "schedule_id", "original_occurrence_date"], unique = true),
  ],
)
data class ScheduleOccurrenceAdjustmentStateEntity(
  @ColumnInfo(name = "account_id") val accountId: String,
  @ColumnInfo(name = "local_id") val localId: String,
  @ColumnInfo(name = "schedule_id") val scheduleId: String,
  @ColumnInfo(name = "original_occurrence_date") val originalOccurrenceDate: Long,
  /** 服务端确认的 live 快照；构造 confirmed 时只上传 remote id/version。 */
  @ColumnInfo(name = "remote_snapshot_json") val remoteSnapshot: OccurrenceAdjustmentInput?,
  /** 本地待提交操作，仅 UPSERT 或 DELETE；不作为 wire 字段上传。 */
  @ColumnInfo(name = "pending_operation") val pendingOperation: String?,
  /** UPSERT 要上传的完整输入；DELETE 时为 null。 */
  @ColumnInfo(name = "pending_snapshot_json") val pendingSnapshot: OccurrenceAdjustmentInput?,
  /** DELETE 的本地操作时刻；只用于失败记录和诊断，不进入服务端删除合同。 */
  @ColumnInfo(name = "pending_local_modified_at") val pendingLocalModifiedAt: Long?,
  /**
   * 纯本地 compare-and-clear 标识；不上传且不同于服务端 version。
   * R 的响应只可清除它上传的旧 revision；更高 revision 的 U 始终保留并作为 effective 值，下一轮同步后收敛。
   */
  @ColumnInfo(name = "local_revision") val localRevision: Long?,
)
