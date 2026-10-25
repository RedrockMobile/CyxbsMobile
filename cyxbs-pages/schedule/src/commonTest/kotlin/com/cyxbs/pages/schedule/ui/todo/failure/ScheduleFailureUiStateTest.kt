package com.cyxbs.pages.schedule.ui.todo.failure

import com.cyxbs.pages.schedule.data.repository.testScheduleResource
import com.cyxbs.pages.schedule.data.repository.toWire
import com.cyxbs.pages.schedule.domain.sync.TimingInput
import com.cyxbs.pages.schedule.domain.sync.TimingKind
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

/** 失败记录卡片业务摘要的纯映射测试。 */
class ScheduleFailureUiStateTest {

  /** 全天协议只携带 date，摘要不能误读仅属于时间段的 startAt。 */
  @Test
  fun allDaySummaryReadsDateField() {
    val input = testScheduleResource(
      timing = TimingInput(TimingKind.ALL_DAY, date = 1_777_593_600_000L),
    ).toWire { null }

    assertEquals("2026-05-01 00:00 全天", input.timeSummary(TimeZone.UTC))
  }
}
