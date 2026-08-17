package com.cyxbs.pages.schedule.data.remote.v3

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.utils.network.ApiWrapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest

/** Ktorfit 适配层只验证统一外壳和 exact-session 传递，不复制 HTTP 客户端实现测试。 */
class KtorScheduleV2GatewayTest {

  @Test
  fun businessRejectedKeepsTypedRawDataAndExactSession() = runTest {
    val session = AccountSession(7, AccountState.Login(ACCOUNT_ID))
    val response = emptyResponse("sync-1")
    val api = FakeApi(ApiWrapper(response, 20101, "rejected"))
    val gateway = KtorScheduleV2Gateway(api, session)

    val result = assertIs<ScheduleV2CallResult.Completed<SyncResponse>>(
      gateway.sync(ACCOUNT_ID, emptyRequest("sync-1")),
    )

    assertSame(session, api.receivedSession)
    assertSame(response, result.wrapper.rawData)
    assertEquals(20101, result.wrapper.status)
  }

  @Test
  fun unsupportedBusinessStatusDoesNotExposeCompletedData() = runTest {
    val session = AccountSession(8, AccountState.Login(ACCOUNT_ID))
    val api = FakeApi(ApiWrapper(emptyResponse("sync-2"), 20001, "internal"))
    val gateway = KtorScheduleV2Gateway(api, session)

    val result = assertIs<ScheduleV2CallResult.ApiFailure>(
      gateway.sync(ACCOUNT_ID, emptyRequest("sync-2")),
    )

    assertEquals(20001, result.status)
    assertEquals("internal", result.info)
  }

  /** 只实现本测试使用的 Sync；其余 daily 方法若误调用立即失败。 */
  private class FakeApi(
    private val syncResponse: ApiWrapper<SyncResponse>,
  ) : ScheduleV2ApiService {
    var receivedSession: AccountSession? = null

    override suspend fun sync(
      request: SyncRequest,
      session: AccountSession,
    ): ApiWrapper<SyncResponse> {
      receivedSession = session
      return syncResponse
    }

    override suspend fun createSchedule(
      input: ScheduleInput,
      session: AccountSession,
    ): ApiWrapper<ScheduleUpsertResult> = error("unexpected create")

    override suspend fun updateSchedule(
      input: ScheduleInput,
      session: AccountSession,
    ): ApiWrapper<ScheduleUpsertResult> = error("unexpected update")

    override suspend fun deleteSchedule(
      input: ScheduleDelete,
      session: AccountSession,
    ): ApiWrapper<ScheduleDeleteResult> = error("unexpected delete")
  }

  private fun emptyRequest(requestId: String) = SyncRequest(
    syncRequestId = requestId,
    categories = CategorySyncRequest(emptyList(), emptyList(), emptyList()),
    schedules = ScheduleSyncRequest(emptyList(), emptyList(), emptyList()),
    occurrenceOverrides = OccurrenceOverrideSyncRequest(emptyList(), emptyList(), emptyList()),
    atomicBatches = emptyList(),
  )

  private fun emptyResponse(requestId: String) = SyncResponse(
    syncRequestId = requestId,
    categories = CategorySyncResponse(emptyList(), emptyList(), emptyList(), emptyList()),
    schedules = ScheduleSyncResponse(emptyList(), emptyList(), emptyList(), emptyList()),
    occurrenceOverrides = OccurrenceOverrideSyncResponse(emptyList(), emptyList(), emptyList(), emptyList()),
    atomicBatchResults = emptyList(),
  )

  private companion object {
    const val ACCOUNT_ID = "20260001"
  }
}
