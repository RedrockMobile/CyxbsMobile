package com.cyxbs.pages.schedule.calendar

import android.provider.CalendarContract
import com.cyxbs.pages.schedule.domain.calendar.AndroidManagedCalendarIdentifierCodec
import com.cyxbs.pages.schedule.domain.calendar.CalendarExportScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * gateway 到受信快照采集器的 Android JVM host 委派合同。
 *
 * 本类仅注入内存 [SnapshotReadHostFake]；不会构造或调用 Android [android.content.Context]、ContentResolver、真实
 * Calendar Provider、设备、用户数据库或网络。它证明 gateway 的读取没有保留第二条 cursor/canonicalization 路径，写 API
 * 也不会由采集器取得。
 */
class AndroidScheduleCalendarGatewayTest {
  /** gateway 快照结果必须等价于同一采集器的结果，且 fake 仅收到 Calendar/Events/Reminders 的读取。 */
  @Test
  fun gatewayDelegatesSnapshotAcquisitionToTrustedAcquirer() {
    val scope = hostScope()
    val projectionId = hostProjectionId(scope)
    val directFake = hostFake(projectionId)
    val gatewayFake = hostFake(projectionId)

    val direct =
      AndroidManagedCalendarSnapshotAcquirer(directFake, "gateway-host-account").acquire(scope)
    val gateway = AndroidScheduleCalendarGateway(
      accountId = "gateway-host-account",
      snapshotReadPlatform = gatewayFake,
    )
    val delegated = gateway.queryManagedCalendarSnapshot(scope)
    val present = assertIs<AndroidManagedCalendarSnapshot.Present>(delegated)

    assertEquals(direct, delegated)
    assertEquals(
      AndroidManagedCalendarIdentifierCodec.encode(88L, HOST_INCARNATION),
      present.calendarIdentifier,
    )
    assertEquals(listOf(7), present.events.single().canonicalFields.deviceReminderMinutes)
    assertEquals(
      listOf("permission", "calendar", "events:all", "reminders:94"),
      gatewayFake.operations,
      "gateway 只通过采集器触发三个只读边界，未访问任何写入 API",
    )
  }

  /** 空 Schedule ID 子集仍需查询 Calendar identity，但绝不能触发 Events 或 Reminders 查询。 */
  @Test
  fun emptyScheduleSubsetKeepsCalendarAbsentDistinctFromPresentEmptySnapshot() {
    val scope = hostScope()
    val fake = hostFake(hostProjectionId(scope))
    val gateway = AndroidScheduleCalendarGateway(
      accountId = "gateway-host-account",
      snapshotReadPlatform = fake,
    )

    val snapshot = gateway.queryManagedCalendarSnapshot(scope, emptySet())
    val present = assertIs<AndroidManagedCalendarSnapshot.Present>(snapshot)

    assertEquals(emptyList(), present.events)
    assertEquals(listOf("permission", "calendar"), fake.operations)
  }

  /** 构造一份全内存 host 行；测试名字与断言均不把它描述为真实 Provider 验证。 */
  private fun hostFake(projectionId: com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionId) =
    SnapshotReadHostFake(
      calendarId = 88L,
      events = listOf(hostEventRow(projectionId, eventId = 94L)),
      remindersByEventId = mapOf(
        94L to listOf(
          AndroidManagedCalendarSnapshotReminderRow(
            7,
            CalendarContract.Reminders.METHOD_ALERT
          )
        ),
      ),
    )
}
