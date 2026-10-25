@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.cyxbs.pages.schedule.domain.repository

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.pages.schedule.data.local.room3.IosScheduleRoomDatabaseResources
import com.cyxbs.pages.schedule.data.local.room3.RoomScheduleRepositoryFactory
import com.cyxbs.pages.schedule.data.local.room3.ScheduleRepositoryGateway
import com.cyxbs.pages.schedule.data.local.room3.closeScheduleRoomDatabase
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.schedule.data.remote.CategorySyncResponse
import com.cyxbs.pages.schedule.data.remote.OccurrenceAdjustmentSyncResponse
import com.cyxbs.pages.schedule.data.remote.ScheduleSyncResponse
import com.cyxbs.pages.schedule.data.remote.ScheduleCallResult
import com.cyxbs.pages.schedule.data.remote.SyncRequest
import com.cyxbs.pages.schedule.data.remote.SyncResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSTemporaryDirectory
import platform.posix.remove

/** iOS Room factory 的隔离 seam 测试；只验证 exact session、typed sync 与四表存储边界。 */
class ProductionScheduleRepositoryFactoryIosTest {
  /** initialize 必须把同一 session 原引用交给 gateway，并且只进行一次合法的 typed Sync。 */
  @Test
  fun productionSeamBindsExactSessionAndPerformsOneTypedSync() = runTest {
    val databasePath = temporaryDatabasePath()
    val resources = IosScheduleRoomDatabaseResources(databasePath)
    val gateway = RecordingGateway()
    val session = AccountSession(
      1,
      AccountState.Login("factory-seam-student"),
      CoroutineScope(SupervisorJob()),
      tokenState = null,
    )
    try {
      val factory = createIosRoomScheduleRepositoryFactory(
        resources = resources,
        clock = Clock.System,
        gatewayFactory = gateway::create,
      )
      assertIs<RoomScheduleRepositoryFactory>(factory)

      val repository = factory.create(session)
      repository.initialize()

      assertSame(session, gateway.receivedSession)
      assertEquals(1, gateway.syncCalls)
      assertEquals("factory-seam-student", repository.snapshot.value.accountId)
    } finally {
      resources.database.closeScheduleRoomDatabase()
      remove(databasePath)
      remove("$databasePath-wal")
      remove("$databasePath-shm")
    }
  }

  /** 生成隔离 SQLite 路径，不会命中生产 Home 数据库。 */
  private fun temporaryDatabasePath(): String =
    "${NSTemporaryDirectory()}schedule-room3-production-seam-${Uuid.random()}.db"

  /** 记录 exact session，并在返回空图前严格编码 Sync 请求。 */
  private class RecordingGateway : ScheduleRepositoryGateway {
    var receivedSession: AccountSession? = null
    var syncCalls = 0

    /** factory 创建 facade 时冻结 exact session 原引用。 */
    fun create(session: AccountSession): ScheduleRepositoryGateway = also { receivedSession = session }

    override suspend fun sync(accountId: String, request: SyncRequest): ScheduleCallResult<SyncResponse> {
      syncCalls += 1
      return ScheduleCallResult.Completed(
        ApiWrapper(
          SyncResponse(
            CategorySyncResponse(emptyList(), emptyList(), emptyList(), emptyList()),
            ScheduleSyncResponse(emptyList(), emptyList(), emptyList(), emptyList()),
            OccurrenceAdjustmentSyncResponse(emptyList(), emptyList(), emptyList(), emptyList()),
          ),
          10000,
          "ok",
        ),
      )
    }

    override suspend fun createSchedule(
      accountId: String,
      input: com.cyxbs.pages.schedule.data.remote.MutationRequest,
    ) = error("factory initialize must not call daily create")

    override suspend fun updateSchedule(
      accountId: String,
      input: com.cyxbs.pages.schedule.data.remote.MutationRequest,
    ) = error("factory initialize must not call daily update")

    override suspend fun deleteSchedule(
      accountId: String,
      input: com.cyxbs.pages.schedule.data.remote.MutationRequest,
    ) = error("factory initialize must not call daily delete")
  }
}
