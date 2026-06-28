package com.cyxbs.pages.schedule.data.remote

import com.cyxbs.components.config.service.impl
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.pages.schedule.data.model.ScheduleEntity

/**
 * todo 远端数据源，负责屏蔽 [ScheduleApiService] 与 [com.cyxbs.components.utils.network.ApiWrapper] 的细节。
 *
 * 这里统一做接口状态码校验，并把异常包装成 [Result]，上层同步仓库只需要处理成功数据或失败原因。
 */
object ScheduleRemoteDataSource {

  private val api: ScheduleApiService
    get() = ScheduleApiService::class.impl()

  /**
   * 全量拉取服务端当前有效 todo 列表。
   *
   * 用于首次初始化、本地分片 Settings 损坏、或者本地 [SyncTimeResponse.isSyncTimeExist] 为 false 后的重建。
   */
  suspend fun getAllSchedules(): Result<ScheduleListResponse> {
    return runCatchingCoroutine {
      api.getAllSchedules().also { it.throwApiExceptionIfFail() }.data
    }
  }

  /**
   * 查询服务端最新 sync_time，并判断本地 [syncTime] 是否还能作为增量同步基线。
   */
  suspend fun getSyncTime(syncTime: Long): Result<SyncTimeResponse> {
    return runCatchingCoroutine {
      api.getSyncTime(syncTime).also { it.throwApiExceptionIfFail() }.data
    }
  }

  /**
   * 基于本地 [syncTime] 拉取增量变化。
   *
   * 如果后端返回 sync_time 不存在，上层应回退到 [getAllSchedules] 全量重建。
   */
  suspend fun pullSchedules(syncTime: Long): Result<ScheduleDeltaResponse> {
    return runCatchingCoroutine {
      api.pullSchedules(syncTime).also { it.throwApiExceptionIfFail() }.data
    }
  }

  /**
   * 上传完整 todo 快照列表。
   *
   * 新增、编辑、置顶/取消置顶、重复提醒推进都通过该方法同步；默认不强制覆盖冲突。
   */
  suspend fun pushSchedules(
    /** 待上传的完整 todo 快照列表。 */
    todos: List<ScheduleEntity>,
    /** 客户端当前持有的服务端全局同步版本。 */
    syncTime: Long,
    /** 是否强制覆盖冲突，默认 [PushSchedulesRequest.NONE_FORCE]。 */
    force: Int = PushSchedulesRequest.NONE_FORCE,
    /** 服务端无历史数据时的首次推送标记，默认 [PushSchedulesRequest.NONE_FIRST_PUSH]。 */
    firstPush: Int = PushSchedulesRequest.NONE_FIRST_PUSH,
  ): Result<SyncTimeOnlyResponse> {
    return runCatchingCoroutine {
      api.pushSchedules(
        PushSchedulesRequest(
          data = todos,
          syncTime = syncTime,
          force = force,
          firstPush = firstPush,
        )
      ).also { it.throwApiExceptionIfFail() }.data
    }
  }

  /**
   * 删除 todo。
   *
   * 删除成功后后端会返回新的 sync_time；默认不强制覆盖冲突。
   */
  suspend fun deleteSchedules(
    /** 待删除的 todoId 列表。 */
    todoIds: List<Long>,
    /** 客户端当前持有的服务端全局同步版本。 */
    syncTime: Long,
    /** 是否强制覆盖冲突，默认 [DeleteSchedulesRequest.NONE_FORCE]。 */
    force: Int = DeleteSchedulesRequest.NONE_FORCE,
  ): Result<SyncTimeOnlyResponse> {
    return runCatchingCoroutine {
      api.deleteSchedules(
        DeleteSchedulesRequest(
          delScheduleArray = todoIds,
          syncTime = syncTime,
          force = force,
        )
      ).also { it.throwApiExceptionIfFail() }.data
    }
  }
}
