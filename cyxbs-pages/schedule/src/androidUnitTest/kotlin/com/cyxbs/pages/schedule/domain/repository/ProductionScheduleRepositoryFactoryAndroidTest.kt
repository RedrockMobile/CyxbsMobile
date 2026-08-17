package com.cyxbs.pages.schedule.domain.repository

import androidx.room3.Room
import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.pages.schedule.data.local.room3.RoomScheduleRepositoryFactory
import com.cyxbs.pages.schedule.data.local.room3.ScheduleRoomDatabase
import com.cyxbs.pages.schedule.data.local.room3.ScheduleV2RepositoryGateway
import com.cyxbs.pages.schedule.data.local.room3.ScheduleV2RoomStateStore
import com.cyxbs.pages.schedule.data.local.room3.bundledScheduleRoomDriver
import com.cyxbs.pages.schedule.data.local.room3.closeScheduleRoomDatabase
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleDelete
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleDeleteResult
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleInput
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleUpsertResult
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleV2CallResult
import com.cyxbs.pages.schedule.data.remote.v3.SyncRequest
import com.cyxbs.pages.schedule.data.remote.v3.SyncResponse
import com.cyxbs.pages.schedule.domain.model.CategoryId
import com.cyxbs.pages.schedule.domain.model.ScheduleCategory
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** Android Schedule v2 production factory 的纯 host 组装测试。 */
class ProductionScheduleRepositoryFactoryAndroidTest {
  /**
   * seam 必须把 exact session、同一数据库与调用方墙钟交给 Room factory。
   *
   * 测试只使用临时 bundled SQLite 和纯内存 gateway：构造阶段零网络，initialize 按合同发起一次完整 Sync，
   * Category 本地提交不调用 daily API。Category pending 的 modifiedAt 同时证明 production seam 没有退回系统墙钟。
   */
  @Test
  fun productionSeamBindsExactSessionDatabaseAndClockWithoutNetwork() = runTest {
    val path = Files.createTempFile("schedule-android-production-seam-", ".db")
    Files.deleteIfExists(path)
    val database = Room.databaseBuilder<ScheduleRoomDatabase>(name = path.toString())
      .setDriver(bundledScheduleRoomDriver())
      .build()
    try {
      val gateway = NoNetworkGateway()
      val clock = RecordingClock(NOW_MILLIS)
      var receivedSession: AccountSession? = null
      val factory = createAndroidRoomScheduleRepositoryFactory(
        database = database,
        clock = clock,
        gatewayFactory = { session ->
          receivedSession = session
          gateway
        },
      )
      assertIs<RoomScheduleRepositoryFactory>(factory)

      val session = AccountSession(7, AccountState.Login(ACCOUNT_ID))
      val repository = factory.create(session)
      assertSame(session, receivedSession)
      assertEquals(0, gateway.syncCalls)
      assertEquals(0, gateway.dailyCalls)
      assertEquals(0, clock.reads)

      repository.initialize()
      assertEquals(1, gateway.syncCalls)
      assertEquals(0, gateway.dailyCalls)
      assertEquals(0, clock.reads)
      assertEquals(
        ScheduleSyncResult.Success(attempted = false),
        repository.execute(
          ScheduleCommand.CreateCategory(
            ScheduleCategory(
              id = CategoryId(CATEGORY_ID),
              revision = 0,
              name = "生产组装测试",
              color = null,
              sortOrder = 0,
            ),
          ),
        ),
      )

      val category = ScheduleV2RoomStateStore(database)
        .readAccountState(ACCOUNT_ID)
        .categories
        .single()
      assertEquals(NOW_MILLIS, category.pendingSnapshot?.name?.modifiedAt)
      assertEquals(1, clock.reads)
      assertEquals(1, gateway.syncCalls)
      assertEquals(0, gateway.dailyCalls)
    } finally {
      database.closeScheduleRoomDatabase()
      Files.deleteIfExists(path)
      Files.deleteIfExists(path.resolveSibling("${path.fileName}-wal"))
      Files.deleteIfExists(path.resolveSibling("${path.fileName}-shm"))
    }
  }

  /** 固定时间并记录读取次数，确保 factory/create/initialize 不提前消费墙钟。 */
  private class RecordingClock(private val epochMillis: Long) : Clock {
    var reads: Int = 0
      private set

    override fun now(): Instant {
      reads += 1
      return Instant.fromEpochMilliseconds(epochMillis)
    }
  }

  /** Sync 返回可恢复的传输失败；任一 daily API 被调用都会使测试失败。 */
  private class NoNetworkGateway : ScheduleV2RepositoryGateway {
    var syncCalls: Int = 0
      private set

    var dailyCalls: Int = 0
      private set

    private fun unexpectedDailyCall(): Nothing {
      dailyCalls += 1
      error("Category local command must not call a Schedule daily endpoint")
    }

    override suspend fun sync(
      accountId: String,
      request: SyncRequest,
    ): ScheduleV2CallResult<SyncResponse> {
      syncCalls += 1
      return ScheduleV2CallResult.TransportFailure(
        status = null,
        cause = IllegalStateException("offline in production factory seam test"),
      )
    }

    override suspend fun createSchedule(
      accountId: String,
      input: ScheduleInput,
    ): ScheduleV2CallResult<ScheduleUpsertResult> = unexpectedDailyCall()

    override suspend fun updateSchedule(
      accountId: String,
      input: ScheduleInput,
    ): ScheduleV2CallResult<ScheduleUpsertResult> = unexpectedDailyCall()

    override suspend fun deleteSchedule(
      accountId: String,
      input: ScheduleDelete,
    ): ScheduleV2CallResult<ScheduleDeleteResult> = unexpectedDailyCall()
  }

  private companion object {
    const val ACCOUNT_ID = "20260001"
    const val CATEGORY_ID = "018f0f7c-6000-7000-8000-000000000011"
    const val NOW_MILLIS = 1_777_777_777_000L
  }
}
