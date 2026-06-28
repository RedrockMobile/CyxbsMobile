package com.cyxbs.pages.schedule.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 离线待同步操作。
 *
 * 首版只保留 upsert/delete 两类操作：新增、编辑、置顶、重复提醒推进都收敛为 upsert，
 * 删除和无重复完成收敛为 delete。同一个 todoId 的 pending 操作会在本地数据源中压缩，通常只保留最后状态。
 */
@Serializable
data class SchedulePendingOperation(
  /** 待同步操作对应的 todo id，同时也作为 pending 分片存储中的定位 key。 */
  @SerialName("todo_id")
  val todoId: Long,
  /** 操作类型：完整 upsert 或 delete。 */
  @SerialName("kind")
  val kind: Kind,
  /** upsert 时保存完整 todo 快照；delete 时为 null。 */
  @SerialName("todo")
  val todo: ScheduleEntity? = null,
  /** 首次入队时间，用于调试和后续可能的冲突策略。 */
  @SerialName("created_at")
  val createdAt: Long,
  /** 最近一次合并/更新该 pending 操作的时间。 */
  @SerialName("updated_at")
  val updatedAt: Long = createdAt,
) {
  @Serializable
  enum class Kind {
    /** 新增或更新完整 todo；置顶也作为 upsert 上传，不走后端 `/pin`。 */
    @SerialName("upsert")
    UPSERT,

    /** 删除 todo，对应后端 DELETE `/magipoke-todo/todos`。 */
    @SerialName("delete")
    DELETE,
  }
}
