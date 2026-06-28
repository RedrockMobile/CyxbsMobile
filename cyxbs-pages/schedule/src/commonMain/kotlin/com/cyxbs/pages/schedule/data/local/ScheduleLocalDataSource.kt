package com.cyxbs.pages.schedule.data.local

import com.cyxbs.pages.schedule.data.model.ScheduleEntity
import com.cyxbs.pages.schedule.data.model.ScheduleLocalMeta
import com.cyxbs.pages.schedule.data.model.SchedulePendingOperation

/**
 * todo 本地数据源抽象接口。
 *
 * 首版实现为 [SplitSettingsScheduleLocalDataSource]，后续如需替换成 Room KMP 只需替换实现，
 * 不影响上层同步仓库和 UI。
 */
interface ScheduleLocalDataSource {

  /**
   * 加载当前账号的本地快照。
   *
   * @return 如果本地无有效缓存，返回空 meta + 空列表 + 空 pending；
   *         如果 JSON 解析失败，返回标记 `needsFullRebuild = true` 的 meta。
   */
  suspend fun loadSnapshot(account: String): ScheduleLocalSnapshot

  /**
   * 全量替换本地 todo 快照。
   *
   * 该方法通常在以下场景调用：
   * - 首次从后端 `/list` 初始化。
   * - 本地 `sync_time` 失效后 `/list` 全量重建。
   * - 本地 JSON 损坏后 `/list` 恢复。
   *
   * @param todos 新的完整 todo 列表。
   * @param syncTime 对应的后端全局同步版本。
   * @param preservePending 是否保留现有 pending operations；通常为 true。
   */
  suspend fun replaceAll(
    account: String,
    todos: List<ScheduleEntity>,
    syncTime: Long,
    preservePending: Boolean = true,
  )

  /**
   * 获取当前账号所有 todo。
   */
  suspend fun getAll(account: String): List<ScheduleEntity>

  /**
   * 按 id 查询单条 todo。
   */
  suspend fun getById(account: String, todoId: Long): ScheduleEntity?

  /**
   * 本地新增或更新单条 todo。
   *
   * @param recordPending 是否同时记录 pending operation；离线变更时为 true，同步后写回本地时为 false。
   */
  suspend fun upsertLocal(
    account: String,
    todo: ScheduleEntity,
    recordPending: Boolean,
  )

  /**
   * 本地删除单条 todo。
   *
   * @param recordPending 是否同时记录 pending delete；离线变更时为 true，同步后写回本地时为 false。
   */
  suspend fun deleteLocal(
    account: String,
    todoId: Long,
    recordPending: Boolean,
  )

  /**
   * 获取当前账号所有待同步操作。
   *
   * 同一 todoId 只会有一个 pending operation（已在写入时压缩）。
   */
  suspend fun getPendingOperations(account: String): List<SchedulePendingOperation>

  /**
   * 移除已成功同步的 pending operations。
   *
   * @param todoIds 已成功上传到服务端的 todoId 集合。
   */
  suspend fun removePendingOperations(
    account: String,
    todoIds: Set<Long>,
  )

  /**
   * 标记本地缓存需要全量重建。
   *
   * 下次同步时会优先走 `/list`，而不是增量 `/todos`。
   */
  suspend fun markNeedsFullRebuild(account: String)

  /**
   * 清空指定账号的所有本地 todo 数据和 pending 操作。
   *
   * 通常在退出登录、切换账号时调用。
   */
  suspend fun clearAccount(account: String)

  /** 读取分组候选池；从未存过返回 null（表示需要用默认值初始化）。 */
  suspend fun getCategories(account: String): List<String>?

  /** 保存分组候选池。 */
  suspend fun saveCategories(account: String, categories: List<String>)
}

/**
 * 本地快照读取结果。
 */
data class ScheduleLocalSnapshot(
  /** 元信息；如果是首次读取或损坏，meta 会标记 `needsFullRebuild = true`。 */
  val meta: ScheduleLocalMeta,
  /** 当前账号本地存储的所有 todo。 */
  val todos: List<ScheduleEntity>,
  /** 当前账号待同步的离线操作。 */
  val pendingOperations: List<SchedulePendingOperation>,
)
