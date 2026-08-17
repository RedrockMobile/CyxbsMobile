package com.cyxbs.pages.schedule.calendar

import com.cyxbs.pages.schedule.domain.calendar.CalendarEventProjection
import com.cyxbs.pages.schedule.domain.calendar.CalendarExportScope
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionId
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionKind
import com.cyxbs.pages.schedule.domain.calendar.ManagedCalendarEvent
import com.cyxbs.pages.schedule.domain.calendar.PlatformCalendarEventRef
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.repository.ScheduleCalendarChange
import com.cyxbs.pages.schedule.domain.repository.ScheduleCommand
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepository
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryStatus
import com.cyxbs.pages.schedule.domain.repository.ScheduleSnapshot
import com.cyxbs.pages.schedule.domain.repository.ScheduleSyncResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** 协调器从 Schedule 快照到系统日历写入端口的单向 host 合同测试。 */
class ScheduleCalendarExportCoordinatorTest {
  /** 初始全量投影会删除已不在空 Schedule 快照中的受管事件，不读取任何入站业务状态。 */
  @Test
  fun initialSnapshotPlansAndExecutesManagedEventDeletion() = runTest {
    val accountId = "20260001"
    val scope = CalendarExportScope(accountId)
    val scheduleId = ScheduleId("018f0f7c-6000-7000-8000-000000000001")
    val event = ManagedCalendarEvent(
      id = CalendarProjectionId(scope, scheduleId, CalendarProjectionKind.SINGLE),
      fingerprint = "stale-provider-copy",
      platformEventRef = PlatformCalendarEventRef("event-1"),
    )
    val gateway = RecordingExportGateway(listOf(event))
    var authorizationChecks = 0
    val coordinator = ScheduleCalendarExportCoordinator(
      context = null,
      repository = EmptyReadyRepository(accountId),
      accountId = accountId,
      exportScope = scope,
      coroutineScope = this,
      ensureAuthorized = { authorizationChecks += 1 },
      gateway = gateway,
    )

    coordinator.start()
    runCurrent()
    coordinator.stop()
    runCurrent()

    assertEquals(listOf<Set<ScheduleId>?>(null), gateway.queries)
    assertEquals(listOf(event), gateway.deleted)
    assertEquals(true, authorizationChecks > 0)
  }

  /** 仅提供 Ready 空快照；任何 mutation 都表示单向导出测试越过了只读仓库边界。 */
  private class EmptyReadyRepository(accountId: String) : ScheduleRepository {
    override val snapshot = MutableStateFlow(
      ScheduleSnapshot(
        status = ScheduleRepositoryStatus.Ready(pendingCount = 0, hasPendingDeletes = false),
        accountId = accountId,
      ),
    )
    override val calendarChanges = emptyFlow<ScheduleCalendarChange>()

    override suspend fun initialize() = Unit

    override suspend fun execute(command: ScheduleCommand): ScheduleSyncResult? =
      error("Single-way exporter must not mutate Schedule repository")
  }

  /** 记录 planner 产生的 Provider 操作；未命中的写入种类均直接失败。 */
  private class RecordingExportGateway(
    private val managedEvents: List<ManagedCalendarEvent>,
  ) : ScheduleCalendarExportEventGateway {
    val queries = mutableListOf<Set<ScheduleId>?>()
    val deleted = mutableListOf<ManagedCalendarEvent>()

    override fun queryManagedEvents(
      scope: CalendarExportScope,
      scheduleIds: Set<ScheduleId>?,
      ensureAuthorized: () -> Unit,
    ): List<ManagedCalendarEvent> {
      ensureAuthorized()
      queries += scheduleIds
      return managedEvents
    }

    override fun createEvent(
      projection: CalendarEventProjection,
      scope: CalendarExportScope,
      ensureAuthorized: () -> Unit,
    ): Long? = error("Empty snapshot must not create Provider events")

    override fun updateEvent(
      projection: CalendarEventProjection,
      eventRef: PlatformCalendarEventRef,
      scope: CalendarExportScope,
      ensureAuthorized: () -> Unit,
    ): Boolean = error("Empty snapshot must not update Provider events")

    override fun deleteEvent(
      event: ManagedCalendarEvent,
      scope: CalendarExportScope,
      ensureAuthorized: () -> Unit,
    ): Boolean {
      ensureAuthorized()
      deleted += event
      return true
    }
  }
}
