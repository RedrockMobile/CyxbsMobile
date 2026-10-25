package com.cyxbs.pages.schedule.ui.model

import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.model.ScheduleKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class ScheduleDraftTest {
  @Test
  fun validatorReportsBlankTitleAndMappingTrimsValidTitle() {
    val now = Instant.parse("2026-07-01T00:00:00Z")
    assertTrue(ScheduleDraft(ScheduleId(ID)).validate(now).any { it.field == "title" })
    assertEquals("Title", ScheduleDraft(ScheduleId(ID), title = "  Title  ").toNewDomain(now).title)
  }

  @Test
  fun mappingPreservesAffairIdentityAndCourseProjectionIntent() {
    val now = Instant.parse("2026-07-01T00:00:00Z")
    val schedule = ScheduleDraft(
      id = ScheduleId(ID),
      title = "事务",
      todoState = null,
      kind = ScheduleKind.AFFAIR,
      linkedToCourse = true,
    ).toNewDomain(now)

    assertEquals(ScheduleKind.AFFAIR, schedule.kind)
    assertEquals(null, schedule.todoState)
    assertTrue(schedule.linkedToCourse)
  }

  private companion object { const val ID = "0197f000-0000-7000-8000-000000000001" }
}
