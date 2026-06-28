package com.cyxbs.pages.schedule.data.remote

import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.schedule.data.model.ScheduleEntity
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.HTTP
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Query
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * todo 后端接口的 KMP 版本。
 *
 * 后端实际路由由网关拼上 `magipoke-todo` 前缀；本接口不使用 `/pin` 作为核心路径，
 * 置顶/取消置顶会作为完整 [ScheduleEntity] 通过 [pushSchedules] 上传。
 */
interface ScheduleApiService {

  /**
   * 全量获取当前有效 todo 列表。
   *
   * 首次初始化、本地缓存损坏、`sync_time` 失效时使用该接口重建本地快照。
   */
  @GET("magipoke-todo/list")
  suspend fun getAllSchedules(): ApiWrapper<ScheduleListResponse>

  /**
   * 获取服务端最新 sync_time，并判断客户端持有的 [syncTime] 是否仍存在。
   */
  @GET("magipoke-todo/sync-time")
  suspend fun getSyncTime(
    /** 客户端本地快照对应的后端全局版本。 */
    @Query("sync_time")
    syncTime: Long,
  ): ApiWrapper<SyncTimeResponse>

  /**
   * 按 [syncTime] 增量拉取 todo 变化。
   *
   * 该接口要求 [syncTime] 在服务端历史中仍存在；若不存在，需要回退到 [getAllSchedules] 全量重建。
   */
  @GET("magipoke-todo/todos")
  suspend fun pullSchedules(
    /** 增量拉取的基准后端全局版本。 */
    @Query("sync_time")
    syncTime: Long,
  ): ApiWrapper<ScheduleDeltaResponse>

  /**
   * 上传完整 todo 快照。
   *
   * 后端接口名虽然是 batch-create，但实际可用于新增和更新；编辑、置顶、重复提醒推进都走该接口。
   */
  @POST("magipoke-todo/batch-create")
  @Headers("Content-Type: application/json")
  suspend fun pushSchedules(
    /** 待上传的完整 todo 列表和同步参数。 */
    @Body request: PushSchedulesRequest,
  ): ApiWrapper<SyncTimeOnlyResponse>

  /**
   * 删除 todo。
   *
   * Ktorfit 支持 DELETE 带 body；若后续 iOS 平台发现兼容问题，可在 remote data source 内部回退到后端兼容的 POST `/delete`。
   */
  @HTTP(method = "DELETE", path = "magipoke-todo/todos", hasBody = true)
  @Headers("Content-Type: application/json")
  suspend fun deleteSchedules(
    /** 待删除 id 列表和同步参数。 */
    @Body request: DeleteSchedulesRequest,
  ): ApiWrapper<SyncTimeOnlyResponse>
}

/** `/list` 全量返回结果。 */
@Serializable
data class ScheduleListResponse(
  /** 当前有效 todo 列表，后端 key 沿用 `changed_todo_array`。 */
  @SerialName("changed_todo_array")
  val changedScheduleArray: List<ScheduleEntity> = emptyList(),
  /** 服务端最新全局同步版本。 */
  @SerialName("sync_time")
  val syncTime: Long = 0L,
)

/** `/todos` 增量返回结果。 */
@Serializable
data class ScheduleDeltaResponse(
  /** 自基准 sync_time 后新增或更新的 todo 列表。 */
  @SerialName("changed_todo_array")
  val changedScheduleArray: List<ScheduleEntity> = emptyList(),
  /** 自基准 sync_time 后被删除的 todoId 列表。 */
  @SerialName("del_todo_array")
  val delScheduleArray: List<Long> = emptyList(),
  /** 应用本次增量后的服务端最新全局同步版本。 */
  @SerialName("sync_time")
  val syncTime: Long = 0L,
)

/** `/sync-time` 返回结果。 */
@Serializable
data class SyncTimeResponse(
  /** 服务端当前最新全局同步版本。 */
  @SerialName("sync_time")
  val syncTime: Long = 0L,
  /** 客户端传入的 sync_time 是否仍存在于服务端历史中。 */
  @SerialName("is_sync_time_exist")
  val isSyncTimeExist: Boolean = false,
)

/** 只返回最新 sync_time 的写接口响应。 */
@Serializable
data class SyncTimeOnlyResponse(
  /** 写入成功后服务端生成的新全局同步版本。 */
  @SerialName("sync_time")
  val syncTime: Long = 0L,
)

/** `/batch-create` 请求体。 */
@Serializable
data class PushSchedulesRequest(
  /** 待新增或更新的完整 todo 快照列表。 */
  @SerialName("data")
  val data: List<ScheduleEntity>,
  /** 客户端当前持有的服务端全局同步版本。 */
  @SerialName("sync_time")
  val syncTime: Long,
  /** 是否强制覆盖冲突；首版默认不自动 force。 */
  @SerialName("force")
  val force: Int = NONE_FORCE,
  /** 服务端没有任何历史数据时的首次推送标记。 */
  @SerialName("first_push")
  val firstPush: Int = NONE_FIRST_PUSH,
) {
  companion object {
    /** 不强制覆盖冲突。 */
    const val NONE_FORCE = 0
    /** 强制覆盖冲突。 */
    const val IS_FORCE = 1
    /** 非首次推送。 */
    const val NONE_FIRST_PUSH = 0
    /** 服务端无历史数据时的首次推送。 */
    const val IS_FIRST_PUSH = 1
  }
}

/** DELETE `/todos` 请求体。 */
@Serializable
data class DeleteSchedulesRequest(
  /** 需要删除的 todoId 列表。 */
  @SerialName("del_todo_array")
  val delScheduleArray: List<Long>,
  /** 客户端当前持有的服务端全局同步版本。 */
  @SerialName("sync_time")
  val syncTime: Long,
  /** 是否强制覆盖冲突；首版默认不自动 force。 */
  @SerialName("force")
  val force: Int = NONE_FORCE,
) {
  companion object {
    /** 不强制覆盖冲突。 */
    const val NONE_FORCE = 0
    /** 强制覆盖冲突。 */
    const val IS_FORCE = 1
  }
}
