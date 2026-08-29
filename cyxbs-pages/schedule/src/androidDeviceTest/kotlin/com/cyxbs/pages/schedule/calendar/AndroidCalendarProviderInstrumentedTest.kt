package com.cyxbs.pages.schedule.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.components.config.time.toLocalDate
import com.cyxbs.pages.schedule.domain.calendar.AndroidManagedCalendarIdentifierCodec
import com.cyxbs.pages.schedule.domain.calendar.CalendarCanonicalBaselineMapper
import com.cyxbs.pages.schedule.domain.calendar.CalendarEventProjection
import com.cyxbs.pages.schedule.domain.calendar.CalendarExportScope
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionFingerprint
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionId
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionKind
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionUriCodec
import com.cyxbs.pages.schedule.domain.calendar.CalendarProviderTimingCanonicalizer
import com.cyxbs.pages.schedule.domain.calendar.CalendarRecurrenceCanonicalizer
import com.cyxbs.pages.schedule.domain.calendar.CalendarTiming
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Calendar Provider 真机契约测试。
 *
 * 每个测试使用随机账号创建独立的 LOCAL 日历，并在 `finally` 与 [tearDown] 中双重清理。清理始终使用
 * 完整账号身份，不按前缀扫描，避免触碰设备上的真实账号或其他日历。
 */
@RunWith(AndroidJUnit4::class)
class AndroidCalendarProviderInstrumentedTest {
  @get:Rule
  val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
    Manifest.permission.READ_CALENDAR,
    Manifest.permission.WRITE_CALENDAR,
  )

  private lateinit var context: Context
  private lateinit var registry: AndroidManagedCalendarRegistry
  private lateinit var gateway: AndroidScheduleCalendarGateway
  private lateinit var accountId: String
  private var scope: CalendarExportScope? = null

  /** 为当前测试创建不可与真实学号碰撞的随机账号身份。 */
  @Before
  fun setUp() {
    context = InstrumentationRegistry.getInstrumentation().targetContext
    checkNotNull(context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)).close()
    val suffix = UUID.randomUUID().toString()
    accountId = "cyxbs-schedule-instrumentation-$suffix"
    scope = CalendarExportScope("instrumentation_${suffix.replace("-", "")}")
    registry = AndroidManagedCalendarRegistry(context)
    gateway = AndroidScheduleCalendarGateway(context, registry, accountId)
  }

  /** 测试失败时仍按完整随机账号身份回收日历，避免污染后续测试和用户设备。 */
  @After
  fun tearDown() {
    if (::registry.isInitialized && ::accountId.isInitialized) {
      runCatching { registry.clearAndDeleteManagedCalendars(accountId) }
    }
  }

  /** 验证受管 Calendar row 使用严格本地身份，并持久化 ownership token 与当前投射版本。 */
  @Test
  fun registryCreatesAndFindsStrictLocalIdentity() = withTestCalendar {
    val firstId = requireNotNull(registry.getOrCreateManagedCalendar(accountId, requiredScope))
    val secondId = requireNotNull(registry.getOrCreateManagedCalendar(accountId, requiredScope))
    assertEquals(firstId, secondId)
    assertEquals(firstId, registry.findCurrentManagedCalendar(accountId))

    val rows = queryExactCalendarRows()
    assertEquals(1, rows.size)
    with(rows.single()) {
      assertEquals(firstId, id)
      assertEquals(accountId, accountName)
      assertEquals(CalendarContract.ACCOUNT_TYPE_LOCAL, accountType)
      assertEquals(MANAGED_CALENDAR_NAME, name)
      assertEquals(MANAGED_CALENDAR_NAME, displayName)
      assertEquals(accountId, ownerAccount)
      val decoded = requireNotNull(
        AndroidManagedCalendarIdentifierCodec.decodeOrNull(managedIdentifier()),
      )
      assertEquals(id, decoded.calendarRowId)
      assertEquals(incarnation, decoded.incarnation)
      assertEquals(AndroidManagedCalendarRegistry.CURRENT_PROJECTION_VERSION, projectionVersion)
    }
  }

  /**
   * 验证随机账号尚无 Calendar row 时只读快照返回明确缺失状态，且查询前后不会隐式创建 Provider 资源。
   */
  @Test
  fun gatewaySnapshotReturnsCalendarAbsentWithoutCreatingCalendar() = withTestCalendar {
    val rowsBefore = queryExactCalendarRows()
    assertTrue(rowsBefore.isEmpty())

    val snapshot = gateway.queryManagedCalendarSnapshot(requiredScope)

    assertEquals(AndroidManagedCalendarSnapshot.CalendarAbsent, snapshot)
    assertEquals(rowsBefore.size, queryExactCalendarRows().size)
  }

  /** 空 Schedule ID 集合仍回读完整 row + incarnation 身份，且不会创建第二条 Calendar row。 */
  @Test
  fun gatewaySnapshotWithEmptyScheduleIdsReturnsCalendarIdentityWithoutCreatingAnotherRow() =
    withTestCalendar {
      val calendarRowId =
        requireNotNull(registry.getOrCreateManagedCalendar(accountId, requiredScope))
      val expectedRow = queryExactCalendarRows().single()
      assertEquals(calendarRowId, expectedRow.id)

      val snapshot = gateway.queryManagedCalendarSnapshot(requiredScope, emptySet())
      val present = snapshot.requirePresent()

      assertEquals(expectedRow.managedIdentifier(), present.calendarIdentifier)
      assertTrue(present.events.isEmpty())
      val rows = queryExactCalendarRows()
      assertEquals(1, rows.size)
      assertEquals(expectedRow, rows.single())
    }

  /** 验证 Timed 事件、日历 stable identity、提醒、canonical URI、更新幂等和所有权删除的完整真实 Provider 路径。 */
  @Test
  fun gatewayCreatesUpdatesReadsAndDeletesTimedEvent() = withTestCalendar {
    val original = projection(
      title = "instrumentation-original",
      description = "before-update",
      start = MinuteTimeDate(2026, 7, 12, 9, 30),
      durationMinutes = 90,
      reminders = listOf(0, 15),
    )
    val eventId = requireNotNull(gateway.createEvent(original, requiredScope))

    val rawOriginal = requireNotNull(queryEvent(eventId))
    assertEquals(original.title, rawOriginal.title)
    assertEquals(original.externalUri, rawOriginal.customAppUri)
    assertEquals(context.packageName, rawOriginal.customAppPackage)
    assertEquals(0, rawOriginal.allDay)
    assertEquals("Asia/Shanghai", rawOriginal.timeZone)
    assertEquals(listOf(0, 15), queryReminderMinutes(eventId))

    val snapshot = gateway.queryManagedCalendarSnapshot(requiredScope)
    val presentSnapshot = snapshot.requirePresent()
    val snapshotEvent = presentSnapshot.events.single()
    assertEquals(
      queryExactCalendarRows().single().managedIdentifier(),
      presentSnapshot.calendarIdentifier,
    )
    assertEquals(original.id, snapshotEvent.projectionId)
    assertEquals(AndroidCalendarEventRefCodec.encode(eventId), snapshotEvent.platformEventRef)
    assertEquals(
      CalendarCanonicalBaselineMapper.toCalendarFields(original),
      snapshotEvent.canonicalFields
    )
    assertEquals(original.fingerprint, snapshotEvent.providerFingerprint)

    val managed = gateway.queryManagedEvents(requiredScope)
    assertEquals(1, managed.size)
    assertEquals(snapshotEvent.projectionId, managed.single().id)
    assertEquals(snapshotEvent.platformEventRef, managed.single().platformEventRef)
    assertEquals(snapshotEvent.providerFingerprint, managed.single().fingerprint)

    val updated = original.copy(
      title = "instrumentation-updated",
      description = "after-update",
      timing = CalendarTiming.Timed(MinuteTimeDate(2026, 7, 12, 10, 0), 60, "Asia/Shanghai"),
      deviceReminderMinutes = listOf(5),
      fingerprint = "test-updated-fingerprint",
    )
    assertTrue(
      gateway.updateEvent(
        updated,
        AndroidCalendarEventRefCodec.encode(eventId),
        requiredScope
      )
    )
    assertTrue(
      gateway.updateEvent(
        updated,
        AndroidCalendarEventRefCodec.encode(eventId),
        requiredScope
      )
    )
    assertEquals(1, gateway.queryManagedEvents(requiredScope).size)
    assertEquals(updated.title, requireNotNull(queryEvent(eventId)).title)
    assertEquals(listOf(5), queryReminderMinutes(eventId))

    val updatedManaged = gateway.queryManagedEvents(requiredScope).single()
    assertTrue(gateway.deleteEvent(updatedManaged, requiredScope))
    assertFalse(gateway.deleteEvent(updatedManaged, requiredScope))
    assertTrue(gateway.queryManagedEvents(requiredScope).isEmpty())
  }

  /**
   * 验证 finalized Create 只写 strict preflight 指定的完整 Calendar incarnation。
   *
   * v1、缺失 row 与删除后重建的 replacement 都必须在 Event/Reminder 写前阻断。replacement 的数字 row id 可以
   * 与旧值相同或不同，测试只依赖每次创建都会变化的 `CAL_SYNC1` token，不把某个 Provider 的自增策略当合同。
   */
  @Test
  fun finalizedCreateUsesFixedCalendarAndRejectsMissingOrReplacementRow() = withTestCalendar {
    val projection = projection(
      title = "instrumentation-finalized-create",
      description = "fixed-row-create",
      start = MinuteTimeDate(2026, 7, 18, 8, 30),
      durationMinutes = 60,
      reminders = listOf(10),
    )
    val expectedCalendarId =
      requireNotNull(registry.getOrCreateManagedCalendar(accountId, requiredScope))
    val expectedRow = queryExactCalendarRows().single()
    assertEquals(expectedCalendarId, expectedRow.id)
    val expectedIdentifier = expectedRow.managedIdentifier()

    val invalidFailure = runCatching {
      gateway.createEventInExistingManagedCalendar(
        projection,
        requiredScope,
        "android-calendar-row:v1:$expectedCalendarId",
      )
    }.exceptionOrNull()
    assertTrue(invalidFailure is AndroidScheduleCalendarGateway.FixedCalendarCreateBlockedException)
    assertEquals(
      AndroidScheduleCalendarGateway.FixedCalendarCreateBlockedReason.INVALID_IDENTIFIER,
      (invalidFailure as AndroidScheduleCalendarGateway.FixedCalendarCreateBlockedException).reason,
    )
    assertTrue(gateway.queryManagedEvents(requiredScope).isEmpty())

    val eventId = requireNotNull(
      gateway.createEventInExistingManagedCalendar(
        projection = projection,
        scope = requiredScope,
        expectedCalendarIdentifier = expectedIdentifier,
      ),
    )
    // 复用 helper 同时校验 METHOD_ALERT 与 batch back-reference 写入的 10 分钟提醒。
    assertEquals(listOf(10), queryReminderMinutes(eventId))
    assertEquals(expectedCalendarId, queryEventCalendarId(eventId))
    assertEquals(
      listOf(eventId),
      gateway.queryManagedEvents(requiredScope).map {
        requireNotNull(AndroidCalendarEventRefCodec.decodeOrNull(it.platformEventRef))
      },
    )

    registry.clearAndDeleteManagedCalendars(accountId)
    val absentFailure = runCatching {
      gateway.createEventInExistingManagedCalendar(projection, requiredScope, expectedIdentifier)
    }.exceptionOrNull()
    assertTrue(absentFailure is AndroidScheduleCalendarGateway.FixedCalendarCreateBlockedException)
    assertEquals(
      AndroidScheduleCalendarGateway.FixedCalendarCreateBlockedReason.CALENDAR_ROW_NOT_CURRENT,
      (absentFailure as AndroidScheduleCalendarGateway.FixedCalendarCreateBlockedException).reason,
    )
    assertTrue(queryExactCalendarRows().isEmpty())

    val replacementCalendarId =
      requireNotNull(registry.getOrCreateManagedCalendar(accountId, requiredScope))
    val replacementRow = queryExactCalendarRows().single()
    assertEquals(replacementCalendarId, replacementRow.id)
    assertFalse(expectedIdentifier == replacementRow.managedIdentifier())
    val replacementFailure = runCatching {
      gateway.createEventInExistingManagedCalendar(projection, requiredScope, expectedIdentifier)
    }.exceptionOrNull()
    assertTrue(replacementFailure is AndroidScheduleCalendarGateway.FixedCalendarCreateBlockedException)
    assertEquals(
      AndroidScheduleCalendarGateway.FixedCalendarCreateBlockedReason.CALENDAR_ROW_NOT_CURRENT,
      (replacementFailure as AndroidScheduleCalendarGateway.FixedCalendarCreateBlockedException).reason,
    )
    assertEquals(listOf(replacementRow), queryExactCalendarRows())
    assertTrue(gateway.queryManagedEvents(requiredScope).isEmpty())
  }

  /**
   * 验证 retry 状态回调窗口内 Calendar incarnation 漂移时，finalized Create 必须在 Event/Reminder batch 前阻断。
   *
   * 测试只精确更新本例随机账号受管 row 的 `CAL_SYNC1`；该受控 Calendar 写用于制造竞争，不代表生产自动迁移。
   */
  @Test
  fun finalizedCreateRechecksCalendarIncarnationAfterBeforeInsert() = withTestCalendar {
    val projection = projection(
      title = "instrumentation-finalized-create-callback-drift",
      description = "callback-incarnation-drift",
      start = MinuteTimeDate(2026, 7, 18, 10, 30),
      durationMinutes = 60,
      reminders = listOf(10),
    )
    val calendarId = requireNotNull(registry.getOrCreateManagedCalendar(accountId, requiredScope))
    val expectedRow = queryExactCalendarRows().single()
    assertEquals(calendarId, expectedRow.id)
    val replacementIncarnation = UUID.randomUUID().toString()
    var callbackCalls = 0

    val failure = runCatching {
      gateway.createEventInExistingManagedCalendar(
        projection = projection,
        scope = requiredScope,
        expectedCalendarIdentifier = expectedRow.managedIdentifier(),
        beforeInsert = {
          callbackCalls += 1
          replaceManagedCalendarIncarnation(expectedRow, replacementIncarnation)
        },
      )
    }.exceptionOrNull()

    assertTrue(failure is AndroidScheduleCalendarGateway.FixedCalendarCreateBlockedException)
    assertEquals(
      AndroidScheduleCalendarGateway.FixedCalendarCreateBlockedReason.CALENDAR_ROW_NOT_CURRENT,
      (failure as AndroidScheduleCalendarGateway.FixedCalendarCreateBlockedException).reason,
    )
    assertEquals(1, callbackCalls)
    assertEquals(
      expectedRow.copy(incarnation = replacementIncarnation),
      queryExactCalendarRows().single(),
    )
    assertTrue(gateway.queryManagedEvents(requiredScope).isEmpty())
  }


  /**
   * 验证 finalized Update 只能修改 fresh snapshot 指定的固定 Calendar row。
   *
   * 成功路径保留 event 的 CALENDAR_ID；错误的 expected row、真实 membership 漂移、缺失 row、替换后的同名
   * row、错误 owner 与非 canonical URI 均不得创建日历/事件或替换 reminder。所有外部篡改只精确命中本测试随机
   * 账号创建的 event；辅助日历使用同一随机账号但不同的精确名称和显示名，避免枚举或触碰用户日历。
   */
  @Test
  fun finalizedUpdateUsesFixedCalendarAndRejectsMissingOrDriftedOwnership() = withTestCalendar {
    val original = projection(
      title = "instrumentation-finalized-original",
      description = "fixed-row",
      start = MinuteTimeDate(2026, 7, 18, 9, 30),
      durationMinutes = 60,
      reminders = listOf(5, 15),
    )
    val eventId = requireNotNull(gateway.createEvent(original, requiredScope))
    val calendarId = requireNotNull(registry.findCurrentManagedCalendar(accountId))
    val calendarRow = queryExactCalendarRows().single()
    assertEquals(calendarId, calendarRow.id)
    val calendarIdentifier = calendarRow.managedIdentifier()
    val updated = original.copy(
      title = "instrumentation-finalized-updated",
      description = "fixed-row-updated",
      deviceReminderMinutes = listOf(30),
      fingerprint = "instrumentation-finalized-updated-fingerprint",
    )

    assertTrue(
      gateway.updateExistingManagedEvent(
        updated, AndroidCalendarEventRefCodec.encode(eventId), requiredScope, calendarIdentifier,
      ),
    )
    assertEquals(calendarId, queryEventCalendarId(eventId))
    assertEquals(updated.title, requireNotNull(queryEvent(eventId)).title)
    assertEquals(listOf(30), queryReminderMinutes(eventId))

    // 真实 membership 漂移必须在任何 Provider 写前拒绝：event 留在辅助 row，不能被更新迁回预期受管 row。
    val titleBeforeWrongCalendar = requireNotNull(queryEvent(eventId)).title
    val remindersBeforeWrongCalendar = queryReminderMinutes(eventId)
    val auxiliaryCalendar = createAuxiliaryTestCalendar()
    try {
      updateTestEvent(eventId, ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, auxiliaryCalendar.id)
      })
      assertEquals(auxiliaryCalendar.id, queryEventCalendarId(eventId))

      assertFalse(
        gateway.updateExistingManagedEvent(
          original,
          AndroidCalendarEventRefCodec.encode(eventId),
          requiredScope,
          calendarIdentifier,
        ),
      )
      assertEquals(titleBeforeWrongCalendar, requireNotNull(queryEvent(eventId)).title)
      assertEquals(remindersBeforeWrongCalendar, queryReminderMinutes(eventId))
      assertEquals(auxiliaryCalendar.id, queryEventCalendarId(eventId))
      assertEquals(1, queryExactCalendarRows().size)
      assertEquals(
        auxiliaryCalendar,
        requireNotNull(queryAuxiliaryTestCalendar(auxiliaryCalendar.id))
      )
      assertEquals(
        listOf(eventId),
        queryEventIdsInControlledCalendars(calendarId, auxiliaryCalendar.id)
      )

      // 后续所有权测试需要恢复其各自的 membership 前置；这是受控测试准备，不是 gateway 的迁移结果。
      updateTestEvent(eventId, ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, calendarId)
      })
      assertEquals(calendarId, queryEventCalendarId(eventId))
    } finally {
      deleteAuxiliaryTestCalendar(auxiliaryCalendar)
    }

    // owner 或 URI 漂移时 verifyEventOwnership 必须返回 false，不能先删除旧 reminder 再发现不匹配。
    updateTestEvent(eventId, ContentValues().apply {
      put(CalendarContract.Events.CUSTOM_APP_PACKAGE, "other.package")
    })
    assertFalse(
      gateway.updateExistingManagedEvent(
        original,
        AndroidCalendarEventRefCodec.encode(eventId),
        requiredScope,
        calendarIdentifier
      )
    )
    assertEquals(titleBeforeWrongCalendar, requireNotNull(queryEvent(eventId)).title)
    assertEquals(remindersBeforeWrongCalendar, queryReminderMinutes(eventId))
    updateTestEvent(eventId, ContentValues().apply {
      put(CalendarContract.Events.CUSTOM_APP_PACKAGE, context.packageName)
      put(CalendarContract.Events.CUSTOM_APP_URI, "cyxbs://schedule?v=2&scope=not-canonical")
    })
    assertFalse(
      gateway.updateExistingManagedEvent(
        original,
        AndroidCalendarEventRefCodec.encode(eventId),
        requiredScope,
        calendarIdentifier
      )
    )
    assertEquals(titleBeforeWrongCalendar, requireNotNull(queryEvent(eventId)).title)
    assertEquals(remindersBeforeWrongCalendar, queryReminderMinutes(eventId))

    // Calendar row 缺失或替换均不可触发 legacy get-or-create；旧 stable identifier 不得认领新 row。
    registry.clearAndDeleteManagedCalendars(accountId)
    assertFalse(
      gateway.updateExistingManagedEvent(
        original,
        AndroidCalendarEventRefCodec.encode(eventId),
        requiredScope,
        calendarIdentifier
      )
    )
    assertTrue(queryExactCalendarRows().isEmpty())
    val replacementCalendarId =
      requireNotNull(registry.getOrCreateManagedCalendar(accountId, requiredScope))
    assertFalse(
      gateway.updateExistingManagedEvent(
        original,
        AndroidCalendarEventRefCodec.encode(eventId),
        requiredScope,
        calendarIdentifier
      )
    )
    assertEquals(1, queryExactCalendarRows().size)
    assertEquals(replacementCalendarId, queryExactCalendarRows().single().id)
    assertTrue(gateway.queryManagedEvents(requiredScope).isEmpty())
  }

  /**
   * 验证 update selection 的 expectedCount(1) 在 query→batch 竞争中使 reminder 替换整体回滚。
   *
   * authorization gate 的第十次调用正好位于 updateExistingManagedEvent 发起 applyBatch 前：测试仅把当前随机
   * event 的 URI 改为非 canonical 值，使 batch 内 Events update 的 selection 命中 0 行。若 Provider 未按
   * expectedCount 回滚，紧随其后的 Reminder delete/insert 将篡改原有提醒；断言保留原 title 和 reminders。
   */
  @Test
  fun finalizedUpdateExpectedCountFailureRollsBackReminderReplacement() = withTestCalendar {
    val original = projection(
      title = "instrumentation-expected-count",
      description = "atomic-reminder",
      start = MinuteTimeDate(2026, 7, 19, 9, 30),
      durationMinutes = 60,
      reminders = listOf(5, 15),
    )
    val eventId = requireNotNull(gateway.createEvent(original, requiredScope))
    val calendarId = requireNotNull(registry.findCurrentManagedCalendar(accountId))
    val calendarRow = queryExactCalendarRows().single()
    assertEquals(calendarId, calendarRow.id)
    val beforeTitle = requireNotNull(queryEvent(eventId)).title
    val beforeReminders = queryReminderMinutes(eventId)
    var authorizationCalls = 0
    val failure = runCatching {
      gateway.updateExistingManagedEvent(
        projection = original.copy(
          title = "must-not-commit",
          deviceReminderMinutes = listOf(30),
          fingerprint = "instrumentation-expected-count-update",
        ),
        eventRef = AndroidCalendarEventRefCodec.encode(eventId),
        scope = requiredScope,
        expectedCalendarIdentifier = calendarRow.managedIdentifier(),
        ensureAuthorized = {
          authorizationCalls += 1
          if (authorizationCalls == 10) {
            updateTestEvent(eventId, ContentValues().apply {
              put(
                CalendarContract.Events.CUSTOM_APP_URI,
                "cyxbs://schedule?v=2&scope=changed-before-batch"
              )
            })
          }
        },
      )
    }.exceptionOrNull()

    assertNotNull(failure)
    assertEquals(beforeTitle, requireNotNull(queryEvent(eventId)).title)
    assertEquals(beforeReminders, queryReminderMinutes(eventId))
    assertEquals(calendarId, queryEventCalendarId(eventId))
  }

  /**
   * 验证真实 Provider 对不同投影形状的持久化与回读合同。
   *
   * 所有事件均属于当前随机账号创建的受管日历。断言单次 AllDay 的 UTC 半开区间、Deadline 的零时长 DTEND，
   * 以及重复事件的 DURATION/RRULE 形状；随后经 gateway 回读 fingerprint，证明 Provider 对 RRULE 重排和
   * reminder 重复/顺序不会破坏 canonical 对账。
   */
  @Test
  fun gatewayRoundTripsAllDayDeadlineAndRecurringShapes() = withTestCalendar {
    val allDay = calendarProjection(
      title = "instrumentation-all-day",
      description = "all-day",
      timing = CalendarTiming.AllDay(Date(2026, 7, 12), 2),
      reminders = listOf(0, 5, 15),
    )
    val deadline = calendarProjection(
      title = "instrumentation-deadline",
      description = "deadline",
      timing = CalendarTiming.Deadline(MinuteTimeDate(2026, 7, 13, 18, 0), "Asia/Shanghai"),
    )
    val recurringTimed = calendarProjection(
      title = "instrumentation-recurring-timed",
      description = "recurring-timed",
      timing = CalendarTiming.Timed(MinuteTimeDate(2026, 7, 14, 9, 30), 90, "Asia/Shanghai"),
      recurrenceRule = "FREQ=DAILY;UNTIL=20261231T155959Z",
    )
    val recurringAllDay = calendarProjection(
      title = "instrumentation-recurring-all-day",
      description = "recurring-all-day",
      timing = CalendarTiming.AllDay(Date(2026, 7, 15), 3),
      recurrenceRule = "FREQ=WEEKLY;UNTIL=20261231",
    )

    val allDayEventId = requireNotNull(gateway.createEvent(allDay, requiredScope))
    val deadlineEventId = requireNotNull(gateway.createEvent(deadline, requiredScope))
    val recurringTimedEventId = requireNotNull(gateway.createEvent(recurringTimed, requiredScope))
    val recurringAllDayEventId = requireNotNull(gateway.createEvent(recurringAllDay, requiredScope))

    val allDayRaw = requireNotNull(queryEvent(allDayEventId))
    val allDayStart =
      Date(2026, 7, 12).toLocalDate().atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    assertEquals(1, allDayRaw.allDay)
    assertEquals(TimeZone.UTC.id, allDayRaw.timeZone)
    assertEquals(allDayStart, allDayRaw.dtStart)
    assertEquals(allDayStart + 2 * MILLIS_PER_DAY, allDayRaw.dtEnd)
    assertEquals(null, allDayRaw.duration)
    assertEquals(null, allDayRaw.recurrenceRule)
    // Provider 的提醒行数量并非跨设备稳定契约；只校验 METHOD_ALERT 行中的 canonical 分钟值，主合同由 gateway fingerprint 对账。
    assertEquals(listOf(0, 5, 15), queryReminderMinutes(allDayEventId))

    val deadlineRaw = requireNotNull(queryEvent(deadlineEventId))
    assertEquals(0, deadlineRaw.allDay)
    assertEquals("Asia/Shanghai", deadlineRaw.timeZone)
    assertEquals(deadlineRaw.dtStart, deadlineRaw.dtEnd)
    assertEquals(null, deadlineRaw.duration)

    val recurringTimedRaw = requireNotNull(queryEvent(recurringTimedEventId))
    assertEquals(null, recurringTimedRaw.dtEnd)
    val reconstructedRecurringTimed = CalendarProviderTimingCanonicalizer.reconstructOrNull(
      dtStart = recurringTimedRaw.dtStart,
      dtEnd = recurringTimedRaw.dtEnd,
      duration = recurringTimedRaw.duration,
      timeZoneId = recurringTimedRaw.timeZone,
      allDay = recurringTimedRaw.allDay == 1,
      recurring = true,
      projectionKind = CalendarProjectionKind.SERIES_MASTER,
    )
    assertTrue(reconstructedRecurringTimed is CalendarTiming.Timed)
    assertEquals(90, (reconstructedRecurringTimed as CalendarTiming.Timed).durationMinutes)
    assertEquals(
      recurringTimed.recurrenceRule,
      CalendarRecurrenceCanonicalizer.canonicalizeOrNull(
        requireNotNull(recurringTimedRaw.recurrenceRule),
        allDay = false
      ),
    )

    val recurringAllDayRaw = requireNotNull(queryEvent(recurringAllDayEventId))
    assertEquals(1, recurringAllDayRaw.allDay)
    assertEquals(TimeZone.UTC.id, recurringAllDayRaw.timeZone)
    assertEquals(null, recurringAllDayRaw.dtEnd)
    assertEquals("P3D", recurringAllDayRaw.duration)
    assertEquals(
      recurringAllDay.recurrenceRule,
      CalendarRecurrenceCanonicalizer.canonicalizeOrNull(
        requireNotNull(recurringAllDayRaw.recurrenceRule),
        allDay = true
      ),
    )

    val eventRefsByProjectionId = mapOf(
      allDay.id to AndroidCalendarEventRefCodec.encode(allDayEventId),
      deadline.id to AndroidCalendarEventRefCodec.encode(deadlineEventId),
      recurringTimed.id to AndroidCalendarEventRefCodec.encode(recurringTimedEventId),
      recurringAllDay.id to AndroidCalendarEventRefCodec.encode(recurringAllDayEventId),
    )
    val snapshotById = gateway.queryManagedCalendarSnapshot(requiredScope)
      .requirePresentEvents()
      .associateBy { it.projectionId }
    listOf(allDay, deadline, recurringTimed, recurringAllDay).forEach { projection ->
      val event = requireNotNull(snapshotById[projection.id])
      assertEquals(projection.id, event.projectionId)
      assertEquals(eventRefsByProjectionId[projection.id], event.platformEventRef)
      assertEquals(
        CalendarCanonicalBaselineMapper.toCalendarFields(projection),
        event.canonicalFields
      )
      assertEquals(projection.fingerprint, event.providerFingerprint)
    }

    val managedById = gateway.queryManagedEvents(requiredScope).associateBy { it.id }
    listOf(allDay, deadline, recurringTimed, recurringAllDay).forEach { projection ->
      assertEquals(projection.fingerprint, managedById[projection.id]?.fingerprint)
    }
  }

  /**
   * 验证相同 projection identity 的多条合法 Provider row 会原样保留。
   *
   * 快照 adapter 不负责冲突裁决或去重；后续 planner/link 层需要看到全部平台事实，才能避免静默覆盖。
   */
  @Test
  fun gatewaySnapshotPreservesDuplicateProjectionRows() = withTestCalendar {
    val projection = projection(
      title = "instrumentation-duplicate",
      description = "duplicate",
      start = MinuteTimeDate(2026, 7, 12, 9, 30),
      durationMinutes = 60,
    )
    val firstEventId = requireNotNull(gateway.createEvent(projection, requiredScope))
    val secondEventId = requireNotNull(gateway.createEvent(projection, requiredScope))

    val events = gateway.queryManagedCalendarSnapshot(requiredScope).requirePresentEvents()
    assertEquals(2, events.size)
    assertTrue(events.all { it.projectionId == projection.id })
    assertTrue(events.all {
      it.canonicalFields == CalendarCanonicalBaselineMapper.toCalendarFields(
        projection
      )
    })
    assertTrue(events.all { it.providerFingerprint == projection.fingerprint })
    assertEquals(
      setOf(
        AndroidCalendarEventRefCodec.encode(firstEventId),
        AndroidCalendarEventRefCodec.encode(secondEventId)
      ),
      events.map { it.platformEventRef }.toSet(),
    )
    assertEquals(2, gateway.queryManagedEvents(requiredScope).size)
  }

  /**
   * 验证外部秒/毫秒级修改会被 gateway 安全拒绝。
   *
   * 测试只按本例 gateway Create 返回的固定 event ID 写入；部分 OEM Provider 对 item URI 与非空 selection
   * 的组合兼容性不一致，因此不叠加所有权 selection，不能将非整分钟平台值截断后产生错误的 `NoOp`。
   */
  @Test
  fun gatewayRejectsExternallyMutatedNonCanonicalManagedTiming() = withTestCalendar {
    val projection = projection(
      title = "instrumentation-non-canonical",
      description = "non-canonical",
      start = MinuteTimeDate(2026, 7, 12, 9, 30),
      durationMinutes = 60,
    )
    val eventId = requireNotNull(gateway.createEvent(projection, requiredScope))
    val original = requireNotNull(queryEvent(eventId))
    updateTestEvent(eventId, ContentValues().apply {
      put(CalendarContract.Events.DTSTART, requireNotNull(original.dtStart) + 1)
      put(CalendarContract.Events.DTEND, requireNotNull(original.dtEnd) + 1)
    })

    try {
      gateway.queryManagedCalendarSnapshot(requiredScope)
      throw AssertionError("Gateway must reject a managed event with non-canonical Provider timing")
    } catch (_: AndroidScheduleCalendarGateway.CalendarProviderReadException) {
      // 预期：canonicalizer 拒绝秒/毫秒后只读快照安全失败，而不是把它截断为分钟值或缺失状态。
    }
  }

  /**
   * 验证 RDATE 的非空值与空串都不能作为受管重复事件的额外 occurrence 被静默忽略，且 gateway 更新会恢复
   * 受支持的 RRULE 投影。
   *
   * 外部写入只按当前随机账号日历中 gateway Create 返回的 event ID 精确命中；部分 OEM Provider 不保证 item URI
   * 与非空 selection 的组合可写，因此通过受控 helper 避免依赖该非标准组合，绝不扫描或修改用户日历。
   */
  @Test
  fun gatewayRejectsExternalRDateThenUpdateClearsItAndRestoresRecurringSnapshot() =
    withTestCalendar {
      val recurring = calendarProjection(
        title = "instrumentation-rdate",
        description = "rdate-boundary",
        timing = CalendarTiming.Timed(MinuteTimeDate(2026, 7, 16, 9, 30), 90, "Asia/Shanghai"),
        recurrenceRule = "FREQ=DAILY;UNTIL=20261231T155959Z",
      )
      val eventId = requireNotNull(gateway.createEvent(recurring, requiredScope))
      assertEquals(null, requireNotNull(queryEvent(eventId)).rDate)

      // 第二项故意写入空串，锁定 Provider row 只要 RDATE 非 null 就必须被 gateway 拒绝的边界。
      listOf("20260717T013000Z", "").forEach { rDate ->
        updateTestEvent(eventId, ContentValues().apply {
          put(CalendarContract.Events.RDATE, rDate)
        })
        assertEquals(rDate, requireNotNull(queryEvent(eventId)).rDate)

        try {
          gateway.queryManagedCalendarSnapshot(requiredScope)
          throw AssertionError("Gateway must reject every non-null RDATE, including an empty string")
        } catch (_: AndroidScheduleCalendarGateway.CalendarProviderReadException) {
          // 预期：RDATE 会引入当前投影不支持的 occurrence，快照必须 fail closed 而非忽略它。
        }

        assertTrue(
          gateway.updateEvent(
            recurring,
            AndroidCalendarEventRefCodec.encode(eventId),
            requiredScope
          )
        )
        assertEquals(null, requireNotNull(queryEvent(eventId)).rDate)
      }

      val repairedRaw = requireNotNull(queryEvent(eventId))
      assertEquals(
        recurring.recurrenceRule,
        CalendarRecurrenceCanonicalizer.canonicalizeOrNull(
          requireNotNull(repairedRaw.recurrenceRule),
          allDay = false
        ),
      )
      val repaired =
        gateway.queryManagedCalendarSnapshot(requiredScope).requirePresentEvents().single()
      assertEquals(recurring.id, repaired.projectionId)
      assertEquals(
        CalendarCanonicalBaselineMapper.toCalendarFields(recurring),
        repaired.canonicalFields
      )
      assertEquals(recurring.fingerprint, repaired.providerFingerprint)
    }

  /**
   * 验证显式清理会逐 row 删除同名但 incarnation 不同的受管日历及全部事件，并保持重复执行幂等。
   *
   * duplicate 仍绑定本测试随机 LOCAL 账号；该回归只证明 exact-ID revalidation 不会被 unique-by-name 语义阻断，
   * 不扫描或修改设备上的其他 Calendar row。
   */
  @Test
  fun clearDeletesAllEventsAndCalendarRow() = withTestCalendar(cleanupAfterBody = false) {
    val first = gateway.createEvent(
      projection("instrumentation-first", "first", MinuteTimeDate(2026, 7, 12, 8, 0), 30),
      requiredScope,
    )
    val second = gateway.createEvent(
      projection("instrumentation-second", "second", MinuteTimeDate(2026, 7, 13, 8, 0), 45),
      requiredScope,
    )
    assertNotNull(first)
    assertNotNull(second)
    val primary = queryExactCalendarRows().single()
    val duplicate = createDuplicateManagedTestCalendar(UUID.randomUUID().toString())
    assertTrue(primary.id != duplicate.id)
    assertTrue(primary.incarnation != duplicate.incarnation)
    assertEquals(setOf(primary.id, duplicate.id), queryExactCalendarRows().map { it.id }.toSet())

    val result = registry.clearAndDeleteManagedCalendars(accountId)
    assertTrue(result is AndroidManagedCalendarRegistry.DeleteResult.Deleted)
    result as AndroidManagedCalendarRegistry.DeleteResult.Deleted
    assertEquals(setOf(primary.id, duplicate.id), result.calendarIds.toSet())
    assertEquals(2, result.eventCount)
    assertTrue(queryExactCalendarRows().isEmpty())
    assertEquals(
      AndroidManagedCalendarRegistry.DeleteResult.AlreadyAbsent,
      registry.clearAndDeleteManagedCalendars(accountId)
    )
  }

  /**
   * 验证显式清理在最终 batch 前发现 `CAL_SYNC1` 漂移时，会回滚同批次内已排队的 Event 删除。
   *
   * 第六次 authorization gate 位于完整身份 preflight 后、applyBatch 前；测试只修改本例随机账号的目标 row，
   * 并断言 token 条件失败不会留下“Calendar 未删但 replacement event 已删”的半提交状态。
   */
  @Test
  fun clearRollsBackEventDeletionWhenCalendarIncarnationChangesBeforeBatch() = withTestCalendar {
    val projection = projection(
      title = "instrumentation-clear-incarnation-drift",
      description = "cleanup-batch-rollback",
      start = MinuteTimeDate(2026, 7, 20, 8, 30),
      durationMinutes = 45,
      reminders = listOf(5, 15),
    )
    val eventId = requireNotNull(gateway.createEvent(projection, requiredScope))
    val expectedRow = queryExactCalendarRows().single()
    val replacementIncarnation = UUID.randomUUID().toString()
    val titleBefore = requireNotNull(queryEvent(eventId)).title
    val remindersBefore = queryReminderMinutes(eventId)
    var authorizationCalls = 0

    val failure = runCatching {
      registry.clearAndDeleteManagedCalendars(accountId) {
        authorizationCalls += 1
        if (authorizationCalls == 6) {
          replaceManagedCalendarIncarnation(expectedRow, replacementIncarnation)
        }
      }
    }.exceptionOrNull()

    assertNotNull(failure)
    assertEquals(6, authorizationCalls)
    assertEquals(
      expectedRow.copy(incarnation = replacementIncarnation),
      queryExactCalendarRows().single(),
    )
    assertEquals(expectedRow.id, queryEventCalendarId(eventId))
    assertEquals(titleBefore, requireNotNull(queryEvent(eventId)).title)
    assertEquals(remindersBefore, queryReminderMinutes(eventId))
  }


  /**
   * 提取存在状态的完整快照；测试若意外得到 CalendarAbsent 应立即失败，不能把资源缺失误当成空快照。
   */
  private fun AndroidManagedCalendarSnapshot.requirePresent(): AndroidManagedCalendarSnapshot.Present =
    when (this) {
      AndroidManagedCalendarSnapshot.CalendarAbsent -> throw AssertionError("Managed calendar must be present")
      is AndroidManagedCalendarSnapshot.Present -> this
    }

  /** 提取存在状态中的事件列表，供不需要检查 Calendar row identity 的既有事件断言复用。 */
  private fun AndroidManagedCalendarSnapshot.requirePresentEvents(): List<AndroidManagedCalendarSnapshotEvent> =
    requirePresent().events

  /**
   * 执行测试并保证最终清理。清理前先完整查询身份字段，防止测试代码对异常 Provider 状态做破坏性操作。
   */
  private inline fun withTestCalendar(
    cleanupAfterBody: Boolean = true,
    block: () -> Unit,
  ) {
    try {
      block()
    } finally {
      if (cleanupAfterBody) {
        queryExactCalendarRows().forEach { row ->
          check(row.accountName == accountId)
          check(row.accountType == CalendarContract.ACCOUNT_TYPE_LOCAL)
          check(row.name == MANAGED_CALENDAR_NAME)
          check(row.displayName == MANAGED_CALENDAR_NAME)
          check(row.ownerAccount == accountId)
        }
        registry.clearAndDeleteManagedCalendars(accountId)
        assertTrue(queryExactCalendarRows().isEmpty())
      }
    }
  }

  /** 创建供真实 gateway 写入的规范 Timed 投影。 */
  private fun projection(
    title: String,
    description: String,
    start: MinuteTimeDate,
    durationMinutes: Int,
    reminders: List<Int> = emptyList(),
  ): CalendarEventProjection = calendarProjection(
    title = title,
    description = description,
    timing = CalendarTiming.Timed(start, durationMinutes, "Asia/Shanghai"),
    reminders = reminders,
  )

  /**
   * 创建不同时间和重复形状的测试投影，并生成与 gateway 回读规则相同的 canonical fingerprint。
   *
   * 调用方可以传入重复 reminder 以验证 Provider 原始 row 与 gateway 去重排序回读的边界；生产 projection
   * 已在 common 层 canonicalize，本 helper 仅为真实 Provider 合同构造受控输入。
   */
  private fun calendarProjection(
    title: String,
    description: String,
    timing: CalendarTiming,
    recurrenceRule: String? = null,
    reminders: List<Int> = emptyList(),
  ): CalendarEventProjection {
    val scheduleId = ScheduleId(nextScheduleId())
    // 先识别 Deadline，避免测试夹具按时长推断身份而掩盖生产投影的显式优先级。
    val kind = when {
      timing is CalendarTiming.Deadline -> CalendarProjectionKind.DEADLINE
      recurrenceRule == null -> CalendarProjectionKind.SINGLE
      else -> CalendarProjectionKind.SERIES_MASTER
    }
    val id = CalendarProjectionId(requiredScope, scheduleId, kind)
    val externalUri = CalendarProjectionUriCodec.encode(id)
    return CalendarEventProjection(
      id = id,
      externalUri = externalUri,
      title = title,
      description = description,
      timing = timing,
      recurrenceRule = recurrenceRule,
      deviceReminderMinutes = reminders,
      fingerprint = CalendarProjectionFingerprint.compute(
        externalUri = externalUri,
        title = title,
        description = description,
        timing = timing,
        recurrenceRule = recurrenceRule,
        reminderMinutes = reminders.distinct().sorted(),
      ),
    )
  }


  /**
   * 把真实 Provider row 的 `_ID` 与 `CAL_SYNC1` 组合成完整 v2 身份。
   *
   * helper 只接受本测试精确查询出的受管 row；token 缺失或非 canonical 时立即失败，避免 fixture 自行猜测所有权。
   */
  private fun CalendarRow.managedIdentifier(): String =
    AndroidManagedCalendarIdentifierCodec.encode(
      id,
      requireNotNull(incarnation) { "Managed test calendar must persist CAL_SYNC1 incarnation" },
    )

  /**
   * 精确替换本例受管 Calendar row 的 incarnation，用于制造 preflight 与 Provider batch 之间的受控漂移。
   *
   * selection 同时保留 row、随机账号、LOCAL 类型、固定名称和旧 token；命中数不是 1 时立即失败，避免测试准备
   * 越过自身所有权边界。该 helper 不供生产路径使用，也不对 tokenless row 做 backfill。
   */
  private fun replaceManagedCalendarIncarnation(
    expected: CalendarRow,
    replacementIncarnation: String,
  ) {
    val expectedIncarnation = requireNotNull(expected.incarnation) {
      "Managed test calendar must have an incarnation before controlled replacement"
    }
    // 先经 production codec 校验 replacement 的 canonical UUID 形状，测试不能写入新的畸形 fixture。
    AndroidManagedCalendarIdentifierCodec.encode(expected.id, replacementIncarnation)
    val syncAdapterUri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
      .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
      .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, expected.accountName)
      .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, expected.accountType)
      .build()
    val updated = context.contentResolver.update(
      syncAdapterUri,
      ContentValues().apply {
        put(CalendarContract.Calendars.CAL_SYNC1, replacementIncarnation)
      },
      "${CalendarContract.Calendars._ID} = ? AND " +
          "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND " +
          "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND " +
          "${CalendarContract.Calendars.NAME} = ? AND " +
          "${CalendarContract.Calendars.CAL_SYNC1} = ?",
      arrayOf(
        expected.id.toString(),
        expected.accountName,
        expected.accountType,
        expected.name,
        expectedIncarnation,
      ),
    )
    assertEquals(1, updated)
  }


  /** 使用完整账号身份 selection 查询测试 Calendar row，并复制 `CAL_SYNC1` ownership token。 */
  private fun queryExactCalendarRows(): List<CalendarRow> {
    val projection = arrayOf(
      CalendarContract.Calendars._ID,
      CalendarContract.Calendars.ACCOUNT_NAME,
      CalendarContract.Calendars.ACCOUNT_TYPE,
      CalendarContract.Calendars.NAME,
      CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
      CalendarContract.Calendars.OWNER_ACCOUNT,
      CalendarContract.Calendars.CAL_SYNC1,
      CalendarContract.Calendars.CAL_SYNC2,
    )
    val selection = "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND " +
        "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND ${CalendarContract.Calendars.NAME} = ?"
    return context.contentResolver.query(
      CalendarContract.Calendars.CONTENT_URI,
      projection,
      selection,
      arrayOf(accountId, CalendarContract.ACCOUNT_TYPE_LOCAL, MANAGED_CALENDAR_NAME),
      null,
    )?.use { cursor ->
      buildList {
        while (cursor.moveToNext()) {
          add(
            CalendarRow(
              id = cursor.getLong(0),
              accountName = cursor.getString(1),
              accountType = cursor.getString(2),
              name = cursor.getString(3),
              displayName = cursor.getString(4),
              ownerAccount = cursor.getString(5),
              incarnation = cursor.getString(6),
              projectionVersion = cursor.getString(7),
            ),
          )
        }
      }
    }.orEmpty()
  }

  /**
   * 在本测试随机 LOCAL 账号下创建第二条同名受管 Calendar row，并写入独立 canonical incarnation。
   *
   * 该 helper 只供 exact-ID cleanup 回归；返回前按新 row ID 从受管 identity 查询结果中精确回读，不访问用户日历。
   */
  private fun createDuplicateManagedTestCalendar(incarnation: String): CalendarRow {
    require(UUID.fromString(incarnation).toString() == incarnation) {
      "Duplicate managed test calendar requires a canonical lowercase UUID incarnation"
    }
    val syncAdapterUri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
      .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
      .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountId)
      .appendQueryParameter(
        CalendarContract.Calendars.ACCOUNT_TYPE,
        CalendarContract.ACCOUNT_TYPE_LOCAL,
      )
      .build()
    val rowId = requireNotNull(
      context.contentResolver.insert(
        syncAdapterUri,
        ContentValues().apply {
          put(CalendarContract.Calendars.ACCOUNT_NAME, accountId)
          put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
          put(CalendarContract.Calendars.NAME, MANAGED_CALENDAR_NAME)
          put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, MANAGED_CALENDAR_NAME)
          put(CalendarContract.Calendars.CALENDAR_COLOR, AUXILIARY_CALENDAR_COLOR)
          put(
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.CAL_ACCESS_OWNER,
          )
          put(CalendarContract.Calendars.OWNER_ACCOUNT, accountId)
          put(CalendarContract.Calendars.VISIBLE, 1)
          put(CalendarContract.Calendars.SYNC_EVENTS, 1)
          put(CalendarContract.Calendars.CAL_SYNC1, incarnation)
          put(
            CalendarContract.Calendars.CAL_SYNC2,
            AndroidManagedCalendarRegistry.CURRENT_PROJECTION_VERSION,
          )
        },
      )?.let(ContentUris::parseId),
    )
    check(rowId > 0) { "Duplicate managed test calendar must have a positive row ID" }
    return queryExactCalendarRows().single { row -> row.id == rowId }.also { row ->
      assertEquals(incarnation, row.incarnation)
    }
  }


  /**
   * 创建只供 membership 漂移用的辅助 LOCAL 日历。
   *
   * 该 row 复用当前测试的随机账号，但 `NAME` 和 `CALENDAR_DISPLAY_NAME` 都使用不同的精确随机值；因此它既不
   * 会被受管日历 registry 枚举，也不会与设备用户日历或当前测试的受管 row 重合。
   */
  private fun createAuxiliaryTestCalendar(): CalendarRow {
    val name = "$AUXILIARY_CALENDAR_NAME_PREFIX${UUID.randomUUID()}"
    val displayName = "$name-display"
    val rowId = requireNotNull(
      context.contentResolver.insert(
        auxiliaryCalendarSyncAdapterUri(),
        ContentValues().apply {
          put(CalendarContract.Calendars.ACCOUNT_NAME, accountId)
          put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
          put(CalendarContract.Calendars.NAME, name)
          put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, displayName)
          put(CalendarContract.Calendars.CALENDAR_COLOR, AUXILIARY_CALENDAR_COLOR)
          put(
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.CAL_ACCESS_OWNER
          )
          put(CalendarContract.Calendars.OWNER_ACCOUNT, accountId)
          put(CalendarContract.Calendars.VISIBLE, 1)
          put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        },
      )?.let(ContentUris::parseId),
    )
    check(rowId > 0) { "Auxiliary test calendar must have a positive row ID" }
    return requireNotNull(queryAuxiliaryTestCalendar(rowId))
  }

  /**
   * 用 auxiliary row ID 与完整随机账号身份回读单条辅助日历，禁止前缀扫描或枚举设备日历。
   */
  private fun queryAuxiliaryTestCalendar(calendarId: Long): CalendarRow? =
    context.contentResolver.query(
      CalendarContract.Calendars.CONTENT_URI,
      arrayOf(
        CalendarContract.Calendars._ID,
        CalendarContract.Calendars.ACCOUNT_NAME,
        CalendarContract.Calendars.ACCOUNT_TYPE,
        CalendarContract.Calendars.NAME,
        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
        CalendarContract.Calendars.OWNER_ACCOUNT,
        CalendarContract.Calendars.CAL_SYNC1,
      ),
      "${CalendarContract.Calendars._ID} = ? AND ${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND " +
          "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND ${CalendarContract.Calendars.OWNER_ACCOUNT} = ?",
      arrayOf(calendarId.toString(), accountId, CalendarContract.ACCOUNT_TYPE_LOCAL, accountId),
      null,
    )?.use { cursor ->
      if (!cursor.moveToFirst()) null else {
        val row = CalendarRow(
          id = cursor.getLong(0),
          accountName = cursor.getString(1),
          accountType = cursor.getString(2),
          name = cursor.getString(3),
          displayName = cursor.getString(4),
          ownerAccount = cursor.getString(5),
          incarnation = cursor.getString(6),
        )
        check(!cursor.moveToNext()) { "Auxiliary test calendar identity must be unique" }
        row
      }
    }

  /**
   * 精确删除本测试创建的辅助日历。
   *
   * 删除前后都校验 row ID、随机账号、LOCAL 类型、owner、名称和显示名，防止测试 cleanup 波及任何真实日历。
   * 若事件仍因异常留在此 row，Calendar Provider 会随该受控 row 一并清理，外层 finally 仍只清理受管测试 row。
   */
  private fun deleteAuxiliaryTestCalendar(expected: CalendarRow) {
    assertEquals(expected, requireNotNull(queryAuxiliaryTestCalendar(expected.id)))
    val deleted = context.contentResolver.delete(
      auxiliaryCalendarSyncAdapterUri(),
      "${CalendarContract.Calendars._ID} = ? AND ${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND " +
          "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND ${CalendarContract.Calendars.OWNER_ACCOUNT} = ? AND " +
          "${CalendarContract.Calendars.NAME} = ? AND ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} = ?",
      arrayOf(
        expected.id.toString(), expected.accountName, expected.accountType, expected.ownerAccount,
        expected.name, expected.displayName,
      ),
    )
    assertEquals(1, deleted)
    assertEquals(null, queryAuxiliaryTestCalendar(expected.id))
  }

  /** 将辅助日历写入绑定当前随机 LOCAL 账号的 sync-adapter URI，避免 Provider 解释为其他账号。 */
  private fun auxiliaryCalendarSyncAdapterUri() = CalendarContract.Calendars.CONTENT_URI.buildUpon()
    .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountId)
    .appendQueryParameter(
      CalendarContract.Calendars.ACCOUNT_TYPE,
      CalendarContract.ACCOUNT_TYPE_LOCAL
    )
    .build()

  /**
   * 回读测试 event 的 Calendar membership，验证 finalized Update 不会写入或迁移 CALENDAR_ID。
   */
  private fun queryEventCalendarId(eventId: Long): Long? = context.contentResolver.query(
    CalendarContract.Events.CONTENT_URI,
    arrayOf(CalendarContract.Events.CALENDAR_ID),
    "${CalendarContract.Events._ID} = ?",
    arrayOf(eventId.toString()),
    null,
  )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

  /**
   * 仅枚举当前受管 row 与辅助 row 内的事件 ID，验证 rejected update 没有在受控日历创建额外事件。
   */
  private fun queryEventIdsInControlledCalendars(
    managedCalendarId: Long,
    auxiliaryCalendarId: Long,
  ): List<Long> = context.contentResolver.query(
    CalendarContract.Events.CONTENT_URI,
    arrayOf(CalendarContract.Events._ID),
    "${CalendarContract.Events.CALENDAR_ID} IN (?, ?)",
    arrayOf(managedCalendarId.toString(), auxiliaryCalendarId.toString()),
    null,
  )?.use { cursor ->
    buildList {
      while (cursor.moveToNext()) add(cursor.getLong(0))
    }.sorted()
  }.orEmpty()

  /**
   * 只更新本测试随机账号刚创建的固定 event ID，构造 query→batch 漂移或 ownership 漂移。
   *
   * helper 不扫描、枚举或触碰用户事件；调用者仍需在测试后通过完整随机账号 identity 清理受管日历。
   */
  private fun updateTestEvent(eventId: Long, values: ContentValues) {
    assertEquals(
      1,
      context.contentResolver.update(
        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
        values,
        null,
        null,
      ),
    )
  }

  /**
   * 按本测试创建的 Provider event ID 回读关键字段。
   *
   * 调用点只传入刚由随机账号受管日历创建的 event ID；DTSTART/DTEND/DURATION/RRULE 用于验证 Provider
   * 写入形状，避免把 gateway 的二次 canonicalization 当作 Provider 行为本身。
   */
  private fun queryEvent(eventId: Long): EventRow? {
    val projection = arrayOf(
      CalendarContract.Events.TITLE,
      CalendarContract.Events.CUSTOM_APP_URI,
      CalendarContract.Events.CUSTOM_APP_PACKAGE,
      CalendarContract.Events.ALL_DAY,
      CalendarContract.Events.EVENT_TIMEZONE,
      CalendarContract.Events.DTSTART,
      CalendarContract.Events.DTEND,
      CalendarContract.Events.DURATION,
      CalendarContract.Events.RRULE,
      CalendarContract.Events.RDATE,
    )
    return context.contentResolver.query(
      CalendarContract.Events.CONTENT_URI,
      projection,
      "${CalendarContract.Events._ID} = ?",
      arrayOf(eventId.toString()),
      null,
    )?.use { cursor ->
      if (cursor.moveToFirst()) {
        EventRow(
          title = cursor.getString(0),
          customAppUri = cursor.getString(1),
          customAppPackage = cursor.getString(2),
          allDay = cursor.getInt(3),
          timeZone = cursor.getString(4),
          dtStart = cursor.getLong(5),
          dtEnd = cursor.getLongOrNull(6),
          duration = cursor.getString(7),
          recurrenceRule = cursor.getString(8),
          rDate = cursor.getString(9),
        )
      } else null
    }
  }

  /** Cursor 的 nullable DTEND 与 Kotlin null 对齐，不能把 Provider null 误读为 epoch 0。 */
  private fun android.database.Cursor.getLongOrNull(columnIndex: Int): Long? =
    if (isNull(columnIndex)) null else getLong(columnIndex)

  /** 回读提醒分钟数并排序，验证 batch/back-reference 产生的真实 Reminder rows。 */
  private fun queryReminderMinutes(eventId: Long): List<Int> = context.contentResolver.query(
    CalendarContract.Reminders.CONTENT_URI,
    arrayOf(CalendarContract.Reminders.MINUTES, CalendarContract.Reminders.METHOD),
    "${CalendarContract.Reminders.EVENT_ID} = ?",
    arrayOf(eventId.toString()),
    null,
  )?.use { cursor ->
    buildList {
      while (cursor.moveToNext()) {
        assertEquals(CalendarContract.Reminders.METHOD_ALERT, cursor.getInt(1))
        add(cursor.getInt(0))
      }
    }.sorted()
  }.orEmpty()

  private val requiredScope: CalendarExportScope
    get() = requireNotNull(scope) { "Test calendar scope has not been initialized" }

  /** 生成测试专用的规范 UUIDv7；序号只用于同一测试进程内区分事件。 */
  private fun nextScheduleId(): String {
    scheduleSequence += 1
    return "018f0000-0000-7000-8000-${scheduleSequence.toString().padStart(12, '0')}"
  }

  private data class CalendarRow(
    val id: Long,
    val accountName: String,
    val accountType: String,
    val name: String,
    val displayName: String,
    val ownerAccount: String,
    val incarnation: String?,
    val projectionVersion: String? = null,
  )

  private data class EventRow(
    val title: String,
    val customAppUri: String,
    val customAppPackage: String,
    val allDay: Int,
    val timeZone: String,
    val dtStart: Long,
    val dtEnd: Long?,
    val duration: String?,
    val recurrenceRule: String?,
    val rDate: String?,
  )

  private companion object {
    const val MANAGED_CALENDAR_NAME = "掌邮日程"
    const val AUXILIARY_CALENDAR_NAME_PREFIX = "cyxbs-schedule-auxiliary-"
    const val AUXILIARY_CALENDAR_COLOR = -0xbbcca
    const val MILLIS_PER_MINUTE = 60_000L
    const val MILLIS_PER_DAY = 24 * 60 * MILLIS_PER_MINUTE
    var scheduleSequence: Int = 0
  }
}

/**
 * finalized worker 执行模式的纯契约测试。
 *
 * 本类不初始化 Context、registry 或 ContentResolver；它只验证缺少 exact-session outbound access 时，进入
 * worker callback 会在任何模拟的 Provider 回调和 Completed 发布前失败关闭，因此可安全在 device-test source
 * 中编译而不依赖真实用户日历。
 */
@RunWith(AndroidJUnit4::class)
class FinalizedCalendarExportExecutionInstrumentedTest {
  /** 缺少 access 不得进入 worker callback，也不得发布完成状态。 */
  @Test
  fun missingAccessFailsBeforeProviderCallbacksOrCompletedPublish() {
    var providerCallbacks = 0
    var completedPublishes = 0

    val failure = runCatching {
      runBlocking {
        FinalizedCalendarExportExecution.enter(null) {
          // 该 callback 模拟 worker 内全部 Provider 查询/效果和最后的 Completed 发布；null 必须令其零执行。
          providerCallbacks += 1
          completedPublishes += 1
        }
      }
    }.exceptionOrNull()

    assertTrue(failure is IllegalArgumentException)
    assertEquals(0, providerCallbacks)
    assertEquals(0, completedPublishes)
  }
}
