package com.cyxbs.pages.schedule.domain

import com.cyxbs.pages.schedule.domain.model.CategoryId
import com.cyxbs.pages.schedule.domain.model.FieldPatch
import com.cyxbs.pages.schedule.domain.model.IsoWeekDay
import com.cyxbs.pages.schedule.domain.model.MutationId
import com.cyxbs.pages.schedule.domain.model.OccurrencePatch
import com.cyxbs.pages.schedule.domain.model.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.model.RecurrenceEnd
import com.cyxbs.pages.schedule.domain.model.RecurrenceFrequency
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.RecurrenceRule
import com.cyxbs.pages.schedule.domain.model.ReminderChannel
import com.cyxbs.pages.schedule.domain.model.ReminderId
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleKind
import com.cyxbs.pages.schedule.domain.model.ScheduleTodoState
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrenceException
import com.cyxbs.pages.schedule.domain.model.ScheduleReminder
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.validation.ScheduleValidator
import com.cyxbs.pages.schedule.domain.validation.ScheduleOccurrenceRelationValidator
import com.cyxbs.pages.schedule.domain.validation.canonicalized
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class ScheduleDomainTest {
  @Test
  fun timingRepresentsFourDistinctStates() {
    val timed: ScheduleTiming = ScheduleTiming.Timed(MinuteTimeDate(2026, 7, 12, 9, 0), 90, "Asia/Shanghai")
    val deadline: ScheduleTiming = ScheduleTiming.Deadline(MinuteTimeDate(2026, 7, 12, 18, 0), "Asia/Shanghai")
    val allDay: ScheduleTiming = ScheduleTiming.AllDay(Date(2026, 7, 12), 2)
    val unscheduled: ScheduleTiming = ScheduleTiming.Unscheduled

    assertIs<ScheduleTiming.Timed>(timed)
    assertIs<ScheduleTiming.Deadline>(deadline)
    assertIs<ScheduleTiming.AllDay>(allDay)
    assertEquals(ScheduleTiming.Unscheduled, unscheduled)
  }

  @Test
  fun validatorRejectsTimingRecurrenceReminderAndRepeatedCompletionViolations() {
    val schedule = validSchedule().copy(
      timing = ScheduleTiming.Timed(MinuteTimeDate(2026, 7, 12, 9, 0), 0, "Not/AZone"),
      recurrence = RecurrenceRule(
        frequency = RecurrenceFrequency.MONTHLY,
        interval = 0,
        byMonthDays = setOf(0, 32),
        byMonths = setOf(0, 13),
        end = RecurrenceEnd.Count(0),
      ),
      reminders = listOf(
        ScheduleReminder(ReminderId("same"), -1, ReminderChannel.DEVICE),
        ScheduleReminder(ReminderId("same"), 0, ReminderChannel.PUSH),
      ),
      todoState = ScheduleTodoState.COMPLETED,
    )

    val fields = ScheduleValidator.validate(schedule).map { it.field }.toSet()
    assertTrue("timing.durationMinutes" in fields)
    assertTrue("timing.timeZoneId" in fields)
    assertTrue("recurrence.interval" in fields)
    assertTrue("recurrence.byMonthDays" in fields)
    assertTrue("recurrence.byMonths" in fields)
    assertTrue("recurrence.end.count" in fields)
    assertTrue("reminders.id" in fields)
    assertTrue("reminders[0].offsetMinutes" in fields)
    assertTrue("reminders[1].channel" in fields)
    assertTrue("todoState" in fields)
  }

  /** TODO/AFFAIR 的来源、清单成员与课表投射组合必须在客户端边界和后端保持同义。 */
  @Test
  fun validatorEnforcesTodoAndAffairMembershipMatrix() {
    val timed = ScheduleTiming.Timed(MinuteTimeDate(2026, 7, 12, 9, 0), 60, "Asia/Shanghai")
    val affair = validSchedule().copy(
      timing = timed,
      kind = ScheduleKind.AFFAIR,
      todoState = null,
      linkedToCourse = true,
    )

    assertTrue(ScheduleValidator.validate(affair).isEmpty())
    assertTrue(ScheduleValidator.validate(affair.copy(todoState = ScheduleTodoState.COMPLETED)).isEmpty())
    assertTrue(ScheduleValidator.validate(validSchedule().copy(todoState = null)).any { it.field == "todoState" })
    assertTrue(
      ScheduleValidator.validate(
        validSchedule().copy(timing = ScheduleTiming.Unscheduled, linkedToCourse = true),
      ).any { it.field == "linkedToCourse" },
    )
    assertTrue(ScheduleValidator.validate(affair.copy(linkedToCourse = false)).any { it.field == "linkedToCourse" })
    assertTrue(ScheduleValidator.validate(affair.copy(timing = ScheduleTiming.AllDay(Date(2026, 7, 12)))).any {
      it.field == "timing"
    })
  }

  /** 纯事务没有完成态，因此不能伪造 COMPLETED occurrence；关联清单后才允许。 */
  @Test
  fun completedOccurrenceRequiresParentTodoMembership() {
    val parent = validSchedule().copy(
      timing = ScheduleTiming.Timed(MinuteTimeDate(2026, 7, 12, 9, 0), 60, "Asia/Shanghai"),
      recurrence = RecurrenceRule(RecurrenceFrequency.DAILY),
      kind = ScheduleKind.AFFAIR,
      todoState = null,
      linkedToCourse = true,
    )
    val exception = ScheduleOccurrenceException(
      scheduleId = parent.id,
      recurrenceId = RecurrenceId(MinuteTimeDate(2026, 7, 12, 9, 0), "Asia/Shanghai", false),
      revision = 0,
      status = OccurrenceStatus.COMPLETED,
      patch = null,
      createdAt = Instant.fromEpochMilliseconds(1),
      updatedAt = Instant.fromEpochMilliseconds(2),
    )

    assertFailsWith<IllegalArgumentException> {
      ScheduleOccurrenceRelationValidator.requireValid(parent, exception, setOf("work"))
    }
    ScheduleOccurrenceRelationValidator.requireValid(
      parent.copy(todoState = ScheduleTodoState.PENDING),
      exception,
      setOf("work"),
    )
  }

  @Test
  fun validatorRejectsWeeklyByMonthDayInsteadOfSilentlyIgnoringIt() {
    val issues = ScheduleValidator.validate(
      RecurrenceRule(
        frequency = RecurrenceFrequency.WEEKLY,
        byWeekDays = setOf(IsoWeekDay.MONDAY),
        byMonthDays = setOf(15),
      )
    )

    assertTrue(issues.any {
      it.field == "recurrence.byMonthDays" && it.message.contains("unsupported")
    })
  }

  @Test
  fun validatorRejectsUnscheduledRecurringScheduleButAllowsUnscheduledOneOff() {
    val unscheduled = validSchedule().copy(timing = ScheduleTiming.Unscheduled)

    assertTrue(ScheduleValidator.validate(unscheduled).isEmpty())
    val issues = ScheduleValidator.validate(
      unscheduled.copy(recurrence = RecurrenceRule(RecurrenceFrequency.DAILY))
    )
    assertTrue(issues.any {
      it.field == "recurrence" && it.message == "unscheduled schedules cannot have recurrence"
    })
  }

  @Test
  fun validatorAcceptsCrossDayDurationAndValidZone() {
    val issues = ScheduleValidator.validate(
      validSchedule().copy(
        timing = ScheduleTiming.Timed(MinuteTimeDate(2026, 7, 12, 23, 30), 180, "Asia/Shanghai")
      )
    )
    assertTrue(issues.isEmpty(), issues.toString())
  }

  @Test
  fun canonicalizerProducesStableSortedIterationOrder() {
    val canonical = RecurrenceRule(
      frequency = RecurrenceFrequency.YEARLY,
      byWeekDays = linkedSetOf(IsoWeekDay.SUNDAY, IsoWeekDay.MONDAY, IsoWeekDay.FRIDAY),
      byMonthDays = linkedSetOf(20, -1, 3),
      byMonths = linkedSetOf(12, 1, 6),
    ).canonicalized()

    assertEquals(listOf(IsoWeekDay.MONDAY, IsoWeekDay.FRIDAY, IsoWeekDay.SUNDAY), canonical.byWeekDays.toList())
    assertEquals(listOf(-1, 3, 20), canonical.byMonthDays.toList())
    assertEquals(listOf(1, 6, 12), canonical.byMonths.toList())
  }

  @Test
  fun strictIdsRejectNonCanonicalOrNonV7Values() {
    val canonical = "018f8e2a-7b4c-7abc-8def-0123456789ab"
    assertEquals(canonical, ScheduleId(canonical).value)
    assertEquals(canonical, MutationId(canonical).value)
    assertFailsWith<IllegalArgumentException> { ScheduleId("018F8E2A-7B4C-7ABC-8DEF-0123456789AB") }
    assertFailsWith<IllegalArgumentException> { ScheduleId("018f8e2a-7b4c-4abc-8def-0123456789ab") }
    assertFailsWith<IllegalArgumentException> { MutationId("018f8e2a-7b4c-7abc-cdef-0123456789ab") }
  }

  @Test
  fun occurrenceIdentityEnforcesAllDayAndTimedZoneSemantics() {
    val allDay = RecurrenceId(MinuteTimeDate(2026, 7, 12, 0, 0), null, true)
    val timed = RecurrenceId(MinuteTimeDate(2026, 7, 12, 9, 0), "Asia/Shanghai", false)
    assertTrue(ScheduleValidator.validate(allDay).isEmpty())
    assertTrue(ScheduleValidator.validate(timed).isEmpty())

    val exception = ScheduleOccurrenceException(
      scheduleId = validId,
      recurrenceId = timed,
      revision = 1,
      status = OccurrenceStatus.COMPLETED,
      patch = null,
      createdAt = Instant.fromEpochMilliseconds(1),
      updatedAt = Instant.fromEpochMilliseconds(2),
    )
    assertTrue(ScheduleValidator.validate(exception).isEmpty())
    assertTrue(
      ScheduleValidator.validate(
        exception.copy(
          patch = OccurrencePatch(
            timing = FieldPatch.Replace(
              ScheduleTiming.AllDay(Date(2026, 7, 12)),
            ),
            title = FieldPatch.Replace("改期后完成"),
          ),
        )
      ).isEmpty()
    )
  }

  @Test
  fun occurrencePatchRejectsClearTitleAndTimingAndBlankReplacement() {
    val issues = ScheduleValidator.validate(
      OccurrencePatch(
        timing = FieldPatch.Clear,
        title = FieldPatch.Clear,
        description = FieldPatch.Replace("   "),
      )
    )

    assertTrue(issues.any { it.field == "patch.timing" })
    assertTrue(issues.any { it.field == "patch.title" })
    assertTrue(issues.any { it.field == "patch.description" })
  }

  @Test
  fun occurrencePatchValidatesAtomicTimingReplacement() {
    val issues = ScheduleValidator.validate(
      OccurrencePatch(
        timing = FieldPatch.Replace(
          ScheduleTiming.Timed(MinuteTimeDate(2026, 7, 12, 10, 0), 0, "Not/AZone")
        )
      )
    )

    assertTrue(issues.any { it.field == "patch.timing.durationMinutes" })
    assertTrue(issues.any { it.field == "patch.timing.timeZoneId" })
  }

  @Test
  fun descriptionCategoryAndRemindersAllowExplicitClear() {
    assertTrue(
      ScheduleValidator.validate(
        OccurrencePatch(
          description = FieldPatch.Clear,
          categoryId = FieldPatch.Clear,
          reminders = FieldPatch.Clear,
        )
      ).isEmpty()
    )
    assertTrue(
      ScheduleValidator.validate(
        OccurrencePatch(reminders = FieldPatch.Replace(emptyList()))
      ).isEmpty()
    )
  }

  private fun validSchedule() = Schedule(
    id = validId,
    revision = 0,
    title = "Domain test",
    description = "",
    categoryId = CategoryId("work"),
    timing = ScheduleTiming.AllDay(Date(2026, 7, 12)),
    recurrence = null,
    reminders = emptyList(),
    todoState = ScheduleTodoState.PENDING,
    createdAt = Instant.fromEpochMilliseconds(1),
    updatedAt = Instant.fromEpochMilliseconds(2),
  )

  private companion object {
    val validId = ScheduleId("018f8e2a-7b4c-7abc-8def-0123456789ab")
  }
}
