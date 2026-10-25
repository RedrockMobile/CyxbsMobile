package com.cyxbs.pages.schedule.domain

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.components.config.time.toLocalDateTime
import com.cyxbs.pages.schedule.domain.model.*
import com.cyxbs.pages.schedule.domain.recurrence.RecurrenceEngine
import com.cyxbs.pages.schedule.domain.recurrence.SeriesSplitter
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class RecurrenceEngineTest {
  @Test fun dailyWeeklyMonthlyYearlyAndCount() {
    assertDates(expand(rule(RecurrenceFrequency.DAILY, count = 3)), "2024-01-31", "2024-02-01", "2024-02-02")
    assertDates(expand(rule(RecurrenceFrequency.WEEKLY, count = 3, days = setOf(IsoWeekDay.WEDNESDAY, IsoWeekDay.FRIDAY))), "2024-01-31", "2024-02-02", "2024-02-07")
    assertDates(expand(rule(RecurrenceFrequency.MONTHLY, count = 3, monthDays = setOf(31))), "2024-01-31", "2024-03-31", "2024-05-31")
    assertDates(expand(
      rule(RecurrenceFrequency.YEARLY, count = 3, monthDays = setOf(29), months = setOf(2)),
      start = MinuteTimeDate(2024, 2, 29, 9, 0),
      end = MinuteTimeDate(2033, 1, 1, 0, 0),
    ), "2024-02-29", "2028-02-29", "2032-02-29")
  }

  @Test fun untilIsInclusiveAndNegativeMonthDayResolvesMonthEnd() {
    val until = Date(2024, 3, 31)
    assertDates(expand(rule(RecurrenceFrequency.MONTHLY, end = RecurrenceEnd.Until(until), monthDays = setOf(-1))), "2024-01-31", "2024-02-29", "2024-03-31")
  }

  /** 当前 MONTHLY 子集只按月日筛选，并跳过当月不存在的日期。 */
  @Test fun monthlySelectorsUseMonthDaysAndSkipInvalidDates() {
    val combined = RecurrenceRule(
      frequency = RecurrenceFrequency.MONTHLY,
      byMonthDays = setOf(5, 31),
      end = RecurrenceEnd.Count(4),
    )
    assertDates(expand(combined), "2024-01-31", "2024-02-05", "2024-03-05", "2024-03-31")
  }

  /** 当前 YEARLY 子集由月份和月日共同确定成员。 */
  @Test fun yearlySelectorsUseMonthsAndMonthDays() {
    val combined = RecurrenceRule(
      frequency = RecurrenceFrequency.YEARLY,
      byMonthDays = setOf(5),
      byMonths = setOf(2, 8),
      end = RecurrenceEnd.Count(3),
    )
    assertDates(expand(combined), "2024-02-05", "2024-08-05", "2025-02-05")
  }

  @Test fun timedDurationCanCrossMidnight() {
    val occurrence = expand(rule(RecurrenceFrequency.DAILY, count = 1), duration = 180).single()
    assertEquals(180, (occurrence.timing as ScheduleTiming.Timed).durationMinutes)
  }

  @Test fun dstKeepsWallClockInsteadOfFixedInstantInterval() {
    val start = MinuteTimeDate(2024, 3, 9, 9, 0)
    val occurrences = expand(rule(RecurrenceFrequency.DAILY, count = 3), start, MinuteTimeDate(2024, 3, 12, 0, 0), zone = "America/New_York")
    assertTrue(occurrences.all { (it.timing as ScheduleTiming.Timed).start.time.hour == 9 })
    val zone = TimeZone.of("America/New_York")
    val instants = occurrences.map { (it.timing as ScheduleTiming.Timed).start.toLocalDateTime().toInstant(zone) }
    assertEquals(23.hours, instants[1] - instants[0])
    assertEquals(24.hours, instants[2] - instants[1])
  }

  @Test fun exceptionsCompleteCancelMoveAndKeepIdentity() {
    val schedule = schedule(rule(RecurrenceFrequency.DAILY, count = 4))
    val base = RecurrenceEngine.expandInRange(schedule, emptyList(), dt(31), MinuteTimeDate(2024, 2, 5, 0, 0))
    val complete = exception(schedule, base[1].recurrenceId!!, OccurrenceStatus.COMPLETED)
    val cancel = exception(schedule, base[2].recurrenceId!!, OccurrenceStatus.CANCELLED)
    val movedId = base[3].recurrenceId!!
    val moved = exception(schedule, movedId, OccurrenceStatus.ACTIVE, OccurrencePatch(
      date = FieldPatch.Replace(Date(2024, 2, 10)),
      time = FieldPatch.Replace(OccurrenceTime.TimeRange(15 * 60, 60, "Asia/Shanghai")),
      title = FieldPatch.Replace("Moved"),
    ))
    val actual = RecurrenceEngine.expandInRange(schedule, listOf(complete, cancel, moved), dt(31), MinuteTimeDate(2024, 2, 11, 0, 0))
    assertEquals(listOf(OccurrenceStatus.ACTIVE, OccurrenceStatus.COMPLETED, OccurrenceStatus.ACTIVE), actual.map { it.status })
    assertEquals(movedId, actual.last().recurrenceId)
    assertEquals("Moved", actual.last().title)
    assertEquals(MinuteTimeDate(2024, 2, 10, 15, 0), (actual.last().timing as ScheduleTiming.Timed).start)
  }

  /**
   * 完成态只覆盖 occurrence 状态；父系列后续修改标题或分类时，未被 patch 的字段必须继续继承新值。
   */
  @Test
  fun completedOccurrenceInheritsLaterParentFieldUpdates() {
    val original = schedule(rule(RecurrenceFrequency.DAILY, count = 2)).copy(
      title = "旧标题",
      categoryId = CategoryId("old-category"),
    )
    val occurrences = RecurrenceEngine.expandInRange(
      original,
      emptyList(),
      dt(31),
      MinuteTimeDate(2024, 2, 2, 0, 0),
    )
    val completed = exception(
      original,
      requireNotNull(occurrences.first().recurrenceId),
      OccurrenceStatus.COMPLETED,
    )
    val updated = original.copy(
      title = "新标题",
      categoryId = CategoryId("new-category"),
    )

    val projected = RecurrenceEngine.expandInRange(
      updated,
      listOf(completed),
      dt(31),
      MinuteTimeDate(2024, 2, 2, 0, 0),
    )

    assertEquals(OccurrenceStatus.COMPLETED, projected.first().status)
    assertTrue(projected.all { it.title == "新标题" })
    assertTrue(projected.all { it.categoryId == CategoryId("new-category") })
  }

  @Test fun forgedExceptionIsRejectedAndRemovingExceptionRestoresDefault() {
    val schedule = schedule(rule(RecurrenceFrequency.DAILY, count = 2))
    val forged = RecurrenceId(MinuteTimeDate(2024, 2, 20, 9, 0), "Asia/Shanghai", false)
    assertFailsWith<IllegalArgumentException> { RecurrenceEngine.expandInRange(schedule, listOf(exception(schedule, forged, OccurrenceStatus.CANCELLED)), dt(31), MinuteTimeDate(2024, 3, 1, 0, 0)) }
    val id = RecurrenceEngine.expandInRange(schedule, emptyList(), dt(31), MinuteTimeDate(2024, 2, 3, 0, 0))[1].recurrenceId!!
    assertEquals(1, RecurrenceEngine.expandInRange(schedule, listOf(exception(schedule, id, OccurrenceStatus.CANCELLED)), dt(31), MinuteTimeDate(2024, 2, 3, 0, 0)).size)
    assertEquals(2, RecurrenceEngine.expandInRange(schedule, emptyList(), dt(31), MinuteTimeDate(2024, 2, 3, 0, 0)).size)
  }

  /** 单次时间覆盖与日期覆盖解耦，因此可以切换时间形态，同时继续继承系列日期。 */
  @Test fun timeCanChangeTimingKindWithoutChangingOccurrenceDate() {
    val schedule = schedule(rule(RecurrenceFrequency.DAILY, count = 1))
    val id = RecurrenceEngine.expandInRange(schedule, emptyList(), dt(31), dt(31).plusMinutes(1))
      .single().recurrenceId!!
    val replacements = listOf<OccurrenceTime>(
      OccurrenceTime.TimeRange(10 * 60, 60, "Asia/Shanghai"),
      OccurrenceTime.TimePoint(10 * 60, "Asia/Shanghai"),
      OccurrenceTime.AllDay,
    )

    replacements.forEach { replacement ->
      val raw = exception(
        schedule,
        id,
        OccurrenceStatus.ACTIVE,
        OccurrencePatch(time = FieldPatch.Replace(replacement)),
      )
      val occurrence = RecurrenceEngine.expandInRange(
        schedule,
        listOf(raw),
        dt(31),
        MinuteTimeDate(2024, 2, 3, 0, 0),
      ).single()
      val actualDate = when (val timing = occurrence.timing) {
        is ScheduleTiming.Timed -> timing.start.date
        is ScheduleTiming.Deadline -> timing.due.date
        is ScheduleTiming.AllDay -> timing.date
        ScheduleTiming.Unscheduled -> error("重复实例不应被投影为未排期")
      }
      assertEquals(Date(2024, 1, 31), actualDate)
    }
  }

  /**
   * 整系列日期和时间改变后，未覆盖的维度继续随系列移动，单次显式覆盖的维度保持用户设置。
   * recurrenceId 仍使用稳定原始日期，但时分按父系列当前时间重建。
   */
  @Test
  fun seriesDateAndTimeChangesRespectIndependentOccurrenceAdjustments() {
    val changedSeries = schedule(
      rule(RecurrenceFrequency.DAILY, count = 4),
      start = MinuteTimeDate(2024, 2, 1, 10, 0),
    ).copy(recurrenceAnchorDate = Date(2024, 1, 31))
    fun id(date: Date) = RecurrenceId(MinuteTimeDate(date, 10, 0), "Asia/Shanghai", false)
    val dateOnly = exception(
      changedSeries,
      id(Date(2024, 2, 1)),
      OccurrenceStatus.ACTIVE,
      OccurrencePatch(date = FieldPatch.Replace(Date(2024, 2, 10))),
    )
    val timeOnly = exception(
      changedSeries,
      id(Date(2024, 2, 2)),
      OccurrenceStatus.ACTIVE,
      OccurrencePatch(
        time = FieldPatch.Replace(OccurrenceTime.TimeRange(15 * 60, 90, "Asia/Shanghai")),
      ),
    )
    val both = exception(
      changedSeries,
      id(Date(2024, 2, 3)),
      OccurrenceStatus.ACTIVE,
      OccurrencePatch(
        date = FieldPatch.Replace(Date(2024, 2, 12)),
        time = FieldPatch.Replace(OccurrenceTime.TimePoint(16 * 60, "Asia/Shanghai")),
      ),
    )

    val occurrences = RecurrenceEngine.expandInRange(
      changedSeries,
      listOf(dateOnly, timeOnly, both),
      MinuteTimeDate(2024, 2, 1, 0, 0),
      MinuteTimeDate(2024, 2, 14, 0, 0),
    ).associateBy { it.recurrenceId }

    assertEquals(
      MinuteTimeDate(2024, 2, 10, 10, 0),
      (occurrences.getValue(dateOnly.recurrenceId).timing as ScheduleTiming.Timed).start,
    )
    assertEquals(
      ScheduleTiming.Timed(MinuteTimeDate(2024, 2, 3, 15, 0), 90, "Asia/Shanghai"),
      occurrences.getValue(timeOnly.recurrenceId).timing,
    )
    assertEquals(
      ScheduleTiming.Deadline(MinuteTimeDate(2024, 2, 12, 16, 0), "Asia/Shanghai"),
      occurrences.getValue(both.recurrenceId).timing,
    )
  }

  @Test fun clearReminderAlsoKeepsOtherClearPatches() {
    val schedule = schedule(rule(RecurrenceFrequency.DAILY, count = 1)).copy(
      categoryId = CategoryId("study"),
      description = "Body",
      reminder = ScheduleReminder(10),
    )
    val id = RecurrenceEngine.expandInRange(schedule, emptyList(), dt(31), dt(31).plusMinutes(1)).single().recurrenceId!!
    val clearPatch = OccurrencePatch(
      description = FieldPatch.Clear,
      categoryId = FieldPatch.Clear,
      reminder = FieldPatch.Clear,
    )
    val cleared = RecurrenceEngine.expandInRange(schedule, listOf(exception(schedule, id, OccurrenceStatus.ACTIVE, clearPatch)), dt(31), dt(31).plusMinutes(1)).single()

    assertEquals("", cleared.description)
    assertNull(cleared.categoryId)
    assertNull(cleared.reminder)
  }

  @Test fun halfOpenWindowUsesEffectiveOccupiedIntervalAndRejectsEmptyWindow() {
    val schedule = schedule(
      rule(RecurrenceFrequency.DAILY, count = 2),
      start = MinuteTimeDate(2024, 1, 31, 23, 0),
      duration = 120,
    )
    val firstWindow = RecurrenceEngine.expandInRange(
      schedule,
      emptyList(),
      MinuteTimeDate(2024, 2, 1, 0, 0),
      MinuteTimeDate(2024, 2, 1, 1, 0),
    )
    val secondWindow = RecurrenceEngine.expandInRange(
      schedule,
      emptyList(),
      MinuteTimeDate(2024, 2, 1, 1, 0),
      MinuteTimeDate(2024, 2, 1, 2, 0),
    )
    assertEquals(1, firstWindow.size)
    assertEquals(emptyList(), secondWindow)
    assertFailsWith<IllegalArgumentException> {
      RecurrenceEngine.expandInRange(schedule, emptyList(), dt(31), dt(31))
    }
    assertFailsWith<IllegalArgumentException> {
      RecurrenceEngine.expandInRange(schedule, emptyList(), dt(31).plusMinutes(1), dt(31))
    }
  }

  @Test fun singleDayDeadlineAndUnscheduledUseTheirBoundedOccupancy() {
    val allDay = schedule(rule(RecurrenceFrequency.DAILY, count = 1)).copy(
      timing = ScheduleTiming.AllDay(Date(2024, 1, 30)),
    )
    assertEquals(
      1,
      RecurrenceEngine.expandInRange(
        allDay,
        emptyList(),
        MinuteTimeDate(2024, 1, 30, 0, 0),
        MinuteTimeDate(2024, 1, 31, 0, 0),
      ).size,
    )

    val deadline = schedule(rule(RecurrenceFrequency.DAILY, count = 1)).copy(
      timing = ScheduleTiming.Deadline(MinuteTimeDate(2024, 1, 31, 9, 0), "Asia/Shanghai"),
    )
    assertEquals(1, RecurrenceEngine.expandInRange(deadline, emptyList(), dt(31), dt(31).plusMinutes(1)).size)
    assertEquals(emptyList(), RecurrenceEngine.expandInRange(deadline, emptyList(), dt(31).plusMinutes(1), dt(31).plusMinutes(2)))

    val unscheduled = schedule(rule(RecurrenceFrequency.DAILY, count = 1)).copy(
      timing = ScheduleTiming.Unscheduled,
      recurrence = null,
    )
    assertEquals(emptyList(), RecurrenceEngine.expandInRange(unscheduled, emptyList(), dt(31), dt(31).plusMinutes(1)))
  }

  /**
   * 周重复必须对全天、时间点和时间段使用同一日期选择规则，同时保留各自的稳定 occurrence identity。
   */
  @Test
  fun weeklyRecurrenceKeepsTimingKindAndIdentityForAllSupportedTimings() {
    val recurrence = rule(
      RecurrenceFrequency.WEEKLY,
      count = 2,
      days = setOf(IsoWeekDay.WEDNESDAY),
    )
    val timings = listOf(
      ScheduleTiming.AllDay(Date(2024, 1, 31)),
      ScheduleTiming.Deadline(dt(31), "Asia/Shanghai"),
      ScheduleTiming.Timed(dt(31), 60, "Asia/Shanghai"),
    )

    timings.forEach { timing ->
      val parent = schedule(recurrence).copy(timing = timing)
      val occurrences = RecurrenceEngine.expandInRange(
        parent,
        emptyList(),
        dt(31),
        MinuteTimeDate(2024, 2, 8, 0, 0),
      )

      assertEquals(2, occurrences.size)
      assertEquals(listOf("2024-01-31", "2024-02-07"), occurrences.map { occurrence ->
        val date = when (val value = occurrence.timing) {
          is ScheduleTiming.AllDay -> value.date
          is ScheduleTiming.Deadline -> value.due.date
          is ScheduleTiming.Timed -> value.start.date
          ScheduleTiming.Unscheduled -> error("周重复不允许无日期 timing")
        }
        date.toString()
      })
      assertEquals(timing::class, occurrences.first().timing::class)
      assertTrue(occurrences.all { it.recurrenceId != null })
      assertEquals(2, occurrences.map { it.recurrenceId }.toSet().size)
    }
  }

  /** 修改周选择器后只按新规则生成，旧星期不能作为残留实例继续出现。 */
  @Test
  fun replacingWeeklySelectorsRemovesOldWeekdays() {
    val original = schedule(RecurrenceRule(
      frequency = RecurrenceFrequency.WEEKLY,
      byWeekDays = setOf(IsoWeekDay.MONDAY, IsoWeekDay.WEDNESDAY, IsoWeekDay.FRIDAY),
      end = RecurrenceEnd.Count(4),
    ))
    val changed = original.copy(recurrence = original.recurrence?.copy(
      byWeekDays = setOf(IsoWeekDay.TUESDAY, IsoWeekDay.THURSDAY),
    ))

    val oldDates = RecurrenceEngine.expandInRange(
      original,
      emptyList(),
      dt(31),
      MinuteTimeDate(2024, 2, 15, 0, 0),
    ).map { it.recurrenceId!!.originalDateTime.date }
    val changedDates = RecurrenceEngine.expandInRange(
      changed,
      emptyList(),
      dt(31),
      MinuteTimeDate(2024, 2, 15, 0, 0),
    ).map { it.recurrenceId!!.originalDateTime.date }

    assertEquals(
      listOf(Date(2024, 1, 31), Date(2024, 2, 2), Date(2024, 2, 5), Date(2024, 2, 7)),
      oldDates,
    )
    assertEquals(
      listOf(Date(2024, 2, 1), Date(2024, 2, 6), Date(2024, 2, 8), Date(2024, 2, 13)),
      changedDates,
    )
  }

  @Test fun movedOccurrencesCanEnterOrLeaveWindowWhileKeepingOriginalIdentity() {
    val schedule = schedule(rule(RecurrenceFrequency.DAILY, count = 2))
    val ids = RecurrenceEngine.expandInRange(schedule, emptyList(), dt(31), MinuteTimeDate(2024, 2, 2, 0, 0))
      .map { requireNotNull(it.recurrenceId) }
    val movedIn = exception(
      schedule,
      ids[1],
      OccurrenceStatus.ACTIVE,
      OccurrencePatch(
        date = FieldPatch.Replace(dt(31).date),
        time = FieldPatch.Replace(OccurrenceTime.TimeRange(9 * 60 + 30, 60, "Asia/Shanghai")),
      ),
    )
    val movedOut = exception(
      schedule,
      ids[0],
      OccurrenceStatus.ACTIVE,
      OccurrencePatch(
        date = FieldPatch.Replace(Date(2024, 2, 3)),
        time = FieldPatch.Replace(OccurrenceTime.TimeRange(9 * 60, 60, "Asia/Shanghai")),
      ),
    )
    val visible = RecurrenceEngine.expandInRange(schedule, listOf(movedIn, movedOut), dt(31), MinuteTimeDate(2024, 2, 1, 0, 0))
    assertEquals(listOf(ids[1]), visible.map { it.recurrenceId })
    assertEquals(dt(31).plusMinutes(30), (visible.single().timing as ScheduleTiming.Timed).start)
  }

  @Test fun identityQueryRejectsForgedIdentityWithoutUsingWindow() {
    val schedule = schedule(rule(RecurrenceFrequency.DAILY, count = 2))
    val identity = RecurrenceEngine.expandInRange(schedule, emptyList(), dt(31), dt(31).plusMinutes(1)).single().recurrenceId!!
    assertEquals(0, RecurrenceEngine.requireGeneratedIdentity(schedule, identity).occurrenceIndex)
    val forged = RecurrenceId(MinuteTimeDate(2024, 2, 20, 9, 0), "Asia/Shanghai", false)
    assertFailsWith<IllegalArgumentException> { RecurrenceEngine.requireGeneratedIdentity(schedule, forged) }
  }

  /** identity 定位不依赖窗口，移动前后均保留原 identity，取消或伪造 identity 不可作为编辑目标。 */
  @Test fun resolveOccurrenceByIdentityHandlesMovesCancellationAndForgery() {
    val schedule = schedule(rule(RecurrenceFrequency.DAILY, count = 3))
    val ids = RecurrenceEngine.expandInRange(
      schedule,
      emptyList(),
      dt(31),
      MinuteTimeDate(2024, 2, 3, 0, 0),
    ).map { requireNotNull(it.recurrenceId) }
    val movedEarlier = exception(
      schedule,
      ids[1],
      OccurrenceStatus.ACTIVE,
      OccurrencePatch(
        date = FieldPatch.Replace(dt(31).minusDays(2).date),
        time = FieldPatch.Replace(OccurrenceTime.TimeRange(9 * 60, 60, "Asia/Shanghai")),
      ),
    )
    val movedLater = exception(
      schedule,
      ids[2],
      OccurrenceStatus.ACTIVE,
      OccurrencePatch(
        date = FieldPatch.Replace(Date(2024, 2, 10)),
        time = FieldPatch.Replace(OccurrenceTime.TimeRange(9 * 60, 60, "Asia/Shanghai")),
      ),
    )
    val unmoved = RecurrenceEngine.resolveOccurrenceByIdentity(schedule, listOf(movedEarlier, movedLater), ids[0])
    val earlier = RecurrenceEngine.resolveOccurrenceByIdentity(schedule, listOf(movedEarlier, movedLater), ids[1])
    val later = RecurrenceEngine.resolveOccurrenceByIdentity(schedule, listOf(movedEarlier, movedLater), ids[2])
    assertEquals(dt(31), (unmoved?.timing as ScheduleTiming.Timed).start)
    assertEquals(ids[1], earlier?.recurrenceId)
    assertEquals(dt(31).minusDays(2), (earlier?.timing as ScheduleTiming.Timed).start)
    assertEquals(ids[2], later?.recurrenceId)
    assertEquals(MinuteTimeDate(2024, 2, 10, 9, 0), (later?.timing as ScheduleTiming.Timed).start)

    assertNull(RecurrenceEngine.resolveOccurrenceByIdentity(schedule, listOf(exception(schedule, ids[0], OccurrenceStatus.CANCELLED)), ids[0]))
    assertFailsWith<IllegalArgumentException> {
      RecurrenceEngine.resolveOccurrenceByIdentity(
        schedule,
        emptyList(),
        RecurrenceId(MinuteTimeDate(2024, 2, 20, 9, 0), "Asia/Shanghai", false),
      )
    }
  }

  @Test fun splitTruncatesOldSeriesStartsNewSeriesAndPartitionsExceptions() {
    val schedule = schedule(rule(RecurrenceFrequency.DAILY, count = 5))
    val all = RecurrenceEngine.expandInRange(schedule, emptyList(), dt(31), MinuteTimeDate(2024, 2, 10, 0, 0))
    val boundary = all[2].recurrenceId!!
    val before = exception(schedule, all[1].recurrenceId!!, OccurrenceStatus.COMPLETED)
    val after = exception(schedule, all[3].recurrenceId!!, OccurrenceStatus.CANCELLED)
    val newId = ScheduleId("018f8e2a-7b4c-7abc-8def-0123456789ac")
    val split = SeriesSplitter.split(schedule, listOf(before, after), boundary, newId)
    assertEquals(2, RecurrenceEngine.expandInRange(split.previousSchedule, split.previousAdjustments, dt(31), MinuteTimeDate(2024, 2, 10, 0, 0)).size)
    assertEquals(2, RecurrenceEngine.expandInRange(split.followingSchedule, split.followingAdjustments, dt(31), MinuteTimeDate(2024, 2, 10, 0, 0)).size)
    assertEquals(after.recurrenceId, split.followingAdjustments.single().recurrenceId)
    assertEquals(newId, split.followingAdjustments.single().scheduleId)
    assertFailsWith<IllegalArgumentException> { SeriesSplitter.split(schedule, emptyList(), all.first().recurrenceId!!, newId) }
    val fake = RecurrenceId(MinuteTimeDate(2024, 2, 20, 9, 0), "Asia/Shanghai", false)
    assertFailsWith<IllegalArgumentException> { SeriesSplitter.split(schedule, emptyList(), fake, newId) }
  }

  /** 拆分前必须拒绝 COUNT/UNTIL 外 identity、错误 scheduleId、非法单体和不兼容 timing patch。 */
  @Test fun splitRejectsForgedOrIncompatibleExceptionsBeforePartitioning() {
    val countSchedule = schedule(rule(RecurrenceFrequency.DAILY, count = 3))
    val generated = RecurrenceEngine.expandInRange(
      countSchedule,
      emptyList(),
      dt(31),
      MinuteTimeDate(2024, 2, 10, 0, 0),
    )
    val boundary = generated[1].recurrenceId!!
    val followingId = ScheduleId("018f8e2a-7b4c-7abc-8def-0123456789ac")
    val outsideCount = exception(
      countSchedule,
      RecurrenceId(MinuteTimeDate(2024, 2, 3, 9, 0), "Asia/Shanghai", false),
      OccurrenceStatus.ACTIVE,
    )
    val outsideUntilSchedule = countSchedule.copy(
      recurrence = countSchedule.recurrence!!.copy(end = RecurrenceEnd.Until(Date(2024, 2, 1))),
    )
    val outsideUntil = exception(
      outsideUntilSchedule,
      RecurrenceId(MinuteTimeDate(2024, 2, 2, 9, 0), "Asia/Shanghai", false),
      OccurrenceStatus.ACTIVE,
    )
    val wrongSchedule = exception(countSchedule, generated[2].recurrenceId!!, OccurrenceStatus.ACTIVE)
      .copy(scheduleId = followingId)
    val invalidStandalone = exception(
      countSchedule,
      generated[2].recurrenceId!!,
      OccurrenceStatus.ACTIVE,
      OccurrencePatch(
        time = FieldPatch.Replace(OccurrenceTime.TimeRange(9 * 60, 60, "Not/AZone")),
      ),
    )
    val invalidClearDate = exception(
      countSchedule,
      generated[2].recurrenceId!!,
      OccurrenceStatus.ACTIVE,
      OccurrencePatch(
        date = FieldPatch.Clear,
      ),
    )

    listOf(outsideCount, wrongSchedule, invalidStandalone, invalidClearDate).forEach { invalid ->
      assertFailsWith<IllegalArgumentException> {
        SeriesSplitter.split(countSchedule, listOf(invalid), boundary, followingId)
      }
    }
    val untilBoundary = RecurrenceEngine.expandInRange(
      outsideUntilSchedule,
      emptyList(),
      dt(31),
      MinuteTimeDate(2024, 2, 3, 0, 0),
    )[1].recurrenceId!!
    assertFailsWith<IllegalArgumentException> {
      SeriesSplitter.split(outsideUntilSchedule, listOf(outsideUntil), untilBoundary, followingId)
    }
  }

  private fun expand(rule: RecurrenceRule, start: MinuteTimeDate = dt(31), end: MinuteTimeDate = MinuteTimeDate(2030, 1, 1, 0, 0), duration: Int = 60, zone: String = "Asia/Shanghai") =
    RecurrenceEngine.expandInRange(schedule(rule, start, duration, zone), emptyList(), start, end)
  private fun assertDates(values: List<ScheduleOccurrence>, vararg dates: String) = assertEquals(dates.toList(), values.map { (it.timing as ScheduleTiming.Timed).start.date.toString() })
  private fun rule(
    freq: RecurrenceFrequency,
    count: Int? = null,
    end: RecurrenceEnd = count?.let { RecurrenceEnd.Count(it) } ?: RecurrenceEnd.Never,
    days: Set<IsoWeekDay> = emptySet(),
    monthDays: Set<Int> = emptySet(),
    months: Set<Int> = emptySet(),
  ) = RecurrenceRule(
    frequency = freq,
    byWeekDays = days,
    byMonthDays = monthDays,
    byMonths = months,
    end = end,
  )
  private fun schedule(rule: RecurrenceRule, start: MinuteTimeDate = dt(31), duration: Int = 60, zone: String = "Asia/Shanghai") = Schedule(ID, 1, "Title", "Body", null, ScheduleTiming.Timed(start, duration, zone), rule, null, ScheduleTodoState.PENDING, NOW, NOW)
  private fun exception(schedule: Schedule, id: RecurrenceId, status: OccurrenceStatus, patch: OccurrencePatch? = null) = ScheduleOccurrenceAdjustment(schedule.id, id, 1, status, patch, NOW, NOW)
  private fun dt(day: Int) = MinuteTimeDate(2024, 1, day, 9, 0)
  private companion object {
    val ID = ScheduleId("018f8e2a-7b4c-7abc-8def-0123456789ab")
    val NOW = Instant.fromEpochMilliseconds(1)
  }
}
