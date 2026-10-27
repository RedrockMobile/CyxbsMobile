package com.cyxbs.pages.schedule.ui.model

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.domain.model.FieldPatch
import com.cyxbs.pages.schedule.domain.model.OccurrencePatch
import com.cyxbs.pages.schedule.domain.model.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.model.OccurrenceTime
import com.cyxbs.pages.schedule.domain.model.RecurrenceFrequency
import com.cyxbs.pages.schedule.domain.model.RecurrenceEnd
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.RecurrenceRule
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrenceAdjustment
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.model.ScheduleTodoState
import com.cyxbs.pages.schedule.domain.repository.ScheduleSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/** 详情按稳定 identity 重新解析当前 occurrence 的回归测试。 */
class ScheduleUiModelsTest {

  /** 同步改变规则、时分或时间类型后，详情持有的旧 identity 应返回空供 UI 关闭，而不是抛异常。 */
  @Test
  fun staleOccurrenceIdentityReturnsNullAfterSeriesUpdates() {
    val parent = parentSchedule()
    val id = RecurrenceId(MinuteTimeDate(2026, 9, 4, 15, 0), "Asia/Shanghai", false)
    assertNotNull(ScheduleSnapshot(schedules = listOf(parent)).occurrenceByIdentity(parent.id, id))
    val updatedParents = listOf(
      parent.copy(recurrence = parent.recurrence!!.copy(end = RecurrenceEnd.Count(1))),
      parent.copy(recurrence = parent.recurrence!!.copy(end = RecurrenceEnd.Until(Date(2026, 9, 3)))),
      parent.copy(timing = ScheduleTiming.Timed(MinuteTimeDate(2026, 9, 2, 10, 0), 60, "Asia/Shanghai")),
      parent.copy(timing = ScheduleTiming.Timed(MinuteTimeDate(2026, 9, 2, 15, 0), 60, "UTC")),
      parent.copy(timing = ScheduleTiming.AllDay(Date(2026, 9, 2))),
      parent.copy(recurrence = null),
    )
    updatedParents.forEach { updated ->
      assertNull(ScheduleSnapshot(schedules = listOf(updated)).occurrenceByIdentity(parent.id, id))
    }
  }

  /** 单次调整被还原后，同一 identity 必须立即回到父系列字段，不能继续展示点击时的旧快照。 */
  @Test
  fun occurrenceByIdentityReflectsAdjustmentAndSubsequentRestore() {
    val parent = parentSchedule()
    val recurrenceId = RecurrenceId(
      originalDateTime = MinuteTimeDate(2026, 9, 4, 15, 0),
      timeZoneId = "Asia/Shanghai",
      allDay = false,
    )
    val exception = ScheduleOccurrenceAdjustment(
      scheduleId = parent.id,
      recurrenceId = recurrenceId,
      revision = 1,
      status = OccurrenceStatus.ACTIVE,
      patch = OccurrencePatch(
        title = FieldPatch.Replace("单次标题"),
        time = FieldPatch.Replace(
          OccurrenceTime.TimeRange(
            startMinuteOfDay = 18 * 60,
            durationMinutes = 60,
            timeZoneId = "Asia/Shanghai",
          ),
        ),
      ),
      createdAt = NOW,
      updatedAt = NOW,
    )

    val overridden = ScheduleSnapshot(listOf(parent), listOf(exception))
      .occurrenceByIdentity(parent.id, recurrenceId)
    assertEquals("单次标题", overridden?.title)
    assertEquals(18, (overridden?.timing as ScheduleTiming.Timed).start.time.hour)

    val restored = ScheduleSnapshot(schedules = listOf(parent))
      .occurrenceByIdentity(parent.id, recurrenceId)
    assertEquals("父系列", restored?.title)
    assertEquals(15, (restored?.timing as ScheduleTiming.Timed).start.time.hour)
  }

  /** 被取消的 occurrence 没有可展示详情，identity 查询必须返回空。 */
  @Test
  fun occurrenceByIdentityOmitsCancelledOccurrence() {
    val parent = parentSchedule()
    val recurrenceId = RecurrenceId(
      originalDateTime = MinuteTimeDate(2026, 9, 4, 15, 0),
      timeZoneId = "Asia/Shanghai",
      allDay = false,
    )
    val cancelled = ScheduleOccurrenceAdjustment(
      scheduleId = parent.id,
      recurrenceId = recurrenceId,
      revision = 1,
      status = OccurrenceStatus.CANCELLED,
      patch = null,
      createdAt = NOW,
      updatedAt = NOW,
    )

    assertNull(
      ScheduleSnapshot(listOf(parent), listOf(cancelled))
        .occurrenceByIdentity(parent.id, recurrenceId),
    )
  }

  private fun parentSchedule(): Schedule = Schedule(
    id = ScheduleId("01a0600f-9d85-7ba8-9117-2b2acd652a35"),
    revision = 1,
    title = "父系列",
    description = "父备注",
    categoryId = null,
    timing = ScheduleTiming.Timed(
      start = MinuteTimeDate(2026, 9, 2, 15, 0),
      durationMinutes = 60,
      timeZoneId = "Asia/Shanghai",
    ),
    recurrence = RecurrenceRule(
      frequency = RecurrenceFrequency.DAILY,
      interval = 1,
    ),
    reminder = null,
    todoState = ScheduleTodoState.PENDING,
    createdAt = NOW,
    updatedAt = NOW,
  )

  private companion object {
    val NOW: Instant = Instant.parse("2026-09-02T00:00:00Z")
  }
}
