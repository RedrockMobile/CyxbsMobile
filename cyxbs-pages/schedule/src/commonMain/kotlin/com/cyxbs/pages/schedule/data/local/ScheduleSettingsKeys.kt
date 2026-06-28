package com.cyxbs.pages.schedule.data.local

/**
 * todo 分片 Settings 存储的 key 常量。
 *
 * 所有 key 都通过 [AccountSettings] 按账号隔离，不需要在 key 内拼接学号。
 */
object ScheduleSettingsKeys {

  /** meta 元信息，保存 sync_time、chunk count、schema version 等。 */
  const val SP_SCHEDULE_CMP_META = "SP_SCHEDULE_CMP_META"

  /** todoId 索引分片前缀，实际 key 为 `SP_SCHEDULE_CMP_TODO_IDS_CHUNK_0`、`SP_SCHEDULE_CMP_TODO_IDS_CHUNK_1` 等。 */
  const val SP_SCHEDULE_CMP_TODO_IDS_CHUNK_PREFIX = "SP_SCHEDULE_CMP_TODO_IDS_CHUNK_"

  /** 单条 todo 数据前缀，实际 key 为 `SP_SCHEDULE_CMP_TODO_{todoId}`，例如 `SP_SCHEDULE_CMP_TODO_123456`。 */
  const val SP_SCHEDULE_CMP_TODO_PREFIX = "SP_SCHEDULE_CMP_TODO_"

  /** pendingId 索引分片前缀，实际 key 为 `SP_SCHEDULE_CMP_PENDING_IDS_CHUNK_0`、`SP_SCHEDULE_CMP_PENDING_IDS_CHUNK_1` 等。 */
  const val SP_SCHEDULE_CMP_PENDING_IDS_CHUNK_PREFIX = "SP_SCHEDULE_CMP_PENDING_IDS_CHUNK_"

  /** 单条 pending operation 前缀，实际 key 为 `SP_SCHEDULE_CMP_PENDING_{todoId}`。 */
  const val SP_SCHEDULE_CMP_PENDING_PREFIX = "SP_SCHEDULE_CMP_PENDING_"

  /** 首次使用引导 todo 是否已插入的标记。 */
  const val SP_SCHEDULE_CMP_FIRST_USE_DONE = "SP_SCHEDULE_CMP_FIRST_USE_DONE"

  /** 分组(分类)候选池，存为 JSON 字符串数组（type 列表，含默认与自定义）。 */
  const val SP_SCHEDULE_CMP_CATEGORIES = "SP_SCHEDULE_CMP_CATEGORIES"

  /** 每个 todoId 索引 chunk 最多存储的 id 数量；推荐 100，避免单个 value 过大。 */
  const val TODO_IDS_CHUNK_SIZE = 100

  /** 每个 pendingId 索引 chunk 最多存储的 id 数量。 */
  const val PENDING_IDS_CHUNK_SIZE = 100

  /**
   * 拼接 todoId 索引 chunk key。
   *
   * @param chunkIndex 从 0 开始的 chunk 索引。
   */
  fun todoIdsChunkKey(chunkIndex: Int): String {
    return "$SP_SCHEDULE_CMP_TODO_IDS_CHUNK_PREFIX$chunkIndex"
  }

  /**
   * 拼接单条 todo item key。
   */
  fun todoItemKey(todoId: Long): String {
    return "$SP_SCHEDULE_CMP_TODO_PREFIX$todoId"
  }

  /**
   * 拼接 pendingId 索引 chunk key。
   */
  fun pendingIdsChunkKey(chunkIndex: Int): String {
    return "$SP_SCHEDULE_CMP_PENDING_IDS_CHUNK_PREFIX$chunkIndex"
  }

  /**
   * 拼接单条 pending operation key。
   */
  fun pendingOpKey(todoId: Long): String {
    return "$SP_SCHEDULE_CMP_PENDING_PREFIX$todoId"
  }
}
