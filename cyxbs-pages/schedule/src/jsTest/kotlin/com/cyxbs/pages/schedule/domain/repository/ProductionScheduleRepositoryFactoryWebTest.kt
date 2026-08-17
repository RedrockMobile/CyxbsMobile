package com.cyxbs.pages.schedule.domain.repository

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.pages.schedule.domain.model.CategoryId
import com.cyxbs.pages.schedule.domain.model.ScheduleCategory
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock

/** Web 生产工厂在 JS 上保持最小 READ_ONLY unavailable 合同。 */
class ProductionScheduleRepositoryFactoryWebTest {
  @Test
  fun webProductionFactoryIsReadOnlyAndNeverAttemptsRemoteWork() = runTest {
    val repository = createProductionScheduleRepositoryFactory(Clock.System)
      .create(AccountSession(1, AccountState.Login(ACCOUNT_ID)))

    assertEquals(ScheduleRepositoryMutationMode.READ_ONLY, repository.mutationMode)
    val beforeInitialize = assertIs<ScheduleRepositoryStatus.Unavailable>(repository.snapshot.value.status)
    assertEquals(ScheduleRemoteError.BackendNotDeployed, beforeInitialize.error)
    assertEquals(0, beforeInitialize.pendingCount)
    assertEquals(ACCOUNT_ID, repository.snapshot.value.accountId)

    repository.initialize()

    listOf(
      ScheduleCommand.RequestSync,
      ScheduleCommand.CreateCategory(ScheduleCategory(CategoryId("category-1"), 0, "分类", null, 0)),
    ).forEach { command ->
      assertEquals(
        ScheduleSyncResult.Failure(ScheduleRemoteError.BackendNotDeployed, attempted = false),
        repository.execute(command),
      )
    }
    assertTrue(repository.calendarChanges.toList().isEmpty())
  }

  private companion object {
    private const val ACCOUNT_ID = "web-unavailable"
  }
}
