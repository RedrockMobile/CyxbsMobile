package com.cyxbs.pages.schedule.data.local.room3

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction

/**
 * 一个账号在 Room 内持有的三类 Schedule typed state。
 *
 * 此快照只表达 remote/pending 双侧状态；不附带 cursor、outbox、回执或网络重试信息。
 */
internal data class ScheduleRoomAccountState(
  val categories: List<ScheduleCategoryStateEntity>,
  val schedules: List<ScheduleStateEntity>,
  val occurrenceAdjustments: List<ScheduleOccurrenceAdjustmentStateEntity>,
)

/**
 * Schedule 最小状态持久化薄层。
 *
 * 负责读取三类状态、分配纯本地 revision，以及一次全量替换状态；remote/pending 的业务归约、响应解释由上层
 * repository/applier 负责，网络调用与后续同步触发由上层 repository 负责。
 */
internal class ScheduleRoomStateStore(
  private val database: ScheduleRoomDatabase,
) {
  private val dao: ScheduleRoomDao
    get() = database.scheduleDao()

  /**
   * 在一个只读事务中读取账号全部 typed state。
   *
   * [accountId] 是三类 state 的共同分区键；返回值不做 confirmed/effective 视图合并。
   */
  suspend fun readAccountState(accountId: String): ScheduleRoomAccountState =
    database.withReadTransaction {
      ScheduleRoomAccountState(
        categories = dao.readCategoryStates(accountId),
        schedules = dao.readScheduleStates(accountId),
        occurrenceAdjustments = dao.readOccurrenceAdjustmentStates(accountId),
      )
    }

  /**
   * 以 common applier 成功产出的完整账号状态替换 Room 中三类 state。
   *
   * 这是同步响应唯一的落库入口：同一写事务先清除旧集合再写入新集合，因此服务端已物理删除的资源会随新集合
   * 一起移除。三个列表必须显式传入以避免调用方误清；账号 metadata/localRevisionCounter 不属于替换目标，始终保留。
   * store 不维护请求队列、批次状态或服务端处理进度。
   */
  suspend fun replaceAccountState(
    accountId: String,
    categories: List<ScheduleCategoryStateEntity>,
    schedules: List<ScheduleStateEntity>,
    occurrenceAdjustments: List<ScheduleOccurrenceAdjustmentStateEntity>,
  ) {
    database.withWriteTransaction {
      requireAccount(accountId, categories, schedules, occurrenceAdjustments)
      dao.deleteCategoryStates(accountId)
      dao.deleteScheduleStates(accountId)
      dao.deleteOccurrenceAdjustmentStates(accountId)
      for (state in categories) dao.upsertCategoryState(state)
      for (state in schedules) dao.upsertScheduleState(state)
      for (state in occurrenceAdjustments) dao.upsertOccurrenceAdjustmentState(state)
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

  /**
   * 在一个事务内物理清理账号的全部 Schedule Room 状态。
   *
   * 单次调整先于 Schedule 删除，随后删除 Category 和纯本地 revision 元数据。该操作不影响其他账号，也不重置
   * AccountSettings 中的旧数据迁移版本；失败记录由 repository 在同一维护流程中单独清理。
   */
  suspend fun clearAccountState(accountId: String) {
    database.withWriteTransaction {
      dao.deleteOccurrenceAdjustmentStates(accountId)
      dao.deleteScheduleStates(accountId)
      dao.deleteCategoryStates(accountId)
      dao.deleteAccountMetadata(accountId)
    }
  }

  /** 防止一次事务把其他账号的 state 误写入当前账号分区。 */
  private fun requireAccount(
    accountId: String,
    categories: List<ScheduleCategoryStateEntity>,
    schedules: List<ScheduleStateEntity>,
    occurrenceAdjustments: List<ScheduleOccurrenceAdjustmentStateEntity>,
  ) {
    require(categories.all { it.accountId == accountId }) { "category accountId mismatch" }
    require(schedules.all { it.accountId == accountId }) { "schedule accountId mismatch" }
    require(occurrenceAdjustments.all { it.accountId == accountId }) {
      "occurrence adjustment accountId mismatch"
    }
  }

}
