package com.cyxbs.pages.schedule.data.remote

import com.cyxbs.components.config.service.impl
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.pages.schedule.data.model.ScheduleEntity

/**
 * 远端数据源抽象，便于在单测里用假实现替换（生产实现见 [ScheduleRemoteDataSource]）。
 */
interface IScheduleRemoteDataSource {
  suspend fun getAllSchedules(): Result<ScheduleListResponse>
  suspend fun getSyncTime(syncTime: Long): Result<SyncTimeResponse>
  suspend fun pullSchedules(syncTime: Long): Result<ScheduleDeltaResponse>
  suspend fun pushSchedules(
    todos: List<ScheduleEntity>,
    syncTime: Long,
    force: Int = PushSchedulesRequest.NONE_FORCE,
    firstPush: Int = PushSchedulesRequest.NONE_FIRST_PUSH,
  ): Result<SyncTimeOnlyResponse>
  suspend fun deleteSchedules(
    todoIds: List<Long>,
    syncTime: Long,
    force: Int = DeleteSchedulesRequest.NONE_FORCE,
  ): Result<SyncTimeOnlyResponse>
}

/**
 * 远端数据源生产实现，负责屏蔽 [ScheduleApiService] 与 [com.cyxbs.components.utils.network.ApiWrapper] 的细节。
 *
 * 统一做接口状态码校验，并把异常包装成 [Result]，上层同步仓库只需要处理成功数据或失败原因。
 */
object ScheduleRemoteDataSource : IScheduleRemoteDataSource {

  private val api: ScheduleApiService
    get() = ScheduleApiService::class.impl()

  override suspend fun getAllSchedules(): Result<ScheduleListResponse> {
    return runCatchingCoroutine {
      api.getAllSchedules().also { it.throwApiExceptionIfFail() }.data
    }
  }

  override suspend fun getSyncTime(syncTime: Long): Result<SyncTimeResponse> {
    return runCatchingCoroutine {
      api.getSyncTime(syncTime).also { it.throwApiExceptionIfFail() }.data
    }
  }

  override suspend fun pullSchedules(syncTime: Long): Result<ScheduleDeltaResponse> {
    return runCatchingCoroutine {
      api.pullSchedules(syncTime).also { it.throwApiExceptionIfFail() }.data
    }
  }

  override suspend fun pushSchedules(
    todos: List<ScheduleEntity>,
    syncTime: Long,
    force: Int,
    firstPush: Int,
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

  override suspend fun deleteSchedules(
    todoIds: List<Long>,
    syncTime: Long,
    force: Int,
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
