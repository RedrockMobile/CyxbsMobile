package com.cyxbs.pages.schedule.data.local.room3

import com.cyxbs.pages.schedule.data.remote.AtomicField
import com.cyxbs.pages.schedule.data.remote.CategoryInput
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ScheduleRoomStateStore 的 Desktop SQLite 合同测试。
 *
 * 验证完整响应集合会原子替换账号状态，且纯本地 revision 的独立分配不影响状态落库。
 */
class ScheduleRoomStateStoreTest {
  /** 每次短事务只推进账号 metadata counter，分配的 revision 单调递增。 */
  @Test
  fun allocateLocalRevisionIsMonotonic() = runTest {
    withDatabase { path ->
      val database = buildScheduleRoomDatabase(path.toString())
      try {
        val store = ScheduleRoomStateStore(database)
        assertEquals(1L, store.allocateLocalRevision(ACCOUNT))
        assertEquals(2L, store.allocateLocalRevision(ACCOUNT))
      } finally {
        database.closeScheduleRoomDatabase()
      }
    }
  }

  /** 完整 state 带入已分配 revision 后可往返，替换不会清除账号的 revision metadata。 */
  @Test
  fun replaceAccountStateRoundTripsAllocatedRevisionAndKeepsMetadata() = runTest {
    withDatabase { path ->
      val database = buildScheduleRoomDatabase(path.toString())
      try {
        val store = ScheduleRoomStateStore(database)
        val revision = store.allocateLocalRevision(ACCOUNT)
        val state = categoryDeleteState(
          localModifiedAt = 1,
          localRevision = revision,
        )

        store.replaceAccountState(
          accountId = ACCOUNT,
          categories = listOf(state),
          schedules = emptyList(),
          occurrenceAdjustments = emptyList(),
        )

        assertEquals(listOf(state), store.readAccountState(ACCOUNT).categories)

        store.replaceAccountState(
          accountId = ACCOUNT,
          categories = emptyList(),
          schedules = emptyList(),
          occurrenceAdjustments = emptyList(),
        )
        assertTrue(store.readAccountState(ACCOUNT).categories.isEmpty())
        assertEquals(2L, store.allocateLocalRevision(ACCOUNT))
      } finally {
        database.closeScheduleRoomDatabase()
      }
    }
  }

  /** 构造包含 remote 与本地 DELETE pending 的完整 Category state 行。 */
  private fun categoryDeleteState(localModifiedAt: Long, localRevision: Long) =
    ScheduleCategoryStateEntity(
      accountId = ACCOUNT,
      categoryId = CATEGORY_ID,
      remoteSnapshot = CategoryInput(
        id = 41L,
        version = 1u,
        name = AtomicField("分类", 1),
        color = AtomicField(null, 2),
        sortOrder = AtomicField(0, 3),
      ),
      pendingOperation = SchedulePendingOperation.DELETE,
      pendingSnapshot = null,
      pendingLocalModifiedAt = localModifiedAt,
      localRevision = localRevision,
    )

  /** 在测试结束后删除 SQLite 主文件及 WAL/SHM 辅助文件。 */
  private suspend fun withDatabase(block: suspend (java.nio.file.Path) -> Unit) {
    val path = Files.createTempFile("schedule-room-state-", ".db")
    Files.deleteIfExists(path)
    try {
      block(path)
    } finally {
      Files.deleteIfExists(path)
      Files.deleteIfExists(path.resolveSibling("${path.fileName}-wal"))
      Files.deleteIfExists(path.resolveSibling("${path.fileName}-shm"))
    }
  }

  private companion object {
    const val ACCOUNT = "room-state-account"
    const val CATEGORY_ID = "category-1"
  }
}
