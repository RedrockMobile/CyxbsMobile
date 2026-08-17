package com.cyxbs.pages.schedule.ui.model

import com.cyxbs.pages.schedule.domain.model.ScheduleId
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

  private companion object { const val ID = "0197f000-0000-7000-8000-000000000001" }
}
