package com.cyxbs.pages.schedule.data.remote

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.utils.network.ApiWrapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest

/** Ktorfit 适配层只验证统一外壳和 exact-session 传递，不复制 HTTP 客户端实现测试。 */
class KtorScheduleGatewayTest {

  @Test
  fun httpFailureLogDoesNotContainResponseBody() {
    val untrustedBody = "<html><body>SafeLine blocked credential=secret</body></html>"

    val logText = httpFailureLogText(
      operation = "CREATE",
      status = 468,
      contentType = "text/html; charset=utf-8",
    )

    assertEquals("CREATE HTTP 468 contentType=text/html", logText)
    assertFalse(logText.contains(untrustedBody))
    assertFalse(logText.contains("credential"))
  }

  @Test
  fun handledResponseKeepsTypedRawDataAndExactSession() = runTest {
    val session = AccountSession(
      7,
      AccountState.Login(ACCOUNT_ID),
      CoroutineScope(SupervisorJob()),
      tokenState = null,
    )
    val response = emptyResponse()
    val api = FakeApi(ApiWrapper(response, 10000, "success"))
    val gateway = KtorScheduleGateway(api, session)

    val result = assertIs<ScheduleCallResult.Completed<SyncResponse>>(
      gateway.sync(ACCOUNT_ID, emptyRequest()),
    )

    assertSame(session, api.receivedSession)
    assertSame(response, result.wrapper.rawData)
    assertEquals(10000, result.wrapper.status)
  }

  @Test
  fun unsupportedBusinessStatusDoesNotExposeCompletedData() = runTest {
    val session = AccountSession(
      8,
      AccountState.Login(ACCOUNT_ID),
      CoroutineScope(SupervisorJob()),
      tokenState = null,
    )
    val api = FakeApi(ApiWrapper(emptyResponse(), 20001, "internal"))
    val gateway = KtorScheduleGateway(api, session)

    val result = assertIs<ScheduleCallResult.ApiFailure>(
      gateway.sync(ACCOUNT_ID, emptyRequest()),
    )

    assertEquals(20001, result.status)
    assertEquals("internal", result.info)
  }

  /** 只实现本测试使用的 Sync；其余 mutation 与账号清空方法若误调用立即失败。 */
  private class FakeApi(
    private val syncResponse: ApiWrapper<SyncResponse>,
  ) : ScheduleApiService {
    var receivedSession: AccountSession? = null

    override suspend fun sync(
      request: SyncRequest,
      session: AccountSession,
    ): ApiWrapper<SyncResponse> {
      receivedSession = session
      return syncResponse
    }

    override suspend fun createSchedule(
      input: MutationRequest,
      session: AccountSession,
    ): ApiWrapper<MutationResponse> = error("unexpected create")

    override suspend fun updateSchedule(
      input: MutationRequest,
      session: AccountSession,
    ): ApiWrapper<MutationResponse> = error("unexpected update")

    override suspend fun deleteSchedule(
      input: MutationRequest,
      session: AccountSession,
    ): ApiWrapper<MutationResponse> = error("unexpected delete")

    override suspend fun clearAllSchedules(
      session: AccountSession,
    ): ApiWrapper<Boolean> = error("unexpected account clear")
  }

  private fun emptyRequest() = SyncRequest(
    categories = CategorySyncRequest(emptyList(), emptyList(), emptyList()),
    schedules = ScheduleSyncRequest(emptyList(), emptyList(), emptyList()),
    occurrenceAdjustments = OccurrenceAdjustmentSyncRequest(emptyList(), emptyList(), emptyList()),
  )

  private fun emptyResponse() = SyncResponse(
    categories = CategorySyncResponse(emptyList(), emptyList(), emptyList(), emptyList()),
    schedules = ScheduleSyncResponse(emptyList(), emptyList(), emptyList(), emptyList()),
    occurrenceAdjustments = OccurrenceAdjustmentSyncResponse(emptyList(), emptyList(), emptyList(), emptyList()),
  )

  private companion object {
    const val ACCOUNT_ID = "20260001"
  }
}
