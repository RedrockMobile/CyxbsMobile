package com.cyxbs.pages.schedule.data.local.room3

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update

/**
 * 新协议的最小 Room 访问边界。
 *
 * DAO 只读写完整状态行和本地 revision；JSON 的严格 typed 解码、remote/pending 合并、网络重试都不属于此层。
 */
@Dao
abstract class ScheduleV2RoomDao {
  /** 读取账号内所有 category 状态行。 */
  @Query("SELECT * FROM schedule_v2_category_state WHERE account_id = :accountId ORDER BY category_id")
  abstract suspend fun readCategoryStates(accountId: String): List<ScheduleV2CategoryStateEntity>

  /** 查询一个 category identity 当前的完整状态行。 */
  @Query("SELECT * FROM schedule_v2_category_state WHERE account_id = :accountId AND category_id = :categoryId")
  abstract suspend fun findCategoryState(accountId: String, categoryId: String): ScheduleV2CategoryStateEntity?

  /** 按 identity 插入或更新完整 category 状态行，不使用 REPLACE 避免隐式删除。 */
  @Transaction
  open suspend fun upsertCategoryState(entity: ScheduleV2CategoryStateEntity) {
    if (findCategoryState(entity.accountId, entity.categoryId) == null) insertCategoryState(entity) else updateCategoryState(entity)
  }

  @Insert
  protected abstract suspend fun insertCategoryState(entity: ScheduleV2CategoryStateEntity)

  @Update
  protected abstract suspend fun updateCategoryState(entity: ScheduleV2CategoryStateEntity): Int

  /** 仅物理移除已无 remote/pending 侧的 category 行；不能用于表达业务 DELETE。 */
  @Query("DELETE FROM schedule_v2_category_state WHERE account_id = :accountId AND category_id = :categoryId")
  abstract suspend fun deleteCategoryState(accountId: String, categoryId: String): Int

  /** 物理移除账号内全部 Category state；仅用于完整账号状态替换。 */
  @Query("DELETE FROM schedule_v2_category_state WHERE account_id = :accountId")
  abstract suspend fun deleteCategoryStates(accountId: String): Int

  /** 读取账号内所有 Schedule 状态行。 */
  @Query("SELECT * FROM schedule_v2_schedule_state WHERE account_id = :accountId ORDER BY schedule_id")
  abstract suspend fun readScheduleStates(accountId: String): List<ScheduleV2ScheduleStateEntity>

  /** 查询一个 Schedule identity 当前的完整状态行。 */
  @Query("SELECT * FROM schedule_v2_schedule_state WHERE account_id = :accountId AND schedule_id = :scheduleId")
  abstract suspend fun findScheduleState(accountId: String, scheduleId: String): ScheduleV2ScheduleStateEntity?

  /** 按 identity 插入或更新完整 Schedule 状态行。 */
  @Transaction
  open suspend fun upsertScheduleState(entity: ScheduleV2ScheduleStateEntity) {
    if (findScheduleState(entity.accountId, entity.scheduleId) == null) insertScheduleState(entity) else updateScheduleState(entity)
  }

  @Insert
  protected abstract suspend fun insertScheduleState(entity: ScheduleV2ScheduleStateEntity)

  @Update
  protected abstract suspend fun updateScheduleState(entity: ScheduleV2ScheduleStateEntity): Int

  /** 仅物理移除已无 remote/pending 侧的 Schedule 行。 */
  @Query("DELETE FROM schedule_v2_schedule_state WHERE account_id = :accountId AND schedule_id = :scheduleId")
  abstract suspend fun deleteScheduleState(accountId: String, scheduleId: String): Int

  /** 物理移除账号内全部 Schedule state；仅用于完整账号状态替换。 */
  @Query("DELETE FROM schedule_v2_schedule_state WHERE account_id = :accountId")
  abstract suspend fun deleteScheduleStates(accountId: String): Int

  /** 读取账号内所有 OccurrenceOverride 状态行。 */
  @Query("SELECT * FROM schedule_v2_occurrence_override_state WHERE account_id = :accountId ORDER BY schedule_id, occurrence_date")
  abstract suspend fun readOccurrenceOverrideStates(accountId: String): List<ScheduleV2OccurrenceOverrideStateEntity>

  /** 查询一个 `scheduleId + occurrenceDate` Override identity 的完整状态行。 */
  @Query(
    "SELECT * FROM schedule_v2_occurrence_override_state WHERE account_id = :accountId " +
        "AND schedule_id = :scheduleId AND occurrence_date = :occurrenceDate",
  )
  abstract suspend fun findOccurrenceOverrideState(
    accountId: String,
    scheduleId: String,
    occurrenceDate: Long,
  ): ScheduleV2OccurrenceOverrideStateEntity?

  /** 按 identity 插入或更新完整 OccurrenceOverride 状态行。 */
  @Transaction
  open suspend fun upsertOccurrenceOverrideState(entity: ScheduleV2OccurrenceOverrideStateEntity) {
    if (findOccurrenceOverrideState(entity.accountId, entity.scheduleId, entity.occurrenceDate) == null) {
      insertOccurrenceOverrideState(entity)
    } else {
      updateOccurrenceOverrideState(entity)
    }
  }

  @Insert
  protected abstract suspend fun insertOccurrenceOverrideState(entity: ScheduleV2OccurrenceOverrideStateEntity)

  @Update
  protected abstract suspend fun updateOccurrenceOverrideState(entity: ScheduleV2OccurrenceOverrideStateEntity): Int

  /** 仅物理移除已无 remote/pending 侧的 Override 行。 */
  @Query(
    "DELETE FROM schedule_v2_occurrence_override_state WHERE account_id = :accountId " +
        "AND schedule_id = :scheduleId AND occurrence_date = :occurrenceDate",
  )
  abstract suspend fun deleteOccurrenceOverrideState(accountId: String, scheduleId: String, occurrenceDate: Long): Int

  /** 物理移除账号内全部 OccurrenceOverride state；仅用于完整账号状态替换。 */
  @Query("DELETE FROM schedule_v2_occurrence_override_state WHERE account_id = :accountId")
  abstract suspend fun deleteOccurrenceOverrideStates(accountId: String): Int

  /** 按 localBatchId 读取 category pending 组成员。 */
  @Query(
    "SELECT * FROM schedule_v2_category_state WHERE account_id = :accountId AND local_batch_id = :localBatchId " +
        "AND pending_operation IS NOT NULL ORDER BY category_id",
  )
  abstract suspend fun readPendingCategoriesForBatch(accountId: String, localBatchId: String): List<ScheduleV2CategoryStateEntity>

  /** 按 localBatchId 读取 Schedule pending 组成员。 */
  @Query(
    "SELECT * FROM schedule_v2_schedule_state WHERE account_id = :accountId AND local_batch_id = :localBatchId " +
        "AND pending_operation IS NOT NULL ORDER BY schedule_id",
  )
  abstract suspend fun readPendingSchedulesForBatch(accountId: String, localBatchId: String): List<ScheduleV2ScheduleStateEntity>

  /** 按 localBatchId 读取 OccurrenceOverride pending 组成员。 */
  @Query(
    "SELECT * FROM schedule_v2_occurrence_override_state WHERE account_id = :accountId " +
        "AND local_batch_id = :localBatchId AND pending_operation IS NOT NULL ORDER BY schedule_id, occurrence_date",
  )
  abstract suspend fun readPendingOccurrenceOverridesForBatch(
    accountId: String,
    localBatchId: String,
  ): List<ScheduleV2OccurrenceOverrideStateEntity>

  /** 只在首次分配时创建账号计数器，不覆盖既有值。 */
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  protected abstract suspend fun insertAccountMetadataIfAbsent(entity: ScheduleV2AccountMetadataEntity): Long

  /** 在当前 SQL 事务内推进账号的纯本地 revision 计数器。 */
  @Query(
    "UPDATE schedule_v2_account_metadata SET local_revision_counter = local_revision_counter + 1 " +
        "WHERE account_id = :accountId",
  )
  protected abstract suspend fun incrementLocalRevisionCounter(accountId: String): Int

  /** 读取当前账号的纯本地 revision 计数器。 */
  @Query("SELECT local_revision_counter FROM schedule_v2_account_metadata WHERE account_id = :accountId")
  protected abstract suspend fun readLocalRevisionCounter(accountId: String): Long

  /**
   * 原子分配单调递增的 localRevision。
   *
   * 它只用于 pending compare-and-clear，不能上传为服务端 version 或 AtomicField modifiedAt。
   */
  @Transaction
  open suspend fun allocateNextLocalRevision(accountId: String): Long {
    insertAccountMetadataIfAbsent(ScheduleV2AccountMetadataEntity(accountId))
    check(incrementLocalRevisionCounter(accountId) == 1) { "schedule v2 account metadata is missing" }
    return readLocalRevisionCounter(accountId)
  }
}
