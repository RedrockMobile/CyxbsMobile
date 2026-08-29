package com.cyxbs.pages.schedule.ui.edit

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.data.repository.ScheduleIdGenerators
import com.cyxbs.pages.schedule.domain.model.*
import com.cyxbs.pages.schedule.domain.repository.*
import com.cyxbs.pages.schedule.ui.edit.area.ScheduleTimeBoundary
import com.cyxbs.pages.schedule.ui.edit.area.ScheduleTimeComponent
import com.cyxbs.pages.schedule.ui.edit.area.ScheduleTimeInterval
import com.cyxbs.pages.schedule.ui.edit.area.adjustScheduleTimeInterval
import com.cyxbs.pages.schedule.ui.edit.area.applyExplicitTimeModeSelection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** THIS_ONLY 无改动保存必须保持既有 sparse patch，不得因 parent 演进而误删例外。 */
class ScheduleEditNoOpTest {

  /** 开始小时越过结束小时后优先只抬高结束小时，并保留原结束分钟。 */
  @Test
  fun changingStartHourPreservesEndMinuteBeforeApplyingMinimumDuration() {
    assertEquals(
      ScheduleTimeInterval(startMinuteOfDay = 14 * 60 + 10, endMinuteOfDay = 14 * 60 + 50),
      adjustScheduleTimeInterval(
        startMinuteOfDay = 14 * 60 + 10,
        endMinuteOfDay = 11 * 60 + 50,
        changedBoundary = ScheduleTimeBoundary.START,
        changedComponent = ScheduleTimeComponent.HOUR,
      ),
    )
    assertEquals(
      ScheduleTimeInterval(startMinuteOfDay = 14 * 60 + 50, endMinuteOfDay = 15 * 60 + 20),
      adjustScheduleTimeInterval(
        startMinuteOfDay = 14 * 60 + 50,
        endMinuteOfDay = 11 * 60 + 10,
        changedBoundary = ScheduleTimeBoundary.START,
        changedComponent = ScheduleTimeComponent.HOUR,
      ),
    )
  }

  /** 调整开始分钟后不足 30 分钟时，由结束端向后补足。 */
  @Test
  fun changingStartMinutePushesEndToThirtyMinutesLater() {
    assertEquals(
      ScheduleTimeInterval(startMinuteOfDay = 10 * 60 + 50, endMinuteOfDay = 11 * 60 + 20),
      adjustScheduleTimeInterval(
        startMinuteOfDay = 10 * 60 + 50,
        endMinuteOfDay = 11 * 60 + 10,
        changedBoundary = ScheduleTimeBoundary.START,
        changedComponent = ScheduleTimeComponent.MINUTE,
      ),
    )
  }

  /** 调整结束端时始终保留结束值，并把开始端向前调整到至少相隔 30 分钟。 */
  @Test
  fun changingEndKeepsEndAndPullsStartBackward() {
    assertEquals(
      ScheduleTimeInterval(startMinuteOfDay = 9 * 60 + 45, endMinuteOfDay = 10 * 60 + 15),
      adjustScheduleTimeInterval(
        startMinuteOfDay = 10 * 60 + 45,
        endMinuteOfDay = 10 * 60 + 15,
        changedBoundary = ScheduleTimeBoundary.END,
        changedComponent = ScheduleTimeComponent.HOUR,
      ),
    )
    assertEquals(
      ScheduleTimeInterval(startMinuteOfDay = 10 * 60 + 15, endMinuteOfDay = 10 * 60 + 45),
      adjustScheduleTimeInterval(
        startMinuteOfDay = 10 * 60 + 30,
        endMinuteOfDay = 10 * 60 + 45,
        changedBoundary = ScheduleTimeBoundary.END,
        changedComponent = ScheduleTimeComponent.MINUTE,
      ),
    )
  }

  /** 同日时间段无法跨越午夜，起止两端分别收敛到 23:29—23:59 与 00:00—00:30。 */
  @Test
  fun intervalAdjustmentHandlesDayBoundary() {
    assertEquals(
      ScheduleTimeInterval(startMinuteOfDay = 23 * 60 + 29, endMinuteOfDay = 23 * 60 + 59),
      adjustScheduleTimeInterval(
        startMinuteOfDay = 23 * 60 + 40,
        endMinuteOfDay = 10 * 60,
        changedBoundary = ScheduleTimeBoundary.START,
        changedComponent = ScheduleTimeComponent.HOUR,
      ),
    )
    assertEquals(
      ScheduleTimeInterval(startMinuteOfDay = 0, endMinuteOfDay = 30),
      adjustScheduleTimeInterval(
        startMinuteOfDay = 10 * 60,
        endMinuteOfDay = 10,
        changedBoundary = ScheduleTimeBoundary.END,
        changedComponent = ScheduleTimeComponent.MINUTE,
      ),
    )
  }

  /** 新建草稿的默认值不算用户修改，产生有效输入后才需要未保存确认。 */
  @Test
  fun untouchedCreationDraftBecomesChangedOnlyAfterUserInput() {
    val todo = EditScheduleModelState(origin = null)
    assertFalse(todo.isChanged)

    val affair = EditScheduleModelState(
      origin = null,
      creationKind = ScheduleKind.AFFAIR,
      creationTiming = ScheduleTiming.Timed(
        MinuteTimeDate(2026, 8, 25, 14, 30),
        90,
        "Asia/Shanghai",
      ),
    )
    assertFalse(affair.isChanged)

    affair.title.setTextAndPlaceCursorAtEnd("课表事务")
    assertTrue(affair.isChanged)
  }

  @Test
  fun affairCreationUsesInitialTimingWithoutTodoState() {
    val timing = ScheduleTiming.Timed(
      MinuteTimeDate(2026, 8, 25, 14, 30),
      90,
      "Asia/Shanghai",
    )
    val state = EditScheduleModelState(
      origin = null,
      creationKind = ScheduleKind.AFFAIR,
      creationTiming = timing,
    )
    state.title.setTextAndPlaceCursorAtEnd("课表事务")

    val draft = state.toDraft()
    assertEquals(ScheduleKind.AFFAIR, draft.kind)
    assertEquals(timing, draft.timing)
    assertEquals(null, draft.todoState)
    assertTrue(draft.linkedToCourse)
  }

  /** 从单次详情关联清单时仍更新事务所属系列，不生成伪造的 occurrence 关联字段。 */
  @Test
  fun affairTodoRelationFromThisOnlyDetailUpdatesSeries() = runTest {
    val parent = parentSchedule().copy(
      kind = ScheduleKind.AFFAIR,
      todoState = null,
      linkedToCourse = true,
    )
    val recurrenceId = recurrenceId()
    val repository = RecordingRepository(snapshot(parent))
    val state = EditScheduleModelState(parent, occurrence(parent, recurrenceId))

    state.toggleCourseRelation()
    repository.applyScheduleEdit(state, EditScope.THIS_ONLY, recurrenceId, FakeIds, Clock.System)

    val updated = (repository.commands.single() as ScheduleCommand.Update).schedule
    assertEquals(ScheduleKind.AFFAIR, updated.kind)
    assertEquals(ScheduleTodoState.PENDING, updated.todoState)
    assertTrue(updated.linkedToCourse)
  }

  @Test
  fun unchangedOccurrenceKeepsExistingReplaceAndClearPatchWhenParentNowMatchesProjection() = runTest {
    val parent = parentSchedule()
    val recurrenceId = RecurrenceId(MinuteTimeDate(2026, 7, 8, 9, 0), "Asia/Shanghai", false)
    val occurrenceTiming = ScheduleTiming.Timed(MinuteTimeDate(2026, 7, 8, 9, 0), 60, "Asia/Shanghai")
    val existingPatch = OccurrencePatch(
      timing = FieldPatch.Replace(occurrenceTiming),
      title = FieldPatch.Replace(parent.title),
      description = FieldPatch.Clear,
      categoryId = FieldPatch.Clear,
      reminders = FieldPatch.Replace(parent.reminders),
    )
    val existing = ScheduleOccurrenceException(
      parent.id, recurrenceId, 4, OccurrenceStatus.ACTIVE, existingPatch,
      Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-02T00:00:00Z"),
    )
    val occurrence = ScheduleOccurrence(
      parent.id, recurrenceId, occurrenceTiming, parent.title, "", null,
      parent.reminders, OccurrenceStatus.ACTIVE, true,
    )
    val repository = RecordingRepository(ScheduleSnapshot(
      schedules = listOf(parent), exceptions = listOf(existing), status = ScheduleRepositoryStatus.Ready(0, false),
    ))
    val state = EditScheduleModelState(parent, occurrence)

    assertTrue(!state.isChanged)
    repository.applyScheduleEdit(state, EditScope.THIS_ONLY, recurrenceId, FakeIds, Clock.System)

    assertTrue(repository.commands.isEmpty())
    assertEquals(existingPatch, repository.snapshot.value.exceptions.single().patch)
  }

  @Test
  fun recurrenceOnlyEditRoutesByScopeWithoutCreatingThisOnlyPatch() = runTest {
    suspend fun commandFor(scope: EditScope): ScheduleCommand? {
      val parent = parentSchedule()
      val recurrenceId = recurrenceId()
      val repository = RecordingRepository(snapshot(parent))
      val state = EditScheduleModelState(parent, occurrence(parent, recurrenceId))
      state.recurrence = state.recurrence.copy(interval = 2)

      repository.applyScheduleEdit(state, scope, recurrenceId, FakeIds, Clock.System)
      return repository.commands.singleOrNull()
    }

    val all = commandFor(EditScope.ALL)
    assertEquals(2, (all as ScheduleCommand.Update).schedule.recurrence?.interval)
    val following = commandFor(EditScope.THIS_AND_FOLLOWING)
    assertEquals(2, (following as ScheduleCommand.SplitSeries).followingSchedule.recurrence?.interval)
    assertEquals(null, commandFor(EditScope.THIS_ONLY))
  }

  @Test
  fun partialTitleEditPreservesUntouchedExistingPatchFields() = runTest {
    val parent = parentSchedule().copy(reminders = emptyList())
    val recurrenceId = recurrenceId()
    val occurrenceTiming = occurrenceTiming(recurrenceId)
    val existingPatch = OccurrencePatch(
      timing = FieldPatch.Replace(occurrenceTiming),
      title = FieldPatch.Replace(parent.title),
      description = FieldPatch.Clear,
      categoryId = FieldPatch.Clear,
      reminders = FieldPatch.Replace(emptyList()),
    )
    val existing = exception(parent, recurrenceId, existingPatch)
    val repository = RecordingRepository(snapshot(parent, existing))
    val state = EditScheduleModelState(parent, occurrence(parent, recurrenceId, occurrenceTiming))
    state.title.setTextAndPlaceCursorAtEnd("Changed title")

    repository.applyScheduleEdit(state, EditScope.THIS_ONLY, recurrenceId, FakeIds, Clock.System)

    val patch = (repository.commands.single() as ScheduleCommand.UpsertOccurrenceException).exception.patch!!
    assertEquals(FieldPatch.Replace("Changed title"), patch.title)
    assertEquals(existingPatch.description, patch.description)
    assertEquals(existingPatch.categoryId, patch.categoryId)
    assertEquals(existingPatch.timing, patch.timing)
    assertEquals(existingPatch.reminders, patch.reminders)
  }

  @Test
  fun actuallyEditedPatchFieldIsUpdatedWhileOtherFieldsStayStable() = runTest {
    val parent = parentSchedule()
    val recurrenceId = recurrenceId()
    val existingPatch = OccurrencePatch(
      description = FieldPatch.Replace("Old override"),
      categoryId = FieldPatch.Clear,
    )
    val existing = exception(parent, recurrenceId, existingPatch)
    val repository = RecordingRepository(snapshot(parent, existing))
    val state = EditScheduleModelState(
      parent,
      occurrence(parent, recurrenceId).copy(description = "Old override", categoryId = null),
    )
    state.detail.setTextAndPlaceCursorAtEnd("New override")

    repository.applyScheduleEdit(state, EditScope.THIS_ONLY, recurrenceId, FakeIds, Clock.System)

    val patch = (repository.commands.single() as ScheduleCommand.UpsertOccurrenceException).exception.patch!!
    assertEquals(FieldPatch.Replace("New override"), patch.description)
    assertEquals(FieldPatch.Clear, patch.categoryId)
  }
  @Test
  fun movedOccurrenceUsesParentAnchorAndKeepsWeeklyUntilWhenOnlyTitleChanges() = runTest {
    val until = Date(2026, 7, 15)
    val parent = parentSchedule().copy(recurrence = RecurrenceRule(
      RecurrenceFrequency.WEEKLY,
      byWeekDays = setOf(IsoWeekDay.WEDNESDAY),
      end = RecurrenceEnd.Until(until),
    ))
    val id = recurrenceId()
    val moved = occurrence(parent, id, ScheduleTiming.Timed(
      MinuteTimeDate(2026, 7, 17, 9, 0), 60, "Asia/Shanghai",
    ))
    val repository = RecordingRepository(snapshot(parent))
    val state = EditScheduleModelState(parent, moved)

    assertEquals(Date(2026, 7, 1), state.recurrenceAnchorDate)
    assertEquals(until, (state.toDraft().recurrence?.end as RecurrenceEnd.Until).date)
    state.title.setTextAndPlaceCursorAtEnd("Only title")
    repository.applyScheduleEdit(state, EditScope.ALL, id, FakeIds, Clock.System)

    val update = repository.commands.single() as ScheduleCommand.Update
    assertEquals(RecurrenceEnd.Until(until), update.schedule.recurrence?.end)
  }

  @Test
  fun timingOnlyUnscheduledDoesNotFakeReminderDirtyAndScopesStayValid() = runTest {
    suspend fun apply(scope: EditScope): ScheduleCommand? {
      val parent = parentSchedule().copy(recurrence = null)
      val id = recurrenceId()
      val repository = RecordingRepository(snapshot(parent))
      val state = EditScheduleModelState(parent, occurrence(parent, id))
      state.startTime = ""
      state.endTime = ""
      state.isInterval = false
      assertTrue(!state.isOccurrenceRemindersChanged)
      repository.applyScheduleEdit(state, scope, id, FakeIds, Clock.System)
      return repository.commands.singleOrNull()
    }

    val all = (apply(EditScope.ALL) as ScheduleCommand.Update).schedule
    assertEquals(ScheduleTiming.Unscheduled, all.timing)
    assertTrue(all.reminders.isEmpty())
    // 非重复日程没有可拆分的 occurrence，“此次及以后”防御性等价于整个系列更新。
    val following = (apply(EditScope.THIS_AND_FOLLOWING) as ScheduleCommand.Update).schedule
    assertEquals(ScheduleTiming.Unscheduled, following.timing)
    assertTrue(following.reminders.isEmpty())
    assertFailsWith<IllegalArgumentException> { apply(EditScope.THIS_ONLY) }
  }

  @Test
  fun unscheduledAndAllDayRemainNoOpUntilExplicitTimeAction() {
    val unscheduled = EditScheduleModelState(parentSchedule().copy(
      timing = ScheduleTiming.Unscheduled,
      recurrence = null,
      reminders = emptyList(),
    ))
    val allDayTiming = ScheduleTiming.AllDay(Date(2026, 7, 1))
    val allDay = EditScheduleModelState(parentSchedule().copy(timing = allDayTiming))

    // 构造 state 相当于打开/关闭区域；默认 wheel 值没有经过显式提交，不得改变领域 timing。
    assertEquals(ScheduleTiming.Unscheduled, unscheduled.toDraft().timing)
    assertTrue(!unscheduled.isTimingInputChanged)
    assertEquals(allDayTiming, allDay.toDraft().timing)
    assertTrue(!allDay.isTimingInputChanged)
  }

  @Test
  fun explicitTimeModeSelectionBuildsDeadlineAndTimedDomainValues() {
    val timedState = EditScheduleModelState(parentSchedule().copy(recurrence = null))
    timedState.applyExplicitTimeModeSelection(interval = false, startMinuteOfDay = 9 * 60, endMinuteOfDay = 10 * 60)
    assertTrue(timedState.toDraft().timing is ScheduleTiming.Deadline)

    val deadline = ScheduleTiming.Deadline(MinuteTimeDate(2026, 7, 1, 10, 0), "Asia/Shanghai")
    val deadlineState = EditScheduleModelState(parentSchedule().copy(timing = deadline, recurrence = null))
    deadlineState.applyExplicitTimeModeSelection(interval = true, startMinuteOfDay = 9 * 60, endMinuteOfDay = 10 * 60)
    assertTrue(deadlineState.toDraft().timing is ScheduleTiming.Timed)
  }

  @Test
  fun timeModeSwitchBuildsDeadlineAndTimedDomainValues() {
    val timedParent = parentSchedule().copy(recurrence = null)
    val timedState = EditScheduleModelState(timedParent)
    timedState.isInterval = false
    timedState.startTime = ""
    assertTrue(timedState.toDraft().timing is ScheduleTiming.Deadline)

    val deadline = ScheduleTiming.Deadline(MinuteTimeDate(2026, 7, 1, 10, 0), "Asia/Shanghai")
    val deadlineState = EditScheduleModelState(timedParent.copy(timing = deadline))
    deadlineState.isInterval = true
    deadlineState.startTime = deadlineState.endTime
    assertTrue(deadlineState.toDraft().timing is ScheduleTiming.Timed)
  }
  @Test
  fun movedOccurrenceSummariesUseEffectiveWeeklyMonthlyAndYearlyRules() {
    val movedTiming = ScheduleTiming.Timed(MinuteTimeDate(2026, 8, 14, 9, 0), 60, "Asia/Shanghai")
    val rules = listOf(
      RecurrenceRule(RecurrenceFrequency.WEEKLY, byWeekDays = setOf(IsoWeekDay.WEDNESDAY)),
      RecurrenceRule(RecurrenceFrequency.MONTHLY, byMonthDays = setOf(1)),
      RecurrenceRule(RecurrenceFrequency.YEARLY, byMonthDays = setOf(1), byMonths = setOf(7)),
    )
    rules.forEach { rule ->
      val parent = parentSchedule().copy(recurrence = rule)
      val state = EditScheduleModelState(parent, occurrence(parent, recurrenceId(), movedTiming))
      assertEquals(rule, state.outputRecurrence)
    }
  }

  @Test
  fun yearlyIntervalsRemainExactUntilUserEdits() {
    listOf(60, 101).forEach { interval ->
      val recurrence = RecurrenceRule(
        RecurrenceFrequency.YEARLY,
        interval = interval,
        byMonthDays = setOf(1),
        byMonths = setOf(7),
      )
      val state = EditScheduleModelState(parentSchedule().copy(recurrence = recurrence))
      assertEquals(interval, state.recurrence.interval)
      assertEquals(recurrence, state.outputRecurrence)
      assertTrue(!state.isRecurrenceInputChanged)
    }
  }

  @Test
  fun yearlyMultiMonthRuleAndMovedOccurrenceAreNoOpUntilRecurrenceIsActuallyEdited() = runTest {
    val recurrence = RecurrenceRule(
      RecurrenceFrequency.YEARLY,
      interval = 1,
      byMonthDays = setOf(5, 20),
      byMonths = setOf(3, 9),
      end = RecurrenceEnd.Count(8),
    )
    val parent = parentSchedule().copy(recurrence = recurrence)
    val id = recurrenceId()
    val movedTiming = ScheduleTiming.Timed(MinuteTimeDate(2026, 10, 2, 14, 0), 90, "Asia/Shanghai")
    val repository = RecordingRepository(snapshot(parent))
    val state = EditScheduleModelState(parent, occurrence(parent, id, movedTiming))

    assertEquals(recurrence, state.toDraft().recurrence)
    repository.applyScheduleEdit(state, EditScope.ALL, id, FakeIds, Clock.System)
    assertTrue(repository.commands.isEmpty())

    state.recurrence = state.recurrence.copy(interval = 2)
    repository.applyScheduleEdit(state, EditScope.ALL, id, FakeIds, Clock.System)
    val updated = (repository.commands.single() as ScheduleCommand.Update).schedule.recurrence!!
    assertEquals(2, updated.interval)
    // 用户真正编辑后按当前 UI 支持子集整体替换，不能悄悄残留表单无法表达的多月份/月日组合。
    assertEquals(setOf(7), updated.byMonths)
    assertEquals(setOf(1), updated.byMonthDays)
  }

  @Test
  fun recurrenceOnlySeriesCommandsDoNotPromoteOccurrenceProjectionFields() = runTest {
    suspend fun command(scope: EditScope): ScheduleCommand {
      val parent = parentSchedule()
      val id = recurrenceId()
      val projected = occurrence(parent, id).copy(
        title = "Occurrence title",
        description = "Occurrence description",
        categoryId = CategoryId("occurrence-category"),
        timing = ScheduleTiming.Timed(MinuteTimeDate(2026, 7, 8, 15, 0), 90, "Asia/Shanghai"),
        reminders = emptyList(),
      )
      val repository = RecordingRepository(snapshot(parent))
      val state = EditScheduleModelState(parent, projected)
      state.recurrence = state.recurrence.copy(interval = 2)
      repository.applyScheduleEdit(state, scope, id, FakeIds, Clock.System)
      return repository.commands.single()
    }

    val all = (command(EditScope.ALL) as ScheduleCommand.Update).schedule
    assertSeriesFieldsEqual(parentSchedule(), all)
    assertEquals(2, all.recurrence?.interval)

    val following = (command(EditScope.THIS_AND_FOLLOWING) as ScheduleCommand.SplitSeries).followingSchedule
    // “此次及以后”以当前 occurrence 的有效内容作为新系列基线，已有单次覆盖不会在边界处丢失。
    assertEquals("Occurrence title", following.title)
    assertEquals("Occurrence description", following.description)
    assertEquals(CategoryId("occurrence-category"), following.categoryId)
    assertEquals(
      ScheduleTiming.Timed(MinuteTimeDate(2026, 7, 8, 15, 0), 90, "Asia/Shanghai"),
      following.timing,
    )
    assertTrue(following.reminders.isEmpty())
    assertEquals(2, following.recurrence?.interval)
  }

  @Test
  fun editingWholeSeriesFromMiddleOccurrenceAppliesOnlyRelativeTimingOffset() = runTest {
    val parent = parentSchedule()
    val id = recurrenceId()
    val repository = RecordingRepository(snapshot(parent))
    val state = EditScheduleModelState(parent, occurrence(parent, id))
    state.startTime = "2026年7月9日 10:00"
    state.endTime = "2026年7月9日 11:00"

    repository.applyScheduleEdit(state, EditScope.ALL, id, FakeIds, Clock.System)

    val updated = (repository.commands.single() as ScheduleCommand.Update).schedule
    assertEquals(
      ScheduleTiming.Timed(MinuteTimeDate(2026, 7, 2, 10, 0), 60, "Asia/Shanghai"),
      updated.timing,
    )
  }

  @Test
  fun dstOverlapTimingNoEditKeepsOriginalDuration() {
    val overlapTiming = ScheduleTiming.Timed(
      MinuteTimeDate(2026, 11, 1, 0, 30), 120, "America/New_York",
    )
    val parent = parentSchedule().copy(timing = overlapTiming)
    val id = RecurrenceId(overlapTiming.start, overlapTiming.timeZoneId, false)
    val state = EditScheduleModelState(parent, occurrence(parent, id, overlapTiming))

    assertTrue(!state.isOccurrenceTimingChanged)
    assertEquals(overlapTiming, state.toDraft().timing)
  }

  @Test
  fun occurrenceWhitespaceIsCanonicalNoOp() = runTest {
    val parent = parentSchedule().copy(title = "Canonical", description = "Body")
    val id = recurrenceId()
    val projected = occurrence(parent, id).copy(title = "  Canonical  ", description = "  Body  ")
    val repository = RecordingRepository(snapshot(parent))
    val state = EditScheduleModelState(parent, projected)

    assertTrue(!state.isOccurrenceFieldsChanged)
    repository.applyScheduleEdit(state, EditScope.THIS_ONLY, id, FakeIds, Clock.System)
    assertTrue(repository.commands.isEmpty())
  }
  private fun assertSeriesFieldsEqual(expected: Schedule, actual: Schedule) {
    assertEquals(expected.title, actual.title)
    assertEquals(expected.description, actual.description)
    assertEquals(expected.categoryId, actual.categoryId)
    assertEquals(expected.timing, actual.timing)
    assertEquals(expected.reminders, actual.reminders)
  }

  private fun recurrenceId() =
    RecurrenceId(MinuteTimeDate(2026, 7, 8, 9, 0), "Asia/Shanghai", false)

  private fun occurrenceTiming(recurrenceId: RecurrenceId) =
    ScheduleTiming.Timed(recurrenceId.originalDateTime, 60, "Asia/Shanghai")

  private fun occurrence(
    parent: Schedule,
    recurrenceId: RecurrenceId,
    timing: ScheduleTiming = occurrenceTiming(recurrenceId),
  ) = ScheduleOccurrence(
    parent.id, recurrenceId, timing, parent.title, parent.description, parent.categoryId,
    parent.reminders, OccurrenceStatus.ACTIVE, true,
  )

  private fun exception(parent: Schedule, recurrenceId: RecurrenceId, patch: OccurrencePatch) =
    ScheduleOccurrenceException(
      parent.id, recurrenceId, 4, OccurrenceStatus.ACTIVE, patch,
      Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-02T00:00:00Z"),
    )

  private fun snapshot(parent: Schedule, exception: ScheduleOccurrenceException? = null) = ScheduleSnapshot(
    schedules = listOf(parent),
    exceptions = listOfNotNull(exception),
    status = ScheduleRepositoryStatus.Ready(0, false),
  )

  private fun parentSchedule() = Schedule(
    ScheduleId("0197f000-0000-7000-8000-000000000001"), 2, "Parent now equal", "", null,
    ScheduleTiming.Timed(MinuteTimeDate(2026, 7, 1, 9, 0), 60, "Asia/Shanghai"),
    RecurrenceRule(RecurrenceFrequency.WEEKLY, byWeekDays = setOf(IsoWeekDay.WEDNESDAY)),
    listOf(ScheduleReminder(ReminderId("r1"), 10, ReminderChannel.DEVICE)),
    ScheduleTodoState.PENDING,
    Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-02T00:00:00Z"),
  )

  private object FakeIds : ScheduleIdGenerators {
    override suspend fun scheduleId() = ScheduleId("0197f000-0000-7000-8000-000000000002")
  }

  private class RecordingRepository(initial: ScheduleSnapshot) : ScheduleRepository {
    override val snapshot: StateFlow<ScheduleSnapshot> = MutableStateFlow(initial)
    val commands = mutableListOf<ScheduleCommand>()
    override suspend fun initialize() = Unit
    override suspend fun execute(command: ScheduleCommand): ScheduleSyncResult? {
      commands += command
      return null
    }
  }
}
