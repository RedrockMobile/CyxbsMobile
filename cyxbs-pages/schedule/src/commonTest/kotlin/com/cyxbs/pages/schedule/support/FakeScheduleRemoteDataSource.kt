package com.cyxbs.pages.schedule.support

import com.cyxbs.pages.schedule.data.model.ScheduleEntity
import com.cyxbs.pages.schedule.data.remote.IScheduleRemoteDataSource
import com.cyxbs.pages.schedule.data.remote.ScheduleDeltaResponse
import com.cyxbs.pages.schedule.data.remote.ScheduleListResponse
import com.cyxbs.pages.schedule.data.remote.SyncTimeOnlyResponse
import com.cyxbs.pages.schedule.data.remote.SyncTimeResponse

/**
 * 可编排的假远端数据源，用于同步仓库集成测试。
 */
class FakeScheduleRemoteDataSource : IScheduleRemoteDataSource {

  var listResponse = ScheduleListResponse()
  var syncTimeResponse = SyncTimeResponse()
  var deltaResponse = ScheduleDeltaResponse()
  var pushSyncTime = 0L
  var deleteSyncTime = 0L

  /** 设置后，下一次任意远端调用返回失败，并自动清空。 */
  var failNext: Throwable? = null

  val pushed = mutableListOf<List<ScheduleEntity>>()
  val deleted = mutableListOf<List<Long>>()

  override suspend fun getAllSchedules(): Result<ScheduleListResponse> = failOr { listResponse }

  override suspend fun getSyncTime(syncTime: Long): Result<SyncTimeResponse> = failOr { syncTimeResponse }

  override suspend fun pullSchedules(syncTime: Long): Result<ScheduleDeltaResponse> = failOr { deltaResponse }

  override suspend fun pushSchedules(
    todos: List<ScheduleEntity>,
    syncTime: Long,
    force: Int,
    firstPush: Int,
  ): Result<SyncTimeOnlyResponse> {
    pushed.add(todos)
    return failOr { SyncTimeOnlyResponse(pushSyncTime) }
  }

  override suspend fun deleteSchedules(
    todoIds: List<Long>,
    syncTime: Long,
    force: Int,
  ): Result<SyncTimeOnlyResponse> {
    deleted.add(todoIds)
    return failOr { SyncTimeOnlyResponse(deleteSyncTime) }
  }

  private inline fun <T> failOr(block: () -> T): Result<T> {
    val f = failNext
    if (f != null) { failNext = null; return Result.failure(f) }
    return Result.success(block())
  }
}
