package com.cyxbs.pages.schedule.domain.calendar

import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * EventKit foundation 依赖的 common 合同测试。
 *
 * 该测试不涉及 iOS/Android 平台 API：固定 canonical URI 才是业务身份，平台 identifier 即使变化也只能作为
 * planner 的短期定位缓存；同时验证 Deadline 与零分钟 DEVICE reminder 的既有投影语义没有被 iOS 基础层改写。
 */
class IosEventKitFoundationCommonContractTest {
  @Test
  fun canonicalUriAndFingerprintRemainBusinessIdentityWhenOpaqueCacheChanges() {
    val id = CalendarProjectionId(SCOPE, SCHEDULE_ID, CalendarProjectionKind.DEADLINE)
    val uri = CalendarProjectionUriCodec.encode(id)
    val timing = CalendarTiming.Deadline(MinuteTimeDate(2026, 7, 12, 18, 0), "Asia/Shanghai")
    val projection = CalendarEventProjection(
      id = id,
      externalUri = uri,
      title = "EventKit common contract",
      description = "deadline",
      timing = timing,
      recurrenceRule = null,
      deviceReminderMinutes = listOf(0, 15),
      fingerprint = CalendarProjectionFingerprint.compute(
        externalUri = uri,
        title = "EventKit common contract",
        description = "deadline",
        timing = timing,
        recurrenceRule = null,
        reminderMinutes = listOf(0, 15),
      ),
    )
    val managed = ManagedCalendarEvent(
      id = id,
      fingerprint = projection.fingerprint,
      platformEventRef = PlatformCalendarEventRef("eventkit-opaque-cache-after-refetch"),
    )

    val plan = CalendarExportPlanner.plan(
      result = ScheduleCalendarProjectionResult(
        events = listOf(projection),
        unsupported = emptyList()
      ),
      managedEvents = listOf(managed),
      scope = SCOPE,
    )

    assertEquals(id, CalendarProjectionUriCodec.decodeOrNull(uri))
    assertEquals(
      CalendarTiming.Deadline(MinuteTimeDate(2026, 7, 12, 18, 0), "Asia/Shanghai"),
      CanonicalCalendarFields(
        title = projection.title,
        description = projection.description,
        timing = projection.timing,
        recurrenceRule = projection.recurrenceRule,
        deviceReminderMinutes = projection.deviceReminderMinutes,
      ).timing,
    )
    assertIs<CalendarExportAction.NoOp>(plan.actions.single())
    assertEquals(
      managed.platformEventRef,
      (plan.actions.single() as CalendarExportAction.NoOp).event.platformEventRef
    )
  }

  private companion object {
    val SCOPE = CalendarExportScope("ios_eventkit_foundation")
    val SCHEDULE_ID = ScheduleId("018f7d5a-1234-7abc-8def-1234567890ab")
  }
}
