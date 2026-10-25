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
import com.cyxbs.pages.schedule.ui.edit.area.applyExplicitAllDaySelection
import com.cyxbs.pages.schedule.ui.edit.area.applyExplicitDateSelection
import com.cyxbs.pages.schedule.ui.edit.area.adjustScheduleTimeInterval
import com.cyxbs.pages.schedule.ui.edit.area.applyExplicitTimeModeSelection
import com.cyxbs.pages.schedule.ui.edit.area.ScheduleTimeEditMode
import com.cyxbs.pages.schedule.ui.edit.area.shouldApplyScheduleTimeModeSelection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
      ScheduleTimeInterval(startMinuteOfDay = 14 * 60 + 50, endMinuteOfDay = 15 * 60),
      adjustScheduleTimeInterval(
        startMinuteOfDay = 14 * 60 + 50,
        endMinuteOfDay = 11 * 60 + 10,
        changedBoundary = ScheduleTimeBoundary.START,
        changedComponent = ScheduleTimeComponent.HOUR,
      ),
    )
  }

  /** 调整开始分钟后不足 10 分钟时，由结束端向后补足。 */
  @Test
  fun changingStartMinutePushesEndToTenMinutesLater() {
    assertEquals(
      ScheduleTimeInterval(startMinuteOfDay = 10 * 60 + 50, endMinuteOfDay = 11 * 60),
      adjustScheduleTimeInterval(
        startMinuteOfDay = 10 * 60 + 50,
        endMinuteOfDay = 10 * 60 + 55,
        changedBoundary = ScheduleTimeBoundary.START,
        changedComponent = ScheduleTimeComponent.MINUTE,
      ),
    )
  }

  /** 调整结束端时始终保留结束值，并把开始端向前调整到至少相隔 10 分钟。 */
  @Test
  fun changingEndKeepsEndAndPullsStartBackward() {
    assertEquals(
      ScheduleTimeInterval(startMinuteOfDay = 10 * 60 + 5, endMinuteOfDay = 10 * 60 + 15),
      adjustScheduleTimeInterval(
        startMinuteOfDay = 10 * 60 + 45,
        endMinuteOfDay = 10 * 60 + 15,
        changedBoundary = ScheduleTimeBoundary.END,
        changedComponent = ScheduleTimeComponent.HOUR,
      ),
    )
    assertEquals(
      ScheduleTimeInterval(startMinuteOfDay = 10 * 60 + 35, endMinuteOfDay = 10 * 60 + 45),
      adjustScheduleTimeInterval(
        startMinuteOfDay = 10 * 60 + 40,
        endMinuteOfDay = 10 * 60 + 45,
        changedBoundary = ScheduleTimeBoundary.END,
        changedComponent = ScheduleTimeComponent.MINUTE,
      ),
    )
  }

  /** 同日时间段无法跨越午夜，起止两端分别收敛到 23:49—23:59 与 00:00—00:10。 */
  @Test
  fun intervalAdjustmentHandlesDayBoundary() {
    assertEquals(
      ScheduleTimeInterval(startMinuteOfDay = 23 * 60 + 49, endMinuteOfDay = 23 * 60 + 59),
      adjustScheduleTimeInterval(
        startMinuteOfDay = 23 * 60 + 55,
        endMinuteOfDay = 10 * 60,
        changedBoundary = ScheduleTimeBoundary.START,
        changedComponent = ScheduleTimeComponent.HOUR,
      ),
    )
    assertEquals(
      ScheduleTimeInterval(startMinuteOfDay = 0, endMinuteOfDay = 10),
      adjustScheduleTimeInterval(
        startMinuteOfDay = 10 * 60,
        endMinuteOfDay = 10,
        changedBoundary = ScheduleTimeBoundary.END,
        changedComponent = ScheduleTimeComponent.MINUTE,
      ),
    )
  }

  /** 小于 10 分钟的既有时间段允许展示和保存；只有用户修改时间输入时才执行最短时长收敛。 */
  @Test
  fun existingShortIntervalIsPreservedWithoutTimingInput() {
    val shortTiming = ScheduleTiming.Timed(
      MinuteTimeDate(2026, 9, 6, 12, 0),
      5,
      "Asia/Shanghai",
    )
    val state = EditScheduleModelState(parentSchedule().copy(timing = shortTiming, recurrence = null))

    assertEquals(shortTiming, state.toDraft().timing)
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

  /** 新建事务保存后返回同一个已落库领域对象，课表弹窗才能原位切换到对应详情而不是先关闭。 */
  @Test
  fun affairCreationReturnsCreatedScheduleForDetailTransition() = runTest {
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
    val repository = RecordingRepository(ScheduleSnapshot())

    val created = assertNotNull(
      repository.applyScheduleEdit(state, EditScope.ALL, null, FakeIds, Clock.System),
    )

    assertEquals(ScheduleKind.AFFAIR, created.kind)
    assertEquals(timing, created.timing)
    assertEquals(created, (repository.commands.single() as ScheduleCommand.Create).schedule)
  }

  /** 0 是正式的准时提醒值，从既有日程进入编辑再保存时不能被默认值或空提醒覆盖。 */
  @Test
  fun exactReminderSurvivesEditDraftRoundTrip() {
    val origin = parentSchedule().copy(
      recurrence = null,
      reminder = ScheduleReminder(0),
    )
    val state = EditScheduleModelState(origin)

    assertEquals(0, state.remindMinutes)
    assertEquals(0, state.toDraft().reminder?.offsetMinutes)
  }

  /** 新建编辑器用负数区分“不提醒”，领域草稿只能得到 null，不能把默认态上传成准时提醒。 */
  @Test
  fun newDraftDefaultsToNoReminderInsteadOfExactReminder() {
    val state = EditScheduleModelState(
      origin = null,
      creationTiming = ScheduleTiming.Deadline(
        MinuteTimeDate(2026, 8, 25, 14, 30),
        "Asia/Shanghai",
      ),
    )
    state.title.setTextAndPlaceCursorAtEnd("默认不提醒")

    assertEquals(-1, state.remindMinutes)
    assertNull(state.toDraft().reminder)
  }

  /** 有时间的日程应分别把准时、提前和不提醒映射为 0、正分钟数和空 reminder。 */
  @Test
  fun timedDraftMapsAllReminderChoicesWithoutConflatingZero() {
    val state = EditScheduleModelState(parentSchedule().copy(recurrence = null, reminder = null))

    state.remindMinutes = 0
    assertEquals(0, state.toDraft().reminder?.offsetMinutes)

    state.remindMinutes = 10
    assertEquals(10, state.toDraft().reminder?.offsetMinutes)

    state.remindMinutes = -1
    assertNull(state.toDraft().reminder)
  }

  /** 未排期没有提醒触发点或课表位置，草稿保存边界必须统一清理这两个暂存选择。 */
  @Test
  fun unscheduledDraftClearsReminderAndCourseRelation() {
    val state = EditScheduleModelState(origin = null)
    state.title.setTextAndPlaceCursorAtEnd("稍后安排")
    state.remindMinutes = 0
    state.linkedToCourse = true

    val draft = state.toDraft()
    assertEquals(ScheduleTiming.Unscheduled, draft.timing)
    assertNull(draft.reminder)
    assertFalse(draft.linkedToCourse)
  }

  /** 已有重复日程改成无时间时，提醒、重复规则和课表关联必须在同一个保存草稿中一起清理。 */
  @Test
  fun changingExistingScheduleToUnscheduledClearsAllTimeDerivedFields() {
    val state = EditScheduleModelState(parentSchedule())
    state.startTime = ""
    state.endTime = ""
    state.isInterval = false
    state.linkedToCourse = true

    val draft = state.toDraft()
    assertEquals(ScheduleTiming.Unscheduled, draft.timing)
    assertNull(draft.recurrence)
    assertNull(draft.reminder)
    assertFalse(draft.linkedToCourse)
  }

  /** 从重复实例改为无时间时，整个系列和此次及以后都必须清理系列级派生字段，且不能尝试重算时间偏移。 */
  @Test
  fun recurringOccurrenceToUnscheduledClearsDerivedFieldsForSeriesScopes() = runTest {
    suspend fun commandFor(scope: EditScope): ScheduleCommand {
      val parent = parentSchedule().copy(linkedToCourse = true)
      val id = recurrenceId()
      val repository = RecordingRepository(snapshot(parent))
      val state = EditScheduleModelState(parent, occurrence(parent, id))
      state.startTime = ""
      state.endTime = ""
      state.isInterval = false

      repository.applyScheduleEdit(state, scope, id, FakeIds, Clock.System)
      return repository.commands.single()
    }

    val all = (commandFor(EditScope.ALL) as ScheduleCommand.Update).schedule
    assertEquals(ScheduleTiming.Unscheduled, all.timing)
    assertNull(all.recurrence)
    assertNull(all.reminder)
    assertFalse(all.linkedToCourse)

    val following = (commandFor(EditScope.THIS_AND_FOLLOWING) as ScheduleCommand.SplitSeries)
      .followingSchedule
    assertEquals(ScheduleTiming.Unscheduled, following.timing)
    assertNull(following.recurrence)
    assertNull(following.reminder)
    assertFalse(following.linkedToCourse)
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
      date = FieldPatch.Replace(Date(2026, 7, 8)),
      time = FieldPatch.Replace(OccurrenceTime.TimeRange(9 * 60, 60, "Asia/Shanghai")),
      title = FieldPatch.Replace(parent.title),
      description = FieldPatch.Clear,
      categoryId = FieldPatch.Clear,
      reminder = FieldPatch.Replace(requireNotNull(parent.reminder)),
    )
    val existing = ScheduleOccurrenceAdjustment(
      parent.id, recurrenceId, 4, OccurrenceStatus.ACTIVE, existingPatch,
      Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-02T00:00:00Z"),
    )
    val occurrence = ScheduleOccurrence(
      parent.id, recurrenceId, occurrenceTiming, parent.title, "", null,
      parent.reminder, OccurrenceStatus.ACTIVE, true,
    )
    val repository = RecordingRepository(ScheduleSnapshot(
      schedules = listOf(parent), occurrenceAdjustments = listOf(existing), status = ScheduleRepositoryStatus.Ready(0, false),
    ))
    val state = EditScheduleModelState(parent, occurrence)

    assertTrue(!state.isChanged)
    repository.applyScheduleEdit(state, EditScope.THIS_ONLY, recurrenceId, FakeIds, Clock.System)

    assertTrue(repository.commands.isEmpty())
    assertEquals(existingPatch, repository.snapshot.value.occurrenceAdjustments.single().patch)
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
    val parent = parentSchedule().copy(reminder = null)
    val recurrenceId = recurrenceId()
    val occurrenceTiming = occurrenceTiming(recurrenceId)
    val existingPatch = OccurrencePatch(
      date = FieldPatch.Replace(recurrenceId.originalDateTime.date),
      time = FieldPatch.Replace(OccurrenceTime.TimeRange(9 * 60, 60, "Asia/Shanghai")),
      title = FieldPatch.Replace(parent.title),
      description = FieldPatch.Clear,
      categoryId = FieldPatch.Clear,
      reminder = FieldPatch.Clear,
    )
    val existing = exception(parent, recurrenceId, existingPatch)
    val repository = RecordingRepository(snapshot(parent, existing))
    val state = EditScheduleModelState(parent, occurrence(parent, recurrenceId, occurrenceTiming))
    state.title.setTextAndPlaceCursorAtEnd("Changed title")

    repository.applyScheduleEdit(state, EditScope.THIS_ONLY, recurrenceId, FakeIds, Clock.System)

    val patch = (repository.commands.single() as ScheduleCommand.UpsertOccurrenceAdjustment).adjustment.patch!!
    assertEquals(FieldPatch.Replace("Changed title"), patch.title)
    assertEquals(existingPatch.description, patch.description)
    assertEquals(existingPatch.categoryId, patch.categoryId)
    assertEquals(existingPatch.date, patch.date)
    assertEquals(existingPatch.time, patch.time)
    assertEquals(existingPatch.reminder, patch.reminder)
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

    val patch = (repository.commands.single() as ScheduleCommand.UpsertOccurrenceAdjustment).adjustment.patch!!
    assertEquals(FieldPatch.Replace("New override"), patch.description)
    assertEquals(FieldPatch.Clear, patch.categoryId)
  }

  /** 只改单次日期时保留既有时间覆盖，避免日期编辑把自定义时分和时长重置为系列值。 */
  @Test
  fun editingOnlyOccurrenceDatePreservesExistingTimePatch() = runTest {
    val parent = parentSchedule()
    val id = recurrenceId()
    val existingPatch = OccurrencePatch(
      time = FieldPatch.Replace(
        OccurrenceTime.TimeRange(15 * 60, 90, "Asia/Shanghai"),
      ),
    )
    val existing = exception(parent, id, existingPatch)
    val projectedTiming = ScheduleTiming.Timed(
      MinuteTimeDate(2026, 7, 8, 15, 0), 90, "Asia/Shanghai",
    )
    val repository = RecordingRepository(snapshot(parent, existing))
    val state = EditScheduleModelState(parent, occurrence(parent, id, projectedTiming))
    state.applyExplicitDateSelection(Date(2026, 7, 10))

    repository.applyScheduleEdit(state, EditScope.THIS_ONLY, id, FakeIds, Clock.System)

    val saved = assertIs<ScheduleCommand.UpsertOccurrenceAdjustment>(repository.commands.single()).adjustment
    assertEquals(FieldPatch.Replace(Date(2026, 7, 10)), saved.patch?.date)
    assertEquals(existingPatch.time, saved.patch?.time)
  }

  /** 只改单次时分时保留既有日期覆盖，避免时间编辑使发生日期重新跟随系列。 */
  @Test
  fun editingOnlyOccurrenceTimePreservesExistingDatePatch() = runTest {
    val parent = parentSchedule()
    val id = recurrenceId()
    val existingPatch = OccurrencePatch(
      date = FieldPatch.Replace(Date(2026, 7, 10)),
    )
    val existing = exception(parent, id, existingPatch)
    val projectedTiming = ScheduleTiming.Timed(
      MinuteTimeDate(2026, 7, 10, 9, 0), 60, "Asia/Shanghai",
    )
    val repository = RecordingRepository(snapshot(parent, existing))
    val state = EditScheduleModelState(parent, occurrence(parent, id, projectedTiming))
    state.startTime = "2026年7月10日 11:00"
    state.endTime = "2026年7月10日 12:00"

    repository.applyScheduleEdit(state, EditScope.THIS_ONLY, id, FakeIds, Clock.System)

    val saved = assertIs<ScheduleCommand.UpsertOccurrenceAdjustment>(repository.commands.single()).adjustment
    assertEquals(existingPatch.date, saved.patch?.date)
    assertEquals(
      FieldPatch.Replace(OccurrenceTime.TimeRange(11 * 60, 60, "Asia/Shanghai")),
      saved.patch?.time,
    )
  }

  /** 仅此次编辑必须只写用户实际改变的原子字段，避免修改一项时把其他实例字段固化成覆盖值。 */
  @Test
  fun thisOnlyEditsKeepDateTimeAndContentFieldsIndependent() = runTest {
    val parent = parentSchedule()
    val id = recurrenceId()

    suspend fun patchAfter(edit: EditScheduleModelState.() -> Unit): OccurrencePatch {
      val repository = RecordingRepository(snapshot(parent))
      val state = EditScheduleModelState(parent, occurrence(parent, id)).apply(edit)
      repository.applyScheduleEdit(state, EditScope.THIS_ONLY, id, FakeIds, Clock.System)
      return assertIs<ScheduleCommand.UpsertOccurrenceAdjustment>(repository.commands.single())
        .adjustment.patch!!
    }

    assertEquals(
      OccurrencePatch(title = FieldPatch.Replace("仅此次标题")),
      patchAfter { title.setTextAndPlaceCursorAtEnd("仅此次标题") },
    )
    assertEquals(
      OccurrencePatch(description = FieldPatch.Replace("仅此次备注")),
      patchAfter { detail.setTextAndPlaceCursorAtEnd("仅此次备注") },
    )
    val category = CategoryId("0197f000-0000-7000-8000-000000000099")
    assertEquals(
      OccurrencePatch(categoryId = FieldPatch.Replace(category)),
      patchAfter { categoryId = category },
    )
    assertEquals(
      OccurrencePatch(reminder = FieldPatch.Replace(ScheduleReminder(30))),
      patchAfter { remindMinutes = 30 },
    )
    assertEquals(
      OccurrencePatch(date = FieldPatch.Replace(Date(2026, 7, 10))),
      patchAfter { applyExplicitDateSelection(Date(2026, 7, 10)) },
    )
    assertEquals(
      OccurrencePatch(
        time = FieldPatch.Replace(OccurrenceTime.TimeRange(11 * 60, 60, "Asia/Shanghai")),
      ),
      patchAfter {
        startTime = "2026年7月8日 11:00"
        endTime = "2026年7月8日 12:00"
      },
    )
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

  /** 修改日期后首次开启每周重复，应默认选择新日期星期，而不是父日程的旧星期。 */
  @Test
  fun changedDateUsesNewAnchorWhenEnablingWeeklyRecurrence() {
    val state = EditScheduleModelState(parentSchedule().copy(recurrence = null))
    val changedDate = Date(2026, 7, 2)

    state.applyExplicitDateSelection(changedDate)
    state.recurrence = RecurrenceDraft(freq = RepeatFreqOption.WEEKLY)

    assertEquals(changedDate, state.recurrenceAnchorDate)
    assertEquals(setOf(IsoWeekDay.THURSDAY), state.outputRecurrence?.byWeekDays)
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
      assertTrue(!state.isOccurrenceReminderChanged)
      repository.applyScheduleEdit(state, scope, id, FakeIds, Clock.System)
      return repository.commands.singleOrNull()
    }

    val all = (apply(EditScope.ALL) as ScheduleCommand.Update).schedule
    assertEquals(ScheduleTiming.Unscheduled, all.timing)
    assertNull(all.reminder)
    // 非重复日程没有可拆分的 occurrence，“此次及以后”防御性等价于整个系列更新。
    val following = (apply(EditScope.THIS_AND_FOLLOWING) as ScheduleCommand.Update).schedule
    assertEquals(ScheduleTiming.Unscheduled, following.timing)
    assertNull(following.reminder)
    assertFailsWith<IllegalArgumentException> { apply(EditScope.THIS_ONLY) }
  }

  @Test
  fun unscheduledAndAllDayRemainNoOpUntilExplicitTimeAction() {
    val unscheduled = EditScheduleModelState(parentSchedule().copy(
      timing = ScheduleTiming.Unscheduled,
      recurrence = null,
      reminder = null,
    ))
    val allDayTiming = ScheduleTiming.AllDay(Date(2026, 7, 1))
    val allDay = EditScheduleModelState(parentSchedule().copy(timing = allDayTiming))

    // 构造 state 相当于打开/关闭区域；默认 wheel 值没有经过显式提交，不得改变领域 timing。
    assertEquals(ScheduleTiming.Unscheduled, unscheduled.toDraft().timing)
    assertTrue(!unscheduled.isTimingInputChanged)
    assertEquals(allDayTiming, allDay.toDraft().timing)
    assertTrue(!allDay.isTimingInputChanged)
  }

  /** 日历真实选日会把无日期清单转为全天日程，候选的默认今天本身不参与状态转换。 */
  @Test
  fun explicitDateSelectionTurnsUnscheduledIntoAllDay() {
    val state = EditScheduleModelState(parentSchedule().copy(
      timing = ScheduleTiming.Unscheduled,
      recurrence = null,
      reminder = null,
    ))

    assertEquals(ScheduleTiming.Unscheduled, state.effectiveTiming)
    state.applyExplicitDateSelection(Date(2026, 7, 6))

    assertEquals(ScheduleTiming.AllDay(Date(2026, 7, 6)), state.effectiveTiming)
  }

  /** 全天按钮保留当前日程日期，并把时间段原子收窄为单日全天。 */
  @Test
  fun explicitAllDaySelectionKeepsCurrentDate() {
    val state = EditScheduleModelState(parentSchedule())
    val originalDate = state.anchorDate

    state.applyExplicitAllDaySelection()

    assertEquals(ScheduleTiming.AllDay(originalDate), state.effectiveTiming)
    assertTrue(state.isAllDay)
    assertTrue(!state.isInterval)
  }

  /** 从全天切回时间点时继续使用原日期，且不残留全天标记。 */
  @Test
  fun explicitTimePointSelectionLeavesAllDayMode() {
    val date = Date(2026, 7, 8)
    val state = EditScheduleModelState(parentSchedule().copy(timing = ScheduleTiming.AllDay(date)))

    state.applyExplicitTimeModeSelection(
      interval = false,
      startMinuteOfDay = 9 * 60,
      endMinuteOfDay = 10 * 60 + 15,
    )

    assertEquals(
      ScheduleTiming.Deadline(MinuteTimeDate(date, 10, 15), TimeZone.currentSystemDefault().id),
      state.effectiveTiming,
    )
    assertTrue(!state.isAllDay)
  }

  /** 迁移得到的无日期清单从 occurrence 入口补充时间时，应直接更新非重复系列而不是计算系列偏移。 */
  @Test
  fun unscheduledOccurrenceCanReceiveItsFirstScheduledTime() = runTest {
    val parent = parentSchedule().copy(
      timing = ScheduleTiming.Unscheduled,
      recurrence = null,
      reminder = null,
      linkedToCourse = false,
    )
    val occurrence = ScheduleOccurrence(
      scheduleId = parent.id,
      recurrenceId = null,
      timing = ScheduleTiming.Unscheduled,
      title = parent.title,
      description = parent.description,
      categoryId = parent.categoryId,
      reminder = null,
      status = OccurrenceStatus.ACTIVE,
      isAdjusted = false,
    )
    val repository = RecordingRepository(snapshot(parent))
    val state = EditScheduleModelState(parent, occurrence).apply {
      isInterval = false
      endTime = "2026年7月5日 10:00"
    }

    repository.applyScheduleEdit(state, EditScope.ALL, null, FakeIds, Clock.System)

    val updated = (repository.commands.single() as ScheduleCommand.Update).schedule
    assertEquals(
      ScheduleTiming.Deadline(MinuteTimeDate(2026, 7, 5, 10, 0), "Asia/Shanghai"),
      updated.timing,
    )
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

  /** 无日期草稿虽默认高亮时间段，首次点击同一按钮仍必须把它转换为可保存的时间段。 */
  @Test
  fun unscheduledDraftAppliesPreselectedIntervalMode() {
    assertTrue(
      shouldApplyScheduleTimeModeSelection(
        timing = ScheduleTiming.Unscheduled,
        currentMode = ScheduleTimeEditMode.INTERVAL,
        selectedMode = ScheduleTimeEditMode.INTERVAL,
      )
    )
  }

  /** 已有时间段重复点击当前模式不会重置用户已经设置的起止时间。 */
  @Test
  fun scheduledDraftIgnoresRepeatedCurrentMode() {
    assertTrue(
      !shouldApplyScheduleTimeModeSelection(
        timing = parentSchedule().timing,
        currentMode = ScheduleTimeEditMode.INTERVAL,
        selectedMode = ScheduleTimeEditMode.INTERVAL,
      )
    )
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
        reminder = null,
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
    assertNull(following.reminder)
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

  /**
   * 删除范围必须映射为三种不同命令：单次只取消 occurrence，后续范围截断父系列，全部才删除父资源。
   * 已完成状态不改变删除语义；取消已有例外时保留 revision，但必须清空旧覆盖，避免还原后复活旧内容。
   */
  @Test
  fun deleteScopesRouteWithoutOverDeletingSeries() = runTest {
    val parent = parentSchedule()
    val id = recurrenceId()
    val existingPatch = OccurrencePatch(title = FieldPatch.Replace("单次标题"))
    val existing = exception(parent, id, existingPatch)

    val thisOnlyRepository = RecordingRepository(snapshot(parent, existing))
    thisOnlyRepository.applyScheduleDelete(parent.id, EditScope.THIS_ONLY, id, Clock.System)
    val cancelled = (thisOnlyRepository.commands.single() as
      ScheduleCommand.UpsertOccurrenceAdjustment).adjustment
    assertEquals(OccurrenceStatus.CANCELLED, cancelled.status)
    assertEquals(existing.revision, cancelled.revision)
    assertNull(cancelled.patch)

    val followingRepository = RecordingRepository(snapshot(parent, existing))
    followingRepository.applyScheduleDelete(
      parent.id,
      EditScope.THIS_AND_FOLLOWING,
      id,
      Clock.System,
    )
    val following = followingRepository.commands.single() as ScheduleCommand.DeleteThisAndFollowing
    assertEquals(id, following.recurrenceId)
    assertEquals(RecurrenceEnd.Until(Date(2026, 7, 1)), following.previousSchedule.recurrence?.end)

    val first = RecurrenceId(MinuteTimeDate(2026, 7, 1, 9, 0), "Asia/Shanghai", false)
    val firstRepository = RecordingRepository(snapshot(parent))
    firstRepository.applyScheduleDelete(
      parent.id,
      EditScope.THIS_AND_FOLLOWING,
      first,
      Clock.System,
    )
    assertEquals(ScheduleCommand.Delete(parent.id), firstRepository.commands.single())

    val completed = parent.copy(recurrence = null, todoState = ScheduleTodoState.COMPLETED)
    val allRepository = RecordingRepository(snapshot(completed))
    allRepository.applyScheduleDelete(completed.id, EditScope.ALL, null, Clock.System)
    assertEquals(ScheduleCommand.Delete(completed.id), allRepository.commands.single())
  }

  /** 删除单次会先清空旧 Patch；该槽恢复后再次编辑只能从系列值生成新 Patch，不能复活删除前内容。 */
  @Test
  fun editingRestoredCancelledOccurrenceDoesNotReviveDeletedPatch() = runTest {
    val parent = parentSchedule()
    val id = recurrenceId()
    val deletedPatch = OccurrencePatch(
      title = FieldPatch.Replace("删除前标题"),
      description = FieldPatch.Replace("删除前备注"),
      reminder = FieldPatch.Replace(ScheduleReminder(45)),
    )
    val deleteRepository = RecordingRepository(snapshot(parent, exception(parent, id, deletedPatch)))

    deleteRepository.applyScheduleDelete(parent.id, EditScope.THIS_ONLY, id, Clock.System)

    val cancelled = assertIs<ScheduleCommand.UpsertOccurrenceAdjustment>(
      deleteRepository.commands.single(),
    ).adjustment
    assertEquals(OccurrenceStatus.CANCELLED, cancelled.status)
    assertNull(cancelled.patch)

    // 用户从调整列表还原后，远端会物理删除 CANCELLED adjustment；再次编辑按“无旧 adjustment”处理。
    val editRepository = RecordingRepository(snapshot(parent))
    val state = EditScheduleModelState(parent, occurrence(parent, id)).apply {
      title.setTextAndPlaceCursorAtEnd("恢复后标题")
    }
    editRepository.applyScheduleEdit(state, EditScope.THIS_ONLY, id, FakeIds, Clock.System)

    val recreated = assertIs<ScheduleCommand.UpsertOccurrenceAdjustment>(
      editRepository.commands.single(),
    ).adjustment
    assertEquals(OccurrenceStatus.ACTIVE, recreated.status)
    assertEquals(
      OccurrencePatch(title = FieldPatch.Replace("恢复后标题")),
      recreated.patch,
    )
  }

  /** 有限系列逐次删除到只剩最后一次时，最后一次直接删除父系列，不再写一条多余的取消例外。 */
  @Test
  fun deletingLastRemainingOccurrenceDeletesFiniteSeries() = runTest {
    val finiteEnds = listOf<RecurrenceEnd>(
      RecurrenceEnd.Count(3),
      RecurrenceEnd.Until(Date(2026, 7, 15)),
    )
    finiteEnds.forEach { end ->
      val parent = parentSchedule().copy(recurrence = RecurrenceRule(
        frequency = RecurrenceFrequency.WEEKLY,
        byWeekDays = setOf(IsoWeekDay.WEDNESDAY),
        end = end,
      ))
      val first = RecurrenceId(MinuteTimeDate(2026, 7, 1, 9, 0), "Asia/Shanghai", false)
      val second = recurrenceId()
      val third = RecurrenceId(MinuteTimeDate(2026, 7, 15, 9, 0), "Asia/Shanghai", false)
      val cancelled = listOf(first, third).map { id ->
        exception(parent, id, OccurrencePatch()).copy(status = OccurrenceStatus.CANCELLED)
      }
      val repository = RecordingRepository(snapshot(parent, cancelled))

      repository.applyScheduleDelete(parent.id, EditScope.THIS_ONLY, second, Clock.System)

      assertEquals(ScheduleCommand.Delete(parent.id), repository.commands.single())
    }
  }

  /** 仍有其他可见实例或系列永不结束时，单次删除继续只写当前实例的取消状态。 */
  @Test
  fun deletingOccurrenceKeepsSeriesWhenAnotherOccurrenceCanRemain() = runTest {
    suspend fun assertCancelled(parent: Schedule) {
      val repository = RecordingRepository(snapshot(parent))
      repository.applyScheduleDelete(parent.id, EditScope.THIS_ONLY, recurrenceId(), Clock.System)
      val command = repository.commands.single() as ScheduleCommand.UpsertOccurrenceAdjustment
      assertEquals(OccurrenceStatus.CANCELLED, command.adjustment.status)
    }

    assertCancelled(parentSchedule().copy(recurrence = RecurrenceRule(
      frequency = RecurrenceFrequency.WEEKLY,
      byWeekDays = setOf(IsoWeekDay.WEDNESDAY),
      end = RecurrenceEnd.Count(3),
    )))
    assertCancelled(parentSchedule())
  }

  /** 还原普通修改或删除会移除调整资源；已完成实例只清内容 Patch，必须保留完成态。 */
  @Test
  fun restoreOccurrenceAdjustmentPreservesIndependentCompletionState() = runTest {
    val parent = parentSchedule()
    val id = recurrenceId()
    val patch = OccurrencePatch(title = FieldPatch.Replace("单次标题"))

    listOf(OccurrenceStatus.ACTIVE, OccurrenceStatus.CANCELLED).forEach { status ->
      val existing = exception(parent, id, patch).copy(status = status)
      val repository = RecordingRepository(snapshot(parent, existing))

      repository.restoreOccurrenceAdjustment(parent.id, id)

      assertEquals(
        ScheduleCommand.DeleteOccurrenceAdjustment(parent.id, id),
        repository.commands.single(),
      )
    }

    val completed = exception(parent, id, patch).copy(status = OccurrenceStatus.COMPLETED)
    val restoreRepository = RecordingRepository(snapshot(parent, completed))
    restoreRepository.restoreOccurrenceAdjustment(parent.id, id)
    val restored = assertIs<ScheduleCommand.UpsertOccurrenceAdjustment>(
      restoreRepository.commands.single(),
    ).adjustment
    assertEquals(OccurrenceStatus.COMPLETED, restored.status)
    assertNull(restored.patch)

    val uncompleteRepository = RecordingRepository(snapshot(parent, restored))
    uncompleteRepository.applyScheduleCompletion(parent.id, id, completed = false, Clock.System)
    assertEquals(
      ScheduleCommand.DeleteOccurrenceAdjustment(parent.id, id),
      uncompleteRepository.commands.single(),
    )

    val alreadyActiveRepository = RecordingRepository(snapshot(parent))
    alreadyActiveRepository.applyScheduleCompletion(parent.id, id, completed = false, Clock.System)
    assertTrue(alreadyActiveRepository.commands.isEmpty())
  }

  /** 没有对应资源时误调用还原必须保持 no-op。 */
  @Test
  fun restoreOccurrenceAdjustmentIgnoresMissingAdjustment() = runTest {
    val parent = parentSchedule()
    val id = recurrenceId()
    val repository = RecordingRepository(snapshot(parent))

    repository.restoreOccurrenceAdjustment(parent.id, id)

    assertTrue(repository.commands.isEmpty())
  }

  /** 编辑已完成实例的标题等内容时，单次 patch 必须保留完成态，不能被默认 ACTIVE 覆盖。 */
  @Test
  fun thisOnlyContentEditPreservesCompletedOccurrenceStatus() = runTest {
    val parent = parentSchedule()
    val id = recurrenceId()
    val completed = exception(parent, id, OccurrencePatch()).copy(
      status = OccurrenceStatus.COMPLETED,
      patch = null,
    )
    val projected = occurrence(parent, id).copy(status = OccurrenceStatus.COMPLETED)
    val repository = RecordingRepository(snapshot(parent, completed))
    val state = EditScheduleModelState(parent, projected)
    state.title.setTextAndPlaceCursorAtEnd("已完成实例的新标题")

    repository.applyScheduleEdit(state, EditScope.THIS_ONLY, id, FakeIds, Clock.System)

    val saved = assertIs<ScheduleCommand.UpsertOccurrenceAdjustment>(repository.commands.single()).adjustment
    assertEquals(OccurrenceStatus.COMPLETED, saved.status)
    assertEquals(FieldPatch.Replace("已完成实例的新标题"), saved.patch?.title)
    assertEquals(completed.revision, saved.revision)
    assertEquals(completed.createdAt, saved.createdAt)
  }

  /** 点击还原只修改编辑草稿，直到用户确认保存才清空对应 occurrence 覆盖。 */
  @Test
  fun occurrenceRestoreIsStagedUntilEditConfirmation() = runTest {
    val parent = parentSchedule()
    val id = recurrenceId()
    val existing = exception(parent, id, OccurrencePatch(title = FieldPatch.Replace("单次标题")))
    val repository = RecordingRepository(snapshot(parent, existing))
    val state = EditScheduleModelState(parent, occurrence(parent, id).copy(title = "单次标题"))

    state.stageOccurrenceRestore(id)

    assertTrue(state.isChanged)
    assertTrue(repository.commands.isEmpty())

    repository.applyScheduleEdit(state, EditScope.THIS_ONLY, id, FakeIds, Clock.System)

    assertEquals(
      ScheduleCommand.DeleteOccurrenceAdjustment(parent.id, id),
      repository.commands.single(),
    )
  }

  /** 仅此次首次选中惰性默认分类时，路由必须把分类创建与单次调整保存合并为一个本地命令。 */
  @Test
  fun thisOnlyEditCreatesMissingDefaultCategoryWithOccurrence() = runTest {
    val parent = parentSchedule()
    val id = recurrenceId()
    val repository = RecordingRepository(snapshot(parent))
    val state = EditScheduleModelState(parent, occurrence(parent, id))
    val category = ScheduleCategory(
      id = CategoryId("019d0000-0000-7000-8000-000000000002"),
      revision = 0,
      name = "生活",
      color = null,
      sortOrder = 1,
    )
    state.categoryId = category.id

    repository.applyScheduleEdit(
      state,
      EditScope.THIS_ONLY,
      id,
      FakeIds,
      Clock.System,
      newCategory = category,
    )

    val command = assertIs<ScheduleCommand.SaveOccurrenceWithNewCategory>(repository.commands.single())
    assertEquals(category, command.category)
    assertEquals(FieldPatch.Replace(category.id), command.adjustment.patch?.categoryId)
  }

  /** 修改日期跨周、跨月或跨年时，只迁移 timing 日期，标题、备注、分类、提醒和重复规则保持原值。 */
  @Test
  fun explicitDateChangesPreserveNonTimingFieldsAcrossCalendarBoundaries() {
    val parent = parentSchedule().copy(
      description = "跨日期备注",
      categoryId = CategoryId("category-date-boundary"),
    )
    listOf(
      Date(2026, 7, 8),
      Date(2026, 8, 1),
      Date(2027, 1, 1),
    ).forEach { targetDate ->
      val state = EditScheduleModelState(parent)
      state.applyExplicitDateSelection(targetDate)

      val draft = state.toDraft()
      assertEquals(parent.title, draft.title)
      assertEquals(parent.description, draft.description)
      assertEquals(parent.categoryId, draft.categoryId)
      assertEquals(parent.reminder, draft.reminder)
      assertEquals(parent.recurrence, draft.recurrence)
      assertEquals(
        targetDate,
        (draft.timing as ScheduleTiming.Timed).start.date,
      )
    }
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
    assertEquals(expected.reminder, actual.reminder)
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
    parent.reminder, OccurrenceStatus.ACTIVE, true,
  )

  private fun exception(parent: Schedule, recurrenceId: RecurrenceId, patch: OccurrencePatch) =
    ScheduleOccurrenceAdjustment(
      parent.id, recurrenceId, 4, OccurrenceStatus.ACTIVE, patch,
      Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-02T00:00:00Z"),
    )

  private fun snapshot(parent: Schedule, exception: ScheduleOccurrenceAdjustment? = null) =
    snapshot(parent, listOfNotNull(exception))

  private fun snapshot(parent: Schedule, adjustments: List<ScheduleOccurrenceAdjustment>) = ScheduleSnapshot(
    schedules = listOf(parent),
    occurrenceAdjustments = adjustments,
    status = ScheduleRepositoryStatus.Ready(0, false),
  )

  private fun parentSchedule() = Schedule(
    ScheduleId("0197f000-0000-7000-8000-000000000001"), 2, "Parent now equal", "", null,
    ScheduleTiming.Timed(MinuteTimeDate(2026, 7, 1, 9, 0), 60, "Asia/Shanghai"),
    RecurrenceRule(RecurrenceFrequency.WEEKLY, byWeekDays = setOf(IsoWeekDay.WEDNESDAY)),
    ScheduleReminder(10),
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
