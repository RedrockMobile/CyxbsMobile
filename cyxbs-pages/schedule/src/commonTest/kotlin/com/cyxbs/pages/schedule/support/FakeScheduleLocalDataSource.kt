package com.cyxbs.pages.schedule.support

import com.cyxbs.pages.schedule.data.local.ScheduleLocalDataSource
import com.cyxbs.pages.schedule.data.local.ScheduleLocalSnapshot
import com.cyxbs.pages.schedule.data.model.ScheduleEntity
import com.cyxbs.pages.schedule.data.model.ScheduleLocalMeta
import com.cyxbs.pages.schedule.data.model.SchedulePendingOperation

/**
 * 内存版假本地数据源，用于同步仓库集成测试。
 */
class FakeScheduleLocalDataSource : ScheduleLocalDataSource {

  private val todos = linkedMapOf<Long, ScheduleEntity>()
  private val pending = linkedMapOf<Long, SchedulePendingOperation>()
  private var meta = ScheduleLocalMeta()
  private var categories: List<String>? = null

  override suspend fun loadSnapshot(account: String): ScheduleLocalSnapshot =
    ScheduleLocalSnapshot(meta, todos.values.toList(), pending.values.toList())

  override suspend fun replaceAll(
    account: String,
    todos: List<ScheduleEntity>,
    syncTime: Long,
    preservePending: Boolean,
  ) {
    this.todos.clear()
    todos.forEach { this.todos[it.todoId] = it }
    if (!preservePending) pending.clear()
    meta = meta.copy(syncTime = syncTime, needsFullRebuild = false, todoCount = todos.size)
  }

  override suspend fun getAll(account: String): List<ScheduleEntity> = todos.values.toList()

  override suspend fun getById(account: String, todoId: Long): ScheduleEntity? = todos[todoId]

  override suspend fun upsertLocal(account: String, todo: ScheduleEntity, recordPending: Boolean) {
    todos[todo.todoId] = todo
    if (recordPending) {
      pending[todo.todoId] = SchedulePendingOperation(
        todoId = todo.todoId,
        kind = SchedulePendingOperation.Kind.UPSERT,
        todo = todo,
        createdAt = 0L,
      )
    }
  }

  override suspend fun deleteLocal(account: String, todoId: Long, recordPending: Boolean) {
    todos.remove(todoId)
    if (recordPending) {
      pending[todoId] = SchedulePendingOperation(
        todoId = todoId,
        kind = SchedulePendingOperation.Kind.DELETE,
        todo = null,
        createdAt = 0L,
      )
    }
  }

  override suspend fun getPendingOperations(account: String): List<SchedulePendingOperation> =
    pending.values.toList()

  override suspend fun removePendingOperations(account: String, todoIds: Set<Long>) {
    todoIds.forEach { pending.remove(it) }
  }

  override suspend fun markNeedsFullRebuild(account: String) {
    meta = meta.copy(needsFullRebuild = true)
  }

  override suspend fun clearAccount(account: String) {
    todos.clear(); pending.clear(); meta = ScheduleLocalMeta(); categories = null
  }

  override suspend fun getCategories(account: String): List<String>? = categories

  override suspend fun saveCategories(account: String, categories: List<String>) {
    this.categories = categories
  }

  /** 测试辅助：直接设置 meta.syncTime（用于走增量分支）。 */
  fun seedSyncTime(syncTime: Long) {
    meta = meta.copy(syncTime = syncTime)
  }
}
