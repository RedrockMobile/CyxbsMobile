package com.cyxbs.pages.schedule.data.local

import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.pages.schedule.data.model.ScheduleEntity
import com.cyxbs.pages.schedule.data.model.ScheduleLocalMeta
import com.cyxbs.pages.schedule.data.model.SchedulePendingOperation
import kotlin.time.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 分片 Settings 实现的 todo 本地数据源。
 *
 * 本地存储策略：
 * - meta 单独存一个小 JSON。
 * - todoId 索引分 chunk 存，每个 chunk 最多 100 个 id。
 * - 每条 todo 单独存一个 JSON，key 为 `SP_SCHEDULE_CMP_TODO_{todoId}`。
 * - pendingId 索引分 chunk 存。
 * - 每条 pending operation 单独存一个 JSON，key 为 `SP_SCHEDULE_CMP_PENDING_{todoId}`。
 *
 * 账号隔离：所有读写都通过 [AccountSettings.get(account)] 自动按学号隔离，不需要手动拼接学号到 key。
 */
class SplitSettingsScheduleLocalDataSource : ScheduleLocalDataSource {

  private val json: Json = defaultJson

  override suspend fun loadSnapshot(account: String): ScheduleLocalSnapshot {
    val settings = AccountSettings.get(account)

    // 读取 meta
    val metaJson = settings.getString(ScheduleSettingsKeys.SP_SCHEDULE_CMP_META, "")
    val meta = if (metaJson.isNotEmpty()) {
      try {
        json.decodeFromString<ScheduleLocalMeta>(metaJson)
      } catch (e: Exception) {
        // JSON 损坏，标记需要全量重建
        ScheduleLocalMeta(needsFullRebuild = true)
      }
    } else {
      // 首次使用，无本地缓存
      ScheduleLocalMeta(needsFullRebuild = true)
    }

    // 如果标记需要重建，直接返回空快照
    if (meta.needsFullRebuild) {
      return ScheduleLocalSnapshot(
        meta = meta,
        todos = emptyList(),
        pendingOperations = emptyList(),
      )
    }

    // 读取 todoIds
    val todoIds = readScheduleIds(settings, meta.todoChunkCount)

    // 逐个读取 todo item
    val todos = todoIds.mapNotNull { todoId ->
      val itemJson = settings.getString(ScheduleSettingsKeys.todoItemKey(todoId), "")
      if (itemJson.isEmpty()) return@mapNotNull null
      try {
        json.decodeFromString<ScheduleEntity>(itemJson)
      } catch (e: Exception) {
        null // 单条损坏跳过
      }
    }

    // 读取 pendingIds
    val pendingIds = readPendingIds(settings, meta.pendingChunkCount)

    // 逐个读取 pending operation
    val pendingOps = pendingIds.mapNotNull { todoId ->
      val opJson = settings.getString(ScheduleSettingsKeys.pendingOpKey(todoId), "")
      if (opJson.isEmpty()) return@mapNotNull null
      try {
        json.decodeFromString<SchedulePendingOperation>(opJson)
      } catch (e: Exception) {
        null
      }
    }

    return ScheduleLocalSnapshot(
      meta = meta,
      todos = todos,
      pendingOperations = pendingOps,
    )
  }

  override suspend fun replaceAll(
    account: String,
    todos: List<ScheduleEntity>,
    syncTime: Long,
    preservePending: Boolean,
  ) {
    val settings = AccountSettings.get(account)

    // 读取旧 meta，保留 pending 信息
    val oldMetaJson = settings.getString(ScheduleSettingsKeys.SP_SCHEDULE_CMP_META, "")
    val oldMeta = if (oldMetaJson.isNotEmpty()) {
      try {
        json.decodeFromString<ScheduleLocalMeta>(oldMetaJson)
      } catch (e: Exception) {
        ScheduleLocalMeta()
      }
    } else {
      ScheduleLocalMeta()
    }

    // 读取旧 todoIds，用于清理不再存在的 item
    val oldScheduleIds = readScheduleIds(settings, oldMeta.todoChunkCount)

    // 新 todoIds
    val newScheduleIds = todos.map { it.todoId }

    // 找出需要删除的旧 item key
    val toRemove = oldScheduleIds.filterNot { it in newScheduleIds }
    toRemove.forEach { todoId ->
      settings.remove(ScheduleSettingsKeys.todoItemKey(todoId))
    }

    // 写入新 todo items
    todos.forEach { todo ->
      settings.putString(
        ScheduleSettingsKeys.todoItemKey(todo.todoId),
        json.encodeToString(todo)
      )
    }

    // 写入新 todoIds chunks
    writeScheduleIdsChunks(settings, newScheduleIds)

    // 清理旧 chunk（如果新 chunk 数量比旧的少）
    val newChunkCount = (newScheduleIds.size + ScheduleSettingsKeys.TODO_IDS_CHUNK_SIZE - 1) / ScheduleSettingsKeys.TODO_IDS_CHUNK_SIZE
    if (newChunkCount < oldMeta.todoChunkCount) {
      for (i in newChunkCount until oldMeta.todoChunkCount) {
        settings.remove(ScheduleSettingsKeys.todoIdsChunkKey(i))
      }
    }

    // 决定 pending 策略
    val pendingCount: Int
    val pendingChunkCount: Int
    if (preservePending) {
      pendingCount = oldMeta.pendingCount
      pendingChunkCount = oldMeta.pendingChunkCount
    } else {
      // 不保留 pending，清空
      val oldPendingIds = readPendingIds(settings, oldMeta.pendingChunkCount)
      oldPendingIds.forEach { todoId ->
        settings.remove(ScheduleSettingsKeys.pendingOpKey(todoId))
      }
      for (i in 0 until oldMeta.pendingChunkCount) {
        settings.remove(ScheduleSettingsKeys.pendingIdsChunkKey(i))
      }
      pendingCount = 0
      pendingChunkCount = 0
    }

    // 写入新 meta
    val newMeta = ScheduleLocalMeta(
      schemaVersion = ScheduleLocalMeta.CURRENT_SCHEMA_VERSION,
      syncTime = syncTime,
      needsFullRebuild = false,
      todoCount = newScheduleIds.size,
      todoChunkCount = newChunkCount,
      pendingCount = pendingCount,
      pendingChunkCount = pendingChunkCount,
      nextLocalId = oldMeta.nextLocalId, // 保留旧的 id 生成基准
      lastLocalMutationTime = Clock.System.now().toEpochMilliseconds(),
      lastSuccessfulSyncTime = syncTime,
    )
    settings.putString(ScheduleSettingsKeys.SP_SCHEDULE_CMP_META, json.encodeToString(newMeta))
  }

  override suspend fun getAll(account: String): List<ScheduleEntity> {
    return loadSnapshot(account).todos
  }

  override suspend fun getById(account: String, todoId: Long): ScheduleEntity? {
    val settings = AccountSettings.get(account)
    val itemJson = settings.getString(ScheduleSettingsKeys.todoItemKey(todoId), "")
    if (itemJson.isEmpty()) return null
    return try {
      json.decodeFromString<ScheduleEntity>(itemJson)
    } catch (e: Exception) {
      null
    }
  }

  override suspend fun upsertLocal(
    account: String,
    todo: ScheduleEntity,
    recordPending: Boolean,
  ) {
    val settings = AccountSettings.get(account)

    // 读取当前 meta
    val metaJson = settings.getString(ScheduleSettingsKeys.SP_SCHEDULE_CMP_META, "")
    val meta = if (metaJson.isNotEmpty()) {
      try {
        json.decodeFromString<ScheduleLocalMeta>(metaJson)
      } catch (e: Exception) {
        ScheduleLocalMeta()
      }
    } else {
      ScheduleLocalMeta()
    }

    // 读取当前 todoIds
    val todoIds = readScheduleIds(settings, meta.todoChunkCount).toMutableList()

    // 如果是新 id，加入索引
    if (todo.todoId !in todoIds) {
      todoIds.add(todo.todoId)
    }

    // 写入 todo item
    settings.putString(
      ScheduleSettingsKeys.todoItemKey(todo.todoId),
      json.encodeToString(todo)
    )

    // 写回 todoIds chunks
    writeScheduleIdsChunks(settings, todoIds)

    // 清理旧 chunk（如果新 chunk 数量比旧的少，例如删除后再 upsert）
    val newScheduleChunkCount = (todoIds.size + ScheduleSettingsKeys.TODO_IDS_CHUNK_SIZE - 1) / ScheduleSettingsKeys.TODO_IDS_CHUNK_SIZE
    if (newScheduleChunkCount < meta.todoChunkCount) {
      for (i in newScheduleChunkCount until meta.todoChunkCount) {
        settings.remove(ScheduleSettingsKeys.todoIdsChunkKey(i))
      }
    }

    // 如果需要记录 pending
    var newPendingCount = meta.pendingCount
    var newPendingChunkCount = meta.pendingChunkCount
    if (recordPending) {
      val pendingIds = readPendingIds(settings, meta.pendingChunkCount).toMutableSet()
      val isNewPending = todo.todoId !in pendingIds
      if (isNewPending) {
        pendingIds.add(todo.todoId)
      }

      // 写入或更新 pending operation
      val now = Clock.System.now().toEpochMilliseconds()
      val existingPending = if (!isNewPending) {
        val opJson = settings.getString(ScheduleSettingsKeys.pendingOpKey(todo.todoId), "")
        if (opJson.isNotEmpty()) {
          try {
            json.decodeFromString<SchedulePendingOperation>(opJson)
          } catch (e: Exception) {
            null
          }
        } else {
          null
        }
      } else {
        null
      }

      val pendingOp = SchedulePendingOperation(
        todoId = todo.todoId,
        kind = SchedulePendingOperation.Kind.UPSERT,
        todo = todo,
        createdAt = existingPending?.createdAt ?: now,
        updatedAt = now,
      )
      settings.putString(
        ScheduleSettingsKeys.pendingOpKey(todo.todoId),
        json.encodeToString(pendingOp)
      )

      // 写回 pendingIds chunks
      writePendingIdsChunks(settings, pendingIds.toList())

      newPendingCount = pendingIds.size
      newPendingChunkCount = (pendingIds.size + ScheduleSettingsKeys.PENDING_IDS_CHUNK_SIZE - 1) / ScheduleSettingsKeys.PENDING_IDS_CHUNK_SIZE

      // 清理旧 pending chunk
      if (newPendingChunkCount < meta.pendingChunkCount) {
        for (i in newPendingChunkCount until meta.pendingChunkCount) {
          settings.remove(ScheduleSettingsKeys.pendingIdsChunkKey(i))
        }
      }
    }

    // 更新 meta
    val newMeta = meta.copy(
      todoCount = todoIds.size,
      todoChunkCount = newScheduleChunkCount,
      pendingCount = newPendingCount,
      pendingChunkCount = newPendingChunkCount,
      lastLocalMutationTime = Clock.System.now().toEpochMilliseconds(),
    )
    settings.putString(ScheduleSettingsKeys.SP_SCHEDULE_CMP_META, json.encodeToString(newMeta))
  }

  override suspend fun deleteLocal(
    account: String,
    todoId: Long,
    recordPending: Boolean,
  ) {
    val settings = AccountSettings.get(account)

    // 读取当前 meta
    val metaJson = settings.getString(ScheduleSettingsKeys.SP_SCHEDULE_CMP_META, "")
    val meta = if (metaJson.isNotEmpty()) {
      try {
        json.decodeFromString<ScheduleLocalMeta>(metaJson)
      } catch (e: Exception) {
        ScheduleLocalMeta()
      }
    } else {
      ScheduleLocalMeta()
    }

    // 读取当前 todoIds
    val todoIds = readScheduleIds(settings, meta.todoChunkCount).toMutableList()

    // 从索引移除
    todoIds.remove(todoId)

    // 删除 item key
    settings.remove(ScheduleSettingsKeys.todoItemKey(todoId))

    // 写回 todoIds chunks
    writeScheduleIdsChunks(settings, todoIds)

    // 清理旧 chunk
    val newScheduleChunkCount = (todoIds.size + ScheduleSettingsKeys.TODO_IDS_CHUNK_SIZE - 1) / ScheduleSettingsKeys.TODO_IDS_CHUNK_SIZE
    if (newScheduleChunkCount < meta.todoChunkCount) {
      for (i in newScheduleChunkCount until meta.todoChunkCount) {
        settings.remove(ScheduleSettingsKeys.todoIdsChunkKey(i))
      }
    }

    // 如果需要记录 pending delete
    var newPendingCount = meta.pendingCount
    var newPendingChunkCount = meta.pendingChunkCount
    if (recordPending) {
      val pendingIds = readPendingIds(settings, meta.pendingChunkCount).toMutableSet()
      val isNewPending = todoId !in pendingIds
      if (isNewPending) {
        pendingIds.add(todoId)
      }

      // 写入或更新 pending delete
      val now = Clock.System.now().toEpochMilliseconds()
      val existingPending = if (!isNewPending) {
        val opJson = settings.getString(ScheduleSettingsKeys.pendingOpKey(todoId), "")
        if (opJson.isNotEmpty()) {
          try {
            json.decodeFromString<SchedulePendingOperation>(opJson)
          } catch (e: Exception) {
            null
          }
        } else {
          null
        }
      } else {
        null
      }

      val pendingOp = SchedulePendingOperation(
        todoId = todoId,
        kind = SchedulePendingOperation.Kind.DELETE,
        todo = null,
        createdAt = existingPending?.createdAt ?: now,
        updatedAt = now,
      )
      settings.putString(
        ScheduleSettingsKeys.pendingOpKey(todoId),
        json.encodeToString(pendingOp)
      )

      // 写回 pendingIds chunks
      writePendingIdsChunks(settings, pendingIds.toList())

      newPendingCount = pendingIds.size
      newPendingChunkCount = (pendingIds.size + ScheduleSettingsKeys.PENDING_IDS_CHUNK_SIZE - 1) / ScheduleSettingsKeys.PENDING_IDS_CHUNK_SIZE

      // 清理旧 pending chunk
      if (newPendingChunkCount < meta.pendingChunkCount) {
        for (i in newPendingChunkCount until meta.pendingChunkCount) {
          settings.remove(ScheduleSettingsKeys.pendingIdsChunkKey(i))
        }
      }
    }

    // 更新 meta
    val newMeta = meta.copy(
      todoCount = todoIds.size,
      todoChunkCount = newScheduleChunkCount,
      pendingCount = newPendingCount,
      pendingChunkCount = newPendingChunkCount,
      lastLocalMutationTime = Clock.System.now().toEpochMilliseconds(),
    )
    settings.putString(ScheduleSettingsKeys.SP_SCHEDULE_CMP_META, json.encodeToString(newMeta))
  }

  override suspend fun getPendingOperations(account: String): List<SchedulePendingOperation> {
    return loadSnapshot(account).pendingOperations
  }

  override suspend fun removePendingOperations(
    account: String,
    todoIds: Set<Long>,
  ) {
    val settings = AccountSettings.get(account)

    // 读取 meta
    val metaJson = settings.getString(ScheduleSettingsKeys.SP_SCHEDULE_CMP_META, "")
    val meta = if (metaJson.isNotEmpty()) {
      try {
        json.decodeFromString<ScheduleLocalMeta>(metaJson)
      } catch (e: Exception) {
        ScheduleLocalMeta()
      }
    } else {
      ScheduleLocalMeta()
    }

    // 读取 pendingIds
    val pendingIds = readPendingIds(settings, meta.pendingChunkCount).toMutableList()

    // 移除指定 id
    pendingIds.removeAll(todoIds)

    // 删除对应 pending op key
    todoIds.forEach { todoId ->
      settings.remove(ScheduleSettingsKeys.pendingOpKey(todoId))
    }

    // 写回 pendingIds chunks
    writePendingIdsChunks(settings, pendingIds)

    // 清理旧 chunk
    val newPendingChunkCount = (pendingIds.size + ScheduleSettingsKeys.PENDING_IDS_CHUNK_SIZE - 1) / ScheduleSettingsKeys.PENDING_IDS_CHUNK_SIZE
    if (newPendingChunkCount < meta.pendingChunkCount) {
      for (i in newPendingChunkCount until meta.pendingChunkCount) {
        settings.remove(ScheduleSettingsKeys.pendingIdsChunkKey(i))
      }
    }

    // 更新 meta
    val newMeta = meta.copy(
      pendingCount = pendingIds.size,
      pendingChunkCount = newPendingChunkCount,
    )
    settings.putString(ScheduleSettingsKeys.SP_SCHEDULE_CMP_META, json.encodeToString(newMeta))
  }

  override suspend fun markNeedsFullRebuild(account: String) {
    val settings = AccountSettings.get(account)

    // 读取 meta
    val metaJson = settings.getString(ScheduleSettingsKeys.SP_SCHEDULE_CMP_META, "")
    val meta = if (metaJson.isNotEmpty()) {
      try {
        json.decodeFromString<ScheduleLocalMeta>(metaJson)
      } catch (e: Exception) {
        ScheduleLocalMeta()
      }
    } else {
      ScheduleLocalMeta()
    }

    // 标记 needsFullRebuild
    val newMeta = meta.copy(needsFullRebuild = true)
    settings.putString(ScheduleSettingsKeys.SP_SCHEDULE_CMP_META, json.encodeToString(newMeta))
  }

  override suspend fun clearAccount(account: String) {
    val settings = AccountSettings.get(account)

    // 读取 meta
    val metaJson = settings.getString(ScheduleSettingsKeys.SP_SCHEDULE_CMP_META, "")
    val meta = if (metaJson.isNotEmpty()) {
      try {
        json.decodeFromString<ScheduleLocalMeta>(metaJson)
      } catch (e: Exception) {
        ScheduleLocalMeta()
      }
    } else {
      ScheduleLocalMeta()
    }

    // 读取 todoIds 并删除所有 item
    val todoIds = readScheduleIds(settings, meta.todoChunkCount)
    todoIds.forEach { todoId ->
      settings.remove(ScheduleSettingsKeys.todoItemKey(todoId))
    }

    // 删除所有 todoIds chunks
    for (i in 0 until meta.todoChunkCount) {
      settings.remove(ScheduleSettingsKeys.todoIdsChunkKey(i))
    }

    // 读取 pendingIds 并删除所有 pending op
    val pendingIds = readPendingIds(settings, meta.pendingChunkCount)
    pendingIds.forEach { todoId ->
      settings.remove(ScheduleSettingsKeys.pendingOpKey(todoId))
    }

    // 删除所有 pendingIds chunks
    for (i in 0 until meta.pendingChunkCount) {
      settings.remove(ScheduleSettingsKeys.pendingIdsChunkKey(i))
    }

    // 删除 meta
    settings.remove(ScheduleSettingsKeys.SP_SCHEDULE_CMP_META)

    // 删除分组候选池
    settings.remove(ScheduleSettingsKeys.SP_SCHEDULE_CMP_CATEGORIES)
  }

  override suspend fun getCategories(account: String): List<String>? {
    val settings = AccountSettings.get(account)
    val s = settings.getString(ScheduleSettingsKeys.SP_SCHEDULE_CMP_CATEGORIES, "")
    if (s.isEmpty()) return null
    return try {
      json.decodeFromString<List<String>>(s)
    } catch (e: Exception) {
      null
    }
  }

  override suspend fun saveCategories(account: String, categories: List<String>) {
    val settings = AccountSettings.get(account)
    settings.putString(ScheduleSettingsKeys.SP_SCHEDULE_CMP_CATEGORIES, json.encodeToString(categories))
  }

  // --- 私有辅助方法 ---

  private fun readScheduleIds(settings: com.russhwolf.settings.Settings, chunkCount: Int): List<Long> {
    val allIds = mutableListOf<Long>()
    for (i in 0 until chunkCount) {
      val chunkJson = settings.getString(ScheduleSettingsKeys.todoIdsChunkKey(i), "")
      if (chunkJson.isEmpty()) continue
      try {
        val chunk = json.decodeFromString<List<Long>>(chunkJson)
        allIds.addAll(chunk)
      } catch (e: Exception) {
        // chunk 损坏跳过
      }
    }
    return allIds
  }

  private fun writeScheduleIdsChunks(settings: com.russhwolf.settings.Settings, todoIds: List<Long>) {
    val chunks = todoIds.chunked(ScheduleSettingsKeys.TODO_IDS_CHUNK_SIZE)
    chunks.forEachIndexed { index, chunk ->
      settings.putString(
        ScheduleSettingsKeys.todoIdsChunkKey(index),
        json.encodeToString(chunk)
      )
    }
  }

  private fun readPendingIds(settings: com.russhwolf.settings.Settings, chunkCount: Int): List<Long> {
    val allIds = mutableListOf<Long>()
    for (i in 0 until chunkCount) {
      val chunkJson = settings.getString(ScheduleSettingsKeys.pendingIdsChunkKey(i), "")
      if (chunkJson.isEmpty()) continue
      try {
        val chunk = json.decodeFromString<List<Long>>(chunkJson)
        allIds.addAll(chunk)
      } catch (e: Exception) {
        // chunk 损坏跳过
      }
    }
    return allIds
  }

  private fun writePendingIdsChunks(settings: com.russhwolf.settings.Settings, pendingIds: List<Long>) {
    val chunks = pendingIds.chunked(ScheduleSettingsKeys.PENDING_IDS_CHUNK_SIZE)
    chunks.forEachIndexed { index, chunk ->
      settings.putString(
        ScheduleSettingsKeys.pendingIdsChunkKey(index),
        json.encodeToString(chunk)
      )
    }
  }
}
