package com.cyxbs.pages.schedule.ui.service

import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.api.ScheduleExternalCreateFailureReason
import com.cyxbs.pages.schedule.api.ScheduleExternalCreateRequest
import com.cyxbs.pages.schedule.api.ScheduleExternalCreateResult
import com.cyxbs.pages.schedule.api.ScheduleExternalCategory
import com.cyxbs.pages.schedule.api.ScheduleExternalRecurrence
import com.cyxbs.pages.schedule.api.ScheduleExternalSource
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceKind
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceTiming
import com.cyxbs.pages.schedule.domain.model.RecurrenceEnd
import com.cyxbs.pages.schedule.domain.model.RecurrenceFrequency
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleTodoState
import com.cyxbs.pages.schedule.domain.repository.ScheduleCommand
import com.cyxbs.pages.schedule.domain.repository.ScheduleRemoteError
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepository
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryStatus
import com.cyxbs.pages.schedule.domain.repository.ScheduleSnapshot
import com.cyxbs.pages.schedule.domain.repository.ScheduleSyncResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/** 外部业务创建日程的领域边界测试。 */
class ScheduleExternalCreateServiceTest {

  /** 没课约整学期事务应保存为每周重复、只进入课表且不进入清单。 */
  @Test
  fun itineraryCreatesWeeklyAffairOutsideTodoList() = runTest {
    val repository = FakeRepository()
    val service = ScheduleExternalCreateService(repository, Clock.System)

    val result = service.create(
      affairRequest(
        recurrence = ScheduleExternalRecurrence.Weekly(20),
      ),
    )

    assertIs<ScheduleExternalCreateResult.Success>(result)
    val schedule = repository.currentSchedule()
    assertNull(schedule.todoState)
    assertTrue(schedule.linkedToCourse)
    assertNull(schedule.categoryId)
    assertEquals(RecurrenceFrequency.WEEKLY, schedule.recurrence?.frequency)
    assertEquals(RecurrenceEnd.Count(20), schedule.recurrence?.end)
  }

  /** 同一账号、来源和来源 ID 重试时不得覆盖日程，也不得执行第二次创建命令。 */
  @Test
  fun repeatedSourceReturnsExistingScheduleWithoutOverwrite() = runTest {
    val repository = FakeRepository()
    val service = ScheduleExternalCreateService(repository, Clock.System)
    val first = assertIs<ScheduleExternalCreateResult.Success>(service.create(affairRequest()))
    val edited = repository.currentSchedule().copy(title = "用户修改后的标题")
    repository.replaceSchedule(edited)

    val retry = assertIs<ScheduleExternalCreateResult.Success>(
      service.create(affairRequest().copy(title = "外部来源的新标题")),
    )

    assertTrue(retry.alreadyExists)
    assertEquals(first.scheduleId, retry.scheduleId)
    assertEquals("用户修改后的标题", repository.currentSchedule().title)
    assertEquals(1, repository.commands.size)
  }

  /** 活动中心选择“其他”时，缺失的固定分类必须和清单在同一次本地命令中创建。 */
  @Test
  fun activityCreatesTodoWithLazyDefaultCategory() = runTest {
    val repository = FakeRepository()
    val service = ScheduleExternalCreateService(repository, Clock.System)

    val result = service.create(activityRequest())

    assertIs<ScheduleExternalCreateResult.Success>(result)
    assertIs<ScheduleCommand.SaveScheduleWithNewCategory>(repository.commands.single())
    val schedule = repository.currentSchedule()
    assertEquals(ScheduleTodoState.PENDING, schedule.todoState)
    assertFalse(schedule.linkedToCourse)
    assertEquals("其他", repository.snapshot.value.categories.single().name)
    assertEquals(repository.snapshot.value.categories.single().id, schedule.categoryId)
    assertEquals(10, schedule.reminder?.offsetMinutes)
  }

  /** 纯事务未进入清单时不能提前携带分类。 */
  @Test
  fun categoryIsRejectedOutsideTodoList() = runTest {
    val repository = FakeRepository()
    val service = ScheduleExternalCreateService(repository, Clock.System)

    val result = service.create(affairRequest().copy(category = ScheduleExternalCategory.OTHER))

    assertEquals(
      ScheduleExternalCreateResult.Failure(ScheduleExternalCreateFailureReason.INVALID_INPUT),
      result,
    )
    assertTrue(repository.commands.isEmpty())
    assertTrue(repository.snapshot.value.schedules.isEmpty())
  }

  /** 远端投递失败不应把已经保存到本地 pending 的日程误报为创建失败。 */
  @Test
  fun remoteFailureAfterLocalSaveStillReturnsSuccess() = runTest {
    val repository = FakeRepository(
      resultAfterApply = ScheduleSyncResult.Failure(ScheduleRemoteError.Timeout, attempted = true),
    )
    val service = ScheduleExternalCreateService(repository, Clock.System)

    val result = service.create(affairRequest())

    assertIs<ScheduleExternalCreateResult.Success>(result)
    assertEquals(1, repository.snapshot.value.schedules.size)
  }

  /** 本地归约拒绝时不能向调用方报告成功。 */
  @Test
  fun localRejectionReturnsFailure() = runTest {
    val repository = FakeRepository(applyLocally = false)
    val service = ScheduleExternalCreateService(repository, Clock.System)

    val result = service.create(affairRequest())

    assertEquals(
      ScheduleExternalCreateResult.Failure(ScheduleExternalCreateFailureReason.LOCAL_SAVE_FAILED),
      result,
    )
    assertTrue(repository.snapshot.value.schedules.isEmpty())
  }

  /** 构造通知中心使用的默认事务请求。 */
  private fun affairRequest(
    recurrence: ScheduleExternalRecurrence? = null,
  ) = ScheduleExternalCreateRequest(
    source = ScheduleExternalSource.ITINERARY,
    sourceId = "1001",
    title = "结伴自习",
    description = "三教 3201",
    timing = ScheduleOccurrenceTiming.Timed(
      MinuteTimeDate(2026, 9, 7, 14, 0),
      90,
      "Asia/Shanghai",
    ),
    recurrence = recurrence,
    kind = ScheduleOccurrenceKind.AFFAIR,
    isInTodoList = false,
    linkedToCourse = true,
  )

  /** 构造活动中心使用的默认清单请求。 */
  private fun activityRequest() = ScheduleExternalCreateRequest(
    source = ScheduleExternalSource.UFIELD_ACTIVITY,
    sourceId = "2001",
    title = "校园歌手赛",
    description = "风雨操场",
    timing = ScheduleOccurrenceTiming.Deadline(
      MinuteTimeDate(2026, 9, 8, 19, 0),
      "Asia/Shanghai",
    ),
    reminderOffsetMinutes = 10,
    kind = ScheduleOccurrenceKind.TODO,
    isInTodoList = true,
    linkedToCourse = false,
    category = ScheduleExternalCategory.OTHER,
  )

  /** 仅实现创建命令的仓库替身，并同步发布创建后的快照。 */
  private class FakeRepository(
    private val applyLocally: Boolean = true,
    private val resultAfterApply: ScheduleSyncResult = ScheduleSyncResult.Success(),
  ) : ScheduleRepository {
    private val mutableSnapshot = MutableStateFlow(
      ScheduleSnapshot(
        accountId = "2020214988",
        status = ScheduleRepositoryStatus.Ready(0, false),
      ),
    )
    override val snapshot: StateFlow<ScheduleSnapshot> = mutableSnapshot
    val commands = mutableListOf<ScheduleCommand>()

    override suspend fun initialize() = Unit

    override suspend fun execute(command: ScheduleCommand): ScheduleSyncResult {
      commands += command
      if (!applyLocally) {
        return ScheduleSyncResult.Failure(
          ScheduleRemoteError.MutationRejected(
            com.cyxbs.pages.schedule.domain.repository.ScheduleMutationBusinessRejectionReason.INVALID_REQUEST,
          ),
          attempted = false,
        )
      }
      mutableSnapshot.value = when (command) {
        is ScheduleCommand.Create -> mutableSnapshot.value.copy(
          schedules = mutableSnapshot.value.schedules + command.schedule,
        )
        is ScheduleCommand.SaveScheduleWithNewCategory -> mutableSnapshot.value.copy(
          categories = mutableSnapshot.value.categories + command.category,
          schedules = mutableSnapshot.value.schedules + command.schedule,
        )
        else -> error("Unexpected command: ${command::class.simpleName}")
      }
      return resultAfterApply
    }

    /** 返回唯一创建日程，测试若意外创建重复数据会立即失败。 */
    fun currentSchedule(): Schedule = snapshot.value.schedules.single()

    /** 模拟用户后续编辑已创建的外部来源日程。 */
    fun replaceSchedule(schedule: Schedule) {
      mutableSnapshot.value = mutableSnapshot.value.copy(schedules = listOf(schedule))
    }
  }
}
