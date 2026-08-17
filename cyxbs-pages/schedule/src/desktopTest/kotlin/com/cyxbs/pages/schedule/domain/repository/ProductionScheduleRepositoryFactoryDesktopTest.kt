package com.cyxbs.pages.schedule.domain.repository

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.pages.schedule.data.local.room3.DesktopScheduleRoomDatabaseResources
import com.cyxbs.pages.schedule.data.local.room3.RoomScheduleRepositoryFactory
import com.cyxbs.pages.schedule.data.local.room3.ScheduleV2RepositoryGateway
import com.cyxbs.pages.schedule.data.local.room3.closeScheduleRoomDatabase
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.schedule.data.remote.v3.CategorySyncResponse
import com.cyxbs.pages.schedule.data.remote.v3.OccurrenceOverrideSyncResponse
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleDelete
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleDeleteResult
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleInput
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleSyncResponse
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleUpsertResult
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleV2CallResult
import com.cyxbs.pages.schedule.data.remote.v3.SyncRequest
import com.cyxbs.pages.schedule.data.remote.v3.SyncResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Clock

/** Desktop 生产组装只验证最终 Room v2、exact session 与初始化一次 Sync。 */
class ProductionScheduleRepositoryFactoryDesktopTest {
  @Test
  fun productionSeamBindsExactSessionAndInitializeSyncsOnce() = runTest {
    withTemporaryProductionResources { resources ->
      val gatewayFactory = RecordingGatewayFactory()
      val factory = createDesktopRoomScheduleRepositoryFactory(
        resources = resources,
        clock = Clock.System,
        gatewayFactory = gatewayFactory::create,
      )
      assertIs<RoomScheduleRepositoryFactory>(factory)

      val session = AccountSession(1, AccountState.Login(ACCOUNT_ID))
      val repository = factory.create(session)
      assertSame(session, gatewayFactory.receivedSession)
      assertEquals(0, gatewayFactory.gateway.syncCalls)

      repository.initialize()

      assertEquals(1, gatewayFactory.gateway.syncCalls)
      assertEquals(ACCOUNT_ID, gatewayFactory.gateway.syncedAccountId)
      assertEquals(ACCOUNT_ID, repository.snapshot.value.accountId)
    }
  }

  /** 创建隔离数据库；测试结束后关闭连接并清理 SQLite 伴生文件。 */
  private suspend fun withTemporaryProductionResources(
    block: suspend (DesktopScheduleRoomDatabaseResources) -> Unit,
  ) {
    val root = Files.createTempDirectory("schedule-desktop-v2-factory-")
    val resources = DesktopScheduleRoomDatabaseResources(
      databasePath = root.resolve(DATABASE_FILE_NAME).toString(),
    )
    try {
      block(resources)
    } finally {
      resources.database.closeScheduleRoomDatabase()
      deleteRecursively(root)
    }
  }

  /** 记录 factory 收到的 exact session，并复用单个无网络 typed gateway。 */
  private class RecordingGatewayFactory {
    var receivedSession: AccountSession? = null
    val gateway = RecordingGateway()

    fun create(session: AccountSession): ScheduleV2RepositoryGateway {
      receivedSession = session
      return gateway
    }
  }

  /** 初始化只允许一次 Sync；日常接口若被误触发会立即让测试失败。 */
  private class RecordingGateway : ScheduleV2RepositoryGateway {
    var syncCalls = 0
    var syncedAccountId: String? = null

    override suspend fun sync(
      accountId: String,
      request: SyncRequest,
    ): ScheduleV2CallResult<SyncResponse> {
      syncCalls += 1
      syncedAccountId = accountId
      return ScheduleV2CallResult.Completed(
        ApiWrapper(emptySyncResponse(request.syncRequestId), 10000, "ok"),
      )
    }

    override suspend fun createSchedule(
      accountId: String,
      input: ScheduleInput,
    ): ScheduleV2CallResult<ScheduleUpsertResult> = error("initialize must not call daily create")

    override suspend fun updateSchedule(
      accountId: String,
      input: ScheduleInput,
    ): ScheduleV2CallResult<ScheduleUpsertResult> = error("initialize must not call daily update")

    override suspend fun deleteSchedule(
      accountId: String,
      input: ScheduleDelete,
    ): ScheduleV2CallResult<ScheduleDeleteResult> = error("initialize must not call daily delete")
  }

  /** 删除临时目录、数据库主文件以及 Room 可能生成的 WAL/SHM。 */
  private fun deleteRecursively(root: Path) {
    if (Files.exists(root)) {
      Files.walk(root).use { paths ->
        paths.sorted(Comparator.reverseOrder()).forEach { path -> Files.deleteIfExists(path) }
      }
    }
  }

  private companion object {
    private const val ACCOUNT_ID = "20260001"
    private const val DATABASE_FILE_NAME = "schedule-room3-production.db"
  }
}

/** 为嵌套 gateway fake 提供不携带任何资源的合法同步响应。 */
private fun emptySyncResponse(requestId: String) = SyncResponse(
  syncRequestId = requestId,
  categories = CategorySyncResponse(emptyList(), emptyList(), emptyList(), emptyList()),
  schedules = ScheduleSyncResponse(emptyList(), emptyList(), emptyList(), emptyList()),
  occurrenceOverrides = OccurrenceOverrideSyncResponse(emptyList(), emptyList(), emptyList(), emptyList()),
  atomicBatchResults = emptyList(),
)
