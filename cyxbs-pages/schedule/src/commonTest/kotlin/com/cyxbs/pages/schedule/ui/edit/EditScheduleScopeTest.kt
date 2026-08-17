package com.cyxbs.pages.schedule.ui.edit

import com.cyxbs.pages.schedule.domain.model.*
import com.cyxbs.pages.schedule.ui.model.ScheduleDraft
import com.cyxbs.pages.schedule.ui.model.toUpdatedDomain
import com.cyxbs.components.config.time.MinuteTimeDate
import kotlin.test.*
import kotlin.time.Instant

/** Scope and draft tests stay DTO-free after the v2 edit migration. */
class EditScheduleScopeTest {
  @Test fun scopes_are_stable() = assertEquals(
    listOf(EditScope.THIS_ONLY, EditScope.THIS_AND_FOLLOWING, EditScope.ALL), EditScope.entries,
  )
  @Test fun updated_draft_retains_creation_metadata() {
    val created = Instant.parse("2026-01-01T00:00:00Z")
    val origin = Schedule(
      ScheduleId("019b76e0-0000-7000-8000-000000000001"), 7, "old", "", null,
      ScheduleTiming.Deadline(MinuteTimeDate(2026, 7, 7, 10, 0), "Asia/Shanghai"), null,
      emptyList(), ScheduleCompletion.PENDING, created, created,
    )
    val updated = ScheduleDraft(origin.id, title = "new", timing = origin.timing)
      .toUpdatedDomain(origin, Instant.parse("2026-01-02T00:00:00Z"))
    assertEquals(created, updated.createdAt)
    assertEquals(7, updated.revision)
    assertEquals("new", updated.title)
  }
}
