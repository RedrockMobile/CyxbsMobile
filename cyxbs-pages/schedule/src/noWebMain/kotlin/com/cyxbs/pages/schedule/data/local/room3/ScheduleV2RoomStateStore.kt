package com.cyxbs.pages.schedule.data.local.room3

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction

/**
 * 一个账号在 Room 内持有的三类 Schedule v2 typed state。
 *
 * 此快照只表达 remote/pending 双侧状态；不附带 cursor、outbox、回执或网络重试信息。
 */
internal data class ScheduleV2RoomAccountState(
  val categories: List<ScheduleV2CategoryStateEntity>,
  val schedules: List<ScheduleV2ScheduleStateEntity>,
  val occurrenceOverrides: List<ScheduleV2OccurrenceOverrideStateEntity>,
)

/**
 * Schedule v2 最小状态持久化薄层。
 *
 * 负责读取三类状态、分配纯本地 revision，以及一次全量替换状态；remote/pending 的业务归约、响应解释由上层
 * repository/applier 负责，网络调用与后续同步触发由上层 repository 负责。
 */
internal class ScheduleV2RoomStateStore(
  private val database: ScheduleRoomDatabase,
) {
  private val dao: ScheduleV2RoomDao
    get() = database.scheduleV2Dao()

  /**
   * 在一个只读事务中读取账号全部 typed state。
   *
   * [accountId] 是三类 state 的共同分区键；返回值不做 confirmed/effective 视图合并。
   */
  suspend fun readAccountState(accountId: String): ScheduleV2RoomAccountState =
    database.withReadTransaction {
      ScheduleV2RoomAccountState(
        categories = dao.readCategoryStates(accountId),
        schedules = dao.readScheduleStates(accountId),
        occurrenceOverrides = dao.readOccurrenceOverrideStates(accountId),
      )
    }

  /**
   * 以 common applier 成功产出的完整账号状态替换 Room 中三类 state。
   *
   * 这是同步响应唯一的落库入口：同一写事务先清除旧集合再写入新集合，因此已从新集合消失的 tombstone identity 会
   * 删除，atomic 结果也不会出现半批。三个列表必须显式传入以避免调用方误清；账号 metadata/localRevisionCounter
   * 不属于替换目标，始终保留。行内 localBatchId 只表示原子组成员，common planner 会映射它为 wire
   * AtomicBatch.batchId；它不代表 receipt 或服务端进度。
   */
  suspend fun replaceAccountState(
    accountId: String,
    categories: List<ScheduleV2CategoryStateEntity>,
    schedules: List<ScheduleV2ScheduleStateEntity>,
    occurrenceOverrides: List<ScheduleV2OccurrenceOverrideStateEntity>,
  ) {
    database.withWriteTransaction {
      requireAccount(accountId, categories, schedules, occurrenceOverrides)
      dao.deleteCategoryStates(accountId)
      dao.deleteScheduleStates(accountId)
      dao.deleteOccurrenceOverrideStates(accountId)
      for (state in categories) dao.upsertCategoryState(state)
      for (state in schedules) dao.upsertScheduleState(state)
      for (state in occurrenceOverrides) dao.upsertOccurrenceOverrideState(state)
    }
  }

  /**
   * 在短写事务内分配一个单调递增的 localRevision。
   *
   * 返回值只用于本地 compare-and-clear，绝不能作为服务端 version 上传。本方法只推进 metadata counter，不读写
   * state；调用方在自身 Mutex 中将该值附到完整 state、归约后再通过 [replaceAccountState] 一次落库。NoOp、
   * Rejected 或进程中断留下的 revision 间隙没有业务语义。
   */
  suspend fun allocateLocalRevision(accountId: String): Long =
    database.withWriteTransaction { dao.allocateNextLocalRevision(accountId) }

  /** 防止一次事务把其他账号的 state 误写入当前账号分区。 */
  private fun requireAccount(
    accountId: String,
    categories: List<ScheduleV2CategoryStateEntity>,
    schedules: List<ScheduleV2ScheduleStateEntity>,
    occurrenceOverrides: List<ScheduleV2OccurrenceOverrideStateEntity>,
  ) {
    require(categories.all { it.accountId == accountId }) { "category accountId mismatch" }
    require(schedules.all { it.accountId == accountId }) { "schedule accountId mismatch" }
    require(occurrenceOverrides.all { it.accountId == accountId }) { "occurrence override accountId mismatch" }
  }

}
