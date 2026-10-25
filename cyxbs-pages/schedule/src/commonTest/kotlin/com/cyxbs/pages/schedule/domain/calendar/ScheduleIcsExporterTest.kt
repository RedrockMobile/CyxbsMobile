package com.cyxbs.pages.schedule.domain.calendar

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.domain.model.FieldPatch
import com.cyxbs.pages.schedule.domain.model.IsoWeekDay
import com.cyxbs.pages.schedule.domain.model.OccurrencePatch
import com.cyxbs.pages.schedule.domain.model.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.model.OccurrenceTime
import com.cyxbs.pages.schedule.domain.model.RecurrenceFrequency
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.RecurrenceRule
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrenceAdjustment
import com.cyxbs.pages.schedule.domain.model.ScheduleReminder
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.model.ScheduleTodoState
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryStatus
import com.cyxbs.pages.schedule.domain.repository.ScheduleSnapshot
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class ScheduleIcsExporterTest {
  private val generatedAt = Instant.parse("2026-09-04T00:00:00Z")

  /** 系列、提前提醒、移动单次与取消单次应使用同一个 UID，并保留各自原始 recurrence identity。 */
  @Test
  fun recurringScheduleExportsAlarmMovedOccurrenceAndCancelledOccurrence() {
    val schedule = schedule(
      id = id(1),
      title = "每周例会",
      timing = ScheduleTiming.Timed(
        start = MinuteTimeDate(2026, 9, 7, 9, 0),
        durationMinutes = 60,
        timeZoneId = "Asia/Shanghai",
      ),
      recurrence = RecurrenceRule(
        frequency = RecurrenceFrequency.WEEKLY,
        byWeekDays = setOf(IsoWeekDay.MONDAY),
      ),
      reminder = ScheduleReminder(10),
    )
    val moved = adjustment(
      schedule = schedule,
      original = MinuteTimeDate(2026, 9, 14, 9, 0),
      status = OccurrenceStatus.ACTIVE,
      patch = OccurrencePatch(
        time = FieldPatch.Replace(
          OccurrenceTime.TimeRange(
            startMinuteOfDay = 11 * 60,
            durationMinutes = 45,
            timeZoneId = "Asia/Shanghai",
          ),
        ),
        title = FieldPatch.Replace("改到十一点"),
      ),
    )
    val cancelled = adjustment(
      schedule = schedule,
      original = MinuteTimeDate(2026, 9, 21, 9, 0),
      status = OccurrenceStatus.CANCELLED,
    )

    val document = ScheduleIcsExporter.export(
      snapshot(listOf(schedule), listOf(moved, cancelled)),
      generatedAt,
    )
    val unfolded = document.content.replace("\r\n ", "")

    assertEquals(1, document.scheduleCount)
    assertEquals(2, document.adjustmentCount)
    assertEquals(0, document.skippedScheduleCount)
    assertEquals(3, unfolded.windowed("BEGIN:VEVENT".length).count { it == "BEGIN:VEVENT" })
    assertTrue("UID:${schedule.id.value}@schedule.cyxbs" in unfolded)
    assertTrue("RRULE:FREQ=WEEKLY;BYDAY=MO" in unfolded)
    assertTrue("TRIGGER:-PT10M" in unfolded)
    assertTrue("RECURRENCE-ID;TZID=Asia/Shanghai:20260914T090000" in unfolded)
    assertTrue("DTSTART;TZID=Asia/Shanghai:20260914T110000" in unfolded)
    assertTrue("DTEND;TZID=Asia/Shanghai:20260914T114500" in unfolded)
    assertTrue("RECURRENCE-ID;TZID=Asia/Shanghai:20260921T090000" in unfolded)
    assertTrue("STATUS:CANCELLED" in unfolded)
  }

  /** 全天使用排他结束日期，时间点保持零时长；无日期和已完成非重复清单不进入文件。 */
  @Test
  fun allDayAndDeadlineKeepTimingWhileUnscheduledAndCompletedAreSkipped() {
    val allDay = schedule(id(2), "全天", ScheduleTiming.AllDay(Date(2026, 9, 8)))
    val deadline = schedule(
      id = id(3),
      title = "时间点",
      timing = ScheduleTiming.Deadline(MinuteTimeDate(2026, 9, 8, 14, 30), "Asia/Shanghai"),
      reminder = ScheduleReminder(0),
    )
    val unscheduled = schedule(id(4), "未设置日期", ScheduleTiming.Unscheduled)
    val completed = schedule(id(5), "已完成", ScheduleTiming.AllDay(Date(2026, 9, 9)))
      .copy(todoState = ScheduleTodoState.COMPLETED)

    val document = ScheduleIcsExporter.export(
      snapshot(listOf(allDay, deadline, unscheduled, completed)),
      generatedAt,
    )

    assertEquals(2, document.scheduleCount)
    assertEquals(0, document.adjustmentCount)
    assertEquals(2, document.skippedScheduleCount)
    assertTrue("DTSTART;VALUE=DATE:20260908\r\nDTEND;VALUE=DATE:20260909" in document.content)
    assertTrue(
      "DTSTART;TZID=Asia/Shanghai:20260908T143000\r\n" +
        "DTEND;TZID=Asia/Shanghai:20260908T143000" in document.content,
    )
    assertTrue("TRIGGER:PT0M" in document.content)
    assertTrue("未设置日期" !in document.content)
    assertTrue("已完成" !in document.content)
  }

  /** 中文和 emoji 长文本必须按 UTF-8 byte 折行，并正确转义会改变 ICS 字段结构的字符。 */
  @Test
  fun textIsEscapedAndEveryPhysicalLineFitsTheRfcOctetLimit() {
    val title = "课程,提醒;带\\反斜杠\n" + "很长的中文🙂".repeat(12)
    val document = ScheduleIcsExporter.export(
      snapshot(listOf(schedule(id(6), title, ScheduleTiming.AllDay(Date(2026, 9, 10))))),
      generatedAt,
    )
    val unfolded = document.content.replace("\r\n ", "")

    assertTrue("SUMMARY:课程\\,提醒\\;带\\\\反斜杠\\n" in unfolded)
    document.content.split("\r\n")
      .filter { it.isNotEmpty() }
      .forEach { line ->
        assertTrue(line.encodeToByteArray().size <= 75, "line exceeds 75 octets: $line")
      }
  }

  @Test
  fun suggestedFileNameUsesTheRequestedLocalDate() {
    assertEquals(
      "掌邮日程-20260904.ics",
      ScheduleIcsExporter.suggestedFileName(
        generatedAt,
        TimeZone.of("Asia/Shanghai"),
      ),
    )
  }

  private fun snapshot(
    schedules: List<Schedule>,
    adjustments: List<ScheduleOccurrenceAdjustment> = emptyList(),
  ) = ScheduleSnapshot(
    schedules = schedules,
    occurrenceAdjustments = adjustments,
    status = ScheduleRepositoryStatus.Ready(0, false),
    accountId = "2020214988",
  )

  /** 构造通过领域校验的清单日程；测试只改变与 ICS 用例直接相关的字段。 */
  private fun schedule(
    id: ScheduleId,
    title: String,
    timing: ScheduleTiming,
    recurrence: RecurrenceRule? = null,
    reminder: ScheduleReminder? = null,
  ) = Schedule(
    id = id,
    revision = 1,
    title = title,
    description = "导出说明",
    categoryId = null,
    timing = timing,
    recurrence = recurrence,
    reminder = reminder,
    todoState = ScheduleTodoState.PENDING,
    createdAt = generatedAt,
    updatedAt = generatedAt,
  )

  /** occurrence identity 始终保留修改前的 09:00，patch 只改变导出后的展示字段。 */
  private fun adjustment(
    schedule: Schedule,
    original: MinuteTimeDate,
    status: OccurrenceStatus,
    patch: OccurrencePatch? = null,
  ) = ScheduleOccurrenceAdjustment(
    scheduleId = schedule.id,
    recurrenceId = RecurrenceId(original, "Asia/Shanghai", false),
    revision = 1,
    status = status,
    patch = patch,
    createdAt = generatedAt,
    updatedAt = generatedAt,
  )

  private fun id(index: Int): ScheduleId =
    ScheduleId("018f0f7c-6000-7000-8000-${index.toString().padStart(12, '0')}")
}
