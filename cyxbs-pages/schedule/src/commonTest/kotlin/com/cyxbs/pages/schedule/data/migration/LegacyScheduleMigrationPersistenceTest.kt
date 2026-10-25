package com.cyxbs.pages.schedule.data.migration

import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.domain.model.CategoryId
import com.cyxbs.pages.schedule.domain.model.ScheduleCategory
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.repository.ScheduleCommand
import com.cyxbs.pages.schedule.domain.repository.ScheduleRemoteError
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepository
import com.cyxbs.pages.schedule.domain.repository.ScheduleSnapshot
import com.cyxbs.pages.schedule.domain.repository.ScheduleSyncResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 旧数据迁移的本地提交测试，覆盖重试、分类解析、账号隔离与 local-first 失败边界。 */
class LegacyScheduleMigrationPersistenceTest {

  /** 重试遇到已经落库的确定性日程 ID 时必须跳过，不能产生重复命令或重复记录。 */
  @Test
  fun existingScheduleId_isSkippedIdempotently() = runTest {
    val item = migrationItem(todoId = 1, categoryName = null)
    val repository = FakeScheduleRepository(
      ScheduleSnapshot(accountId = ACCOUNT_ID, schedules = listOf(item.schedule))
    )

    LegacyScheduleMigrationCoordinator.persistItems(repository, ACCOUNT_ID, listOf(item))

    assertTrue(repository.commands.isEmpty())
    assertEquals(listOf(item.schedule), repository.snapshot.value.schedules)
  }

  /** 同一批源数据意外包含重复旧 ID 时也只提交一次，不能依赖下一轮迁移才去重。 */
  @Test
  fun duplicateScheduleIdWithinBatch_isCommittedOnce() = runTest {
    val item = migrationItem(todoId = 9, categoryName = null)
    val repository = FakeScheduleRepository(ScheduleSnapshot(accountId = ACCOUNT_ID))

    LegacyScheduleMigrationCoordinator.persistItems(repository, ACCOUNT_ID, listOf(item, item))

    assertEquals(1, repository.commands.size)
    assertEquals(1, repository.snapshot.value.schedules.size)
  }

  /** 已同步分类按去除首尾空白后的名称复用，即使 identity 不同也不能重复创建默认分类。 */
  @Test
  fun existingCategoryWithSameName_isReused() = runTest {
    val item = migrationItem(todoId = 2, categoryName = "学习")
    val existingCategory = ScheduleCategory(
      id = CategoryId(LegacyScheduleMapper.deterministicScheduleUuid("existing-category")),
      revision = 7,
      name = " 学习 ",
      color = """{"background":"custom"}""",
      sortOrder = 9,
    )
    val repository = FakeScheduleRepository(
      ScheduleSnapshot(accountId = ACCOUNT_ID, categories = listOf(existingCategory))
    )

    LegacyScheduleMigrationCoordinator.persistItems(repository, ACCOUNT_ID, listOf(item))

    val command = assertIs<ScheduleCommand.Create>(repository.commands.single())
    assertEquals(existingCategory.id, command.schedule.categoryId)
    assertEquals(listOf(existingCategory), repository.snapshot.value.categories)
  }

  /** 固定默认分类即使被用户改名，仍优先按 identity 复用，避免重建同 ID 分类。 */
  @Test
  fun existingCategoryWithDefaultIdentity_isReusedAfterRename() = runTest {
    val item = migrationItem(todoId = 10, categoryName = "学习")
    val existingCategory = ScheduleCategory(
      id = LegacyDefaultCategoryIds.getValue("学习"),
      revision = 3,
      name = "课程",
      color = null,
      sortOrder = 1,
    )
    val repository = FakeScheduleRepository(
      ScheduleSnapshot(accountId = ACCOUNT_ID, categories = listOf(existingCategory))
    )

    LegacyScheduleMigrationCoordinator.persistItems(repository, ACCOUNT_ID, listOf(item))

    val command = assertIs<ScheduleCommand.Create>(repository.commands.single())
    assertEquals(existingCategory.id, command.schedule.categoryId)
    assertEquals(listOf(existingCategory), repository.snapshot.value.categories)
  }

  /** 同批多条清单引用缺失默认分类时只随第一条原子创建，后续日程直接复用同一 identity。 */
  @Test
  fun missingCategory_isCreatedOnceAndReusedWithinBatch() = runTest {
    val first = migrationItem(todoId = 3, categoryName = "生活")
    val second = migrationItem(todoId = 4, categoryName = "生活")
    val repository = FakeScheduleRepository(ScheduleSnapshot(accountId = ACCOUNT_ID))

    LegacyScheduleMigrationCoordinator.persistItems(repository, ACCOUNT_ID, listOf(first, second))

    val createCategory = assertIs<ScheduleCommand.SaveScheduleWithNewCategory>(
      repository.commands.first()
    )
    val createSchedule = assertIs<ScheduleCommand.Create>(repository.commands.last())
    assertEquals(LegacyDefaultCategoryIds.getValue("生活"), createCategory.category.id)
    assertEquals(createCategory.category.id, createCategory.schedule.categoryId)
    assertEquals(createCategory.category.id, createSchedule.schedule.categoryId)
    assertEquals(1, repository.snapshot.value.categories.size)
    assertEquals(2, repository.snapshot.value.schedules.size)
  }

  /** 远端投递失败但本地快照已发布时迁移仍可继续，等待既有 pending 机制后续同步。 */
  @Test
  fun remoteFailureAfterLocalCommit_isAccepted() = runTest {
    val item = migrationItem(todoId = 5, categoryName = null)
    val repository = FakeScheduleRepository(
      initial = ScheduleSnapshot(accountId = ACCOUNT_ID),
      result = ScheduleSyncResult.Failure(ScheduleRemoteError.Timeout, attempted = true),
    )

    LegacyScheduleMigrationCoordinator.persistItems(repository, ACCOUNT_ID, listOf(item))

    assertEquals(item.schedule.id, repository.snapshot.value.schedules.single().id)
  }

  /** execute 未发布本地日程时必须立即失败，防止调用方把未落库数据误标成迁移完成。 */
  @Test
  fun missingLocalScheduleCommit_failsClosed() = runTest {
    val item = migrationItem(todoId = 6, categoryName = null)
    val repository = FakeScheduleRepository(
      initial = ScheduleSnapshot(accountId = ACCOUNT_ID),
      publishLocal = false,
    )

    assertFailsWith<IllegalStateException> {
      LegacyScheduleMigrationCoordinator.persistItems(repository, ACCOUNT_ID, listOf(item))
    }
  }

  /** 原子命令只发布日程却漏掉新分类时同样失败，避免产生悬空 categoryId。 */
  @Test
  fun missingLocalCategoryCommit_failsClosed() = runTest {
    val item = migrationItem(todoId = 7, categoryName = "其他")
    val repository = FakeScheduleRepository(
      initial = ScheduleSnapshot(accountId = ACCOUNT_ID),
      publishCategory = false,
    )

    assertFailsWith<IllegalStateException> {
      LegacyScheduleMigrationCoordinator.persistItems(repository, ACCOUNT_ID, listOf(item))
    }
  }

  /** 开始提交前账号已变化时不得向新账号仓库写入旧账号数据。 */
  @Test
  fun accountMismatchBeforePersistence_writesNothing() = runTest {
    val repository = FakeScheduleRepository(ScheduleSnapshot(accountId = "another-account"))

    LegacyScheduleMigrationCoordinator.persistItems(
      repository,
      ACCOUNT_ID,
      listOf(migrationItem(todoId = 8, categoryName = null)),
    )

    assertTrue(repository.commands.isEmpty())
    assertTrue(repository.snapshot.value.schedules.isEmpty())
  }

  /** 空旧数据集合是合法成功输入，不应制造分类或仓库命令。 */
  @Test
  fun emptyMigrationBatch_writesNothing() = runTest {
    val repository = FakeScheduleRepository(ScheduleSnapshot(accountId = ACCOUNT_ID))

    LegacyScheduleMigrationCoordinator.persistItems(repository, ACCOUNT_ID, emptyList())

    assertTrue(repository.commands.isEmpty())
    assertTrue(repository.snapshot.value.schedules.isEmpty())
    assertTrue(repository.snapshot.value.categories.isEmpty())
  }

  /** 已有置顶顺序优先，迁移项按旧接口顺序追加，跨两侧的重复 ID 只保留第一次。 */
  @Test
  fun migratedPinsAppendWithoutOverwritingExistingOrder() {
    val first = ScheduleId(LegacyScheduleMapper.deterministicScheduleUuid("pin-1"))
    val second = ScheduleId(LegacyScheduleMapper.deterministicScheduleUuid("pin-2"))
    val third = ScheduleId(LegacyScheduleMapper.deterministicScheduleUuid("pin-3"))

    assertEquals(
      listOf(first, second, third),
      LegacyScheduleMigrationCoordinator.mergeMigratedPinnedIds(
        existing = listOf(first, second),
        migrated = listOf(second, third, first),
      ),
    )
  }

  /** 构造一条无需时间解析的旧清单映射结果，让测试只关注持久化职责。 */
  private fun migrationItem(todoId: Long, categoryName: String?): LegacyScheduleMigrationItem =
    LegacyScheduleMapper.mapTodos(
      accountId = ACCOUNT_ID,
      todos = listOf(
        LegacyTodoDto(
          todoId = todoId,
          title = "迁移清单-$todoId",
          lastModifyTime = 1_700_000_000_000L + todoId,
        )
      ),
      now = MinuteTimeDate(2026, 3, 1, 12, 0),
      nowEpochMillis = 1_700_000_100_000L,
    ).single().copy(categoryName = categoryName)

  /**
   * 仅模拟仓库命令提交后的公开快照。
   *
   * [publishLocal] 与 [publishCategory] 用于制造违反仓库提交承诺的故障；[result] 则验证远端结果不会覆盖
   * “本地是否已经提交”这一迁移判据。
   */
  private class FakeScheduleRepository(
    initial: ScheduleSnapshot,
    private val publishLocal: Boolean = true,
    private val publishCategory: Boolean = true,
    private val result: ScheduleSyncResult? = ScheduleSyncResult.Success(),
  ) : ScheduleRepository {
    private val mutableSnapshot = MutableStateFlow(initial)
    override val snapshot: StateFlow<ScheduleSnapshot> = mutableSnapshot
    val commands = mutableListOf<ScheduleCommand>()

    override suspend fun initialize() = Unit

    override suspend fun execute(command: ScheduleCommand): ScheduleSyncResult? {
      commands += command
      if (!publishLocal) return result

      val current = mutableSnapshot.value
      mutableSnapshot.value = when (command) {
        is ScheduleCommand.Create -> current.copy(
          schedules = current.schedules + command.schedule,
        )
        is ScheduleCommand.SaveScheduleWithNewCategory -> current.copy(
          schedules = current.schedules + command.schedule,
          categories = if (publishCategory) {
            current.categories + command.category
          } else {
            current.categories
          },
        )
        else -> error("迁移测试不应发出命令：$command")
      }
      return result
    }
  }

  private companion object {
    const val ACCOUNT_ID = "20210000"
  }
}
