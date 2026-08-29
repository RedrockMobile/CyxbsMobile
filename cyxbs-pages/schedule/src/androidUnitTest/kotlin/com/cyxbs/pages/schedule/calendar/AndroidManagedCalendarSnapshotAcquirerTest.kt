package com.cyxbs.pages.schedule.calendar

import android.provider.CalendarContract
import com.cyxbs.pages.schedule.domain.calendar.AndroidManagedCalendarIdentifierCodec
import com.cyxbs.pages.schedule.domain.calendar.CalendarExportScope
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionFingerprint
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionId
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionKind
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionUriCodec
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 受信快照采集器的 Android JVM host 合同。
 *
 * 测试只使用 [SnapshotReadHostFake] 的普通值行，绝不构造 Context、ContentResolver、真实 Calendar Provider、设备、
 * 用户数据库或网络；它锁定采集算法的 Provider 行复制语义，而不是验证设备 Provider。
 */
class AndroidManagedCalendarSnapshotAcquirerTest {
  /** 单一采集器必须返回 canonical 字段、稳定 fingerprint、排序去重 reminder 与独立 ordinary snapshot。 */
  @Test
  fun acquiresCanonicalCopiedSnapshotFromHostRows() {
    val scope = hostScope()
    val projectionId = hostProjectionId(scope)
    val fake = SnapshotReadHostFake(
      calendarId = 77L,
      recordCalendarRechecks = true,
      events = listOf(hostEventRow(projectionId, eventId = 91L)),
      remindersByEventId = mapOf(
        91L to listOf(
          AndroidManagedCalendarSnapshotReminderRow(30, CalendarContract.Reminders.METHOD_ALERT),
          AndroidManagedCalendarSnapshotReminderRow(10, CalendarContract.Reminders.METHOD_ALERT),
          AndroidManagedCalendarSnapshotReminderRow(30, CalendarContract.Reminders.METHOD_ALERT),
        ),
      ),
    )

    val gates = mutableListOf<String>()
    val snapshot = AndroidManagedCalendarSnapshotAcquirer(fake, HOST_ACCOUNT).acquire(scope) {
      gates += "gate:${gates.size + 1}"
    }
    val present = assertIs<AndroidManagedCalendarSnapshot.Present>(snapshot)
    val event = present.events.single()

    assertEquals(
      AndroidManagedCalendarIdentifierCodec.encode(77L, HOST_INCARNATION),
      present.calendarIdentifier,
    )
    assertEquals(projectionId, event.projectionId)
    assertEquals(listOf(10, 30), event.canonicalFields.deviceReminderMinutes)
    assertEquals(
      CalendarProjectionFingerprint.compute(
        externalUri = CalendarProjectionUriCodec.encode(projectionId),
        title = "host title",
        description = "host description",
        timing = event.canonicalFields.timing,
        recurrenceRule = null,
        reminderMinutes = listOf(10, 30),
      ),
      event.providerFingerprint,
    )
    assertEquals(
      listOf(
        "gate:1", "gate:2", "gate:3", "gate:4", "gate:5",
        "gate:6", "gate:7", "gate:8", "gate:9", "gate:10",
        "gate:11", "gate:12", "gate:13",
      ),
      gates,
      "初始 gate 后，Calendar、Events、Reminders 与末尾 identity 重读都在 query/copy 边界复核 currentness",
    )
    assertEquals(
      listOf("permission", "calendar", "events:all", "reminders:91", "calendar"),
      fake.operations,
      "host fake 仅记录只读边界，采集结果不保留任何 Provider 资源或副作用",
    )
  }

  /**
   * Reminder cursor 复制期间 Calendar row/incarnation 漂移时，末尾 identity 重读必须在暴露 observations 前失败关闭。
   *
   * 四种 mutation 都只发生在内存 fake 的读取窗口；它们分别覆盖 row 消失、畸形 token、row 替换与合法 token 替换，
   * 不访问真实 Calendar Provider。
   */
  @Test
  fun calendarIdentityDriftDuringReminderReadFailsBeforeReturningObservations() {
    val scope = hostScope()
    val identityMutations = listOf<Pair<String, SnapshotReadHostFake.() -> Unit>>(
      "calendar-absent" to { calendarId = null },
      "malformed-incarnation" to { calendarIncarnation = "not-a-uuid" },
      "calendar-row-replaced" to { calendarId = 78L },
      "calendar-incarnation-replaced" to {
        calendarIncarnation = "42424242-4242-4242-8242-424242424242"
      },
    )

    identityMutations.forEach { (caseName, mutateIdentity) ->
      lateinit var fake: SnapshotReadHostFake
      fake = SnapshotReadHostFake(
        calendarId = 77L,
        recordCalendarRechecks = true,
        events = listOf(hostEventRow(hostProjectionId(scope), eventId = 94L)),
        remindersByEventId = mapOf(
          94L to listOf(
            AndroidManagedCalendarSnapshotReminderRow(
              15,
              CalendarContract.Reminders.METHOD_ALERT,
            )
          ),
        ),
        onReminderCopyRows = { mutateIdentity.invoke(fake) },
      )

      assertFailsWith<AndroidScheduleCalendarGateway.CalendarProviderReadException>(caseName) {
        AndroidManagedCalendarSnapshotAcquirer(fake, HOST_ACCOUNT).acquire(scope)
      }
      assertEquals(
        listOf("permission", "calendar", "events:all", "reminders:94", "calendar"),
        fake.operations,
        "$caseName 必须完成末尾 Calendar 重读后失败，不能返回先前已规范化的 observation",
      )
    }
  }


  /** currentness 在每个 query 前、返回后 cursor copy 前与 copy/close 后复核；Events 返回后撤销时不得读取 reminder。 */
  @Test
  fun currentnessGateOrdersEachReadBoundaryAndStopsBeforeReminder() {
    val scope = hostScope()
    val projectionId = hostProjectionId(scope)
    val fake = SnapshotReadHostFake(
      events = listOf(hostEventRow(projectionId, eventId = 92L)),
      remindersByEventId = mapOf(
        92L to listOf(
          AndroidManagedCalendarSnapshotReminderRow(
            5,
            CalendarContract.Reminders.METHOD_ALERT
          )
        ),
      ),
    )
    val gates = mutableListOf<String>()

    val failure = assertFailsWith<HostCurrentnessRevoked> {
      AndroidManagedCalendarSnapshotAcquirer(fake, HOST_ACCOUNT).acquire(scope) {
        gates += "gate:${gates.size + 1}"
        if (fake.hasReadEvents) throw HostCurrentnessRevoked()
      }
    }

    assertEquals("host currentness revoked", failure.message)
    assertEquals(
      listOf("gate:1", "gate:2", "gate:3", "gate:4", "gate:5", "gate:6"),
      gates,
      "初始、Calendar 的 query/copy 完成点与 Events 的 query/copy 前均必须落在实际读取边界",
    )
    assertEquals(listOf("permission", "calendar", "events:all"), fake.operations)
  }

  /**
   * host cursor 在 copyRows 期间撤销 currentness 时，复制完成后不得继续 canonicalize 或读取 reminder。
   *
   * 此回归只操纵内存 fake 的 copy hook，覆盖真实 CursorWindow 填充可能阻塞的窗口；它不是设备 Provider 验证。
   */
  @Test
  fun currentnessRevokedDuringCursorCopyStopsBeforeCanonicalization() {
    val scope = hostScope()
    var currentnessRevoked = false
    val fake = SnapshotReadHostFake(
      events = listOf(hostEventRow(hostProjectionId(scope), eventId = 95L)),
      onEventCopyRows = { currentnessRevoked = true },
    )
    val gates = mutableListOf<String>()

    val failure = assertFailsWith<HostCurrentnessRevoked> {
      AndroidManagedCalendarSnapshotAcquirer(fake, HOST_ACCOUNT).acquire(scope) {
        gates += "gate:${gates.size + 1}"
        if (currentnessRevoked) throw HostCurrentnessRevoked()
      }
    }

    assertEquals("host currentness revoked", failure.message)
    assertEquals(
      listOf("gate:1", "gate:2", "gate:3", "gate:4", "gate:5", "gate:6", "gate:7"),
      gates,
      "第七次 gate 必须位于 Events copyRows 和 cursor close 之后、canonicalization 之前",
    )
    assertEquals(
      listOf("permission", "calendar", "events:all"),
      fake.operations,
      "currentness 撤销后不得继续读取 event reminder",
    )
  }

  /** 非正 Calendar row ID 属于不可信 Provider 行，必须统一归类为可重试的读取失败。 */
  @Test
  fun invalidCalendarRowIdIsProviderReadFailure() {
    val fake = SnapshotReadHostFake(calendarId = 0L)

    val failure = assertFailsWith<AndroidScheduleCalendarGateway.CalendarProviderReadException> {
      AndroidManagedCalendarSnapshotAcquirer(fake, HOST_ACCOUNT).acquire(hostScope())
    }

    assertEquals("Invalid managed Provider calendar ID: 0", failure.message)
    assertEquals(listOf("permission", "calendar"), fake.operations)
  }

  /** tokenless 或畸形 `CAL_SYNC1` 是迁移边界读取失败，不能伪装为 CalendarAbsent 或自动补写。 */
  @Test
  fun tokenlessAndMalformedCalendarIncarnationsAreProviderReadFailures() {
    listOf(
      null,
      "not-a-uuid",
      HOST_INCARNATION.uppercase(),
      "$HOST_INCARNATION ",
    ).forEach { incarnation ->
      val fake = SnapshotReadHostFake(calendarIncarnation = incarnation)

      val failure = assertFailsWith<AndroidScheduleCalendarGateway.CalendarProviderReadException> {
        AndroidManagedCalendarSnapshotAcquirer(fake, HOST_ACCOUNT).acquire(hostScope())
      }

      assertTrue(failure.message.orEmpty().contains("CAL_SYNC1"), incarnation)
      assertEquals(listOf("permission", "calendar"), fake.operations, incarnation)
    }
  }

  /** `CAL_SYNC2` 缺失或不匹配时不读取 Events，而是要求上游安全重建整个受管投影。 */
  @Test
  fun mismatchedProjectionVersionRequiresManagedCalendarRebuild() {
    listOf(null, "0", "2").forEach { version ->
      val fake = SnapshotReadHostFake(calendarProjectionVersion = version)

      val failure = assertFailsWith<ManagedCalendarRebuildRequiredException> {
        AndroidManagedCalendarSnapshotAcquirer(fake, HOST_ACCOUNT).acquire(hostScope())
      }

      assertTrue(failure.message.orEmpty().contains("projection version"), version)
      assertEquals(listOf("permission", "calendar"), fake.operations, version)
    }
  }


  /** host cursor 的 close 失败也必须与 query/copy 失败相同地归类为 Provider 读取失败。 */
  @Test
  fun cursorCloseFailureIsProviderReadFailure() {
    val fake = SnapshotReadHostFake(
      eventCursorCloseFailure = IllegalStateException("host event cursor close failed"),
    )

    val failure = assertFailsWith<AndroidScheduleCalendarGateway.CalendarProviderReadException> {
      AndroidManagedCalendarSnapshotAcquirer(fake, HOST_ACCOUNT).acquire(hostScope())
    }

    assertEquals("Cannot copy managed calendar event cursor", failure.message)
    assertEquals("host event cursor close failed", failure.cause?.message)
    assertEquals(listOf("permission", "calendar", "events:all"), fake.operations)
  }

  /** gate 的取消不能被通用 Provider 错误包装；初始取消前甚至不应访问 host read port。 */
  @Test
  fun cancellationFromCurrentnessGatePropagatesWithoutProviderWrapping() {
    val fake = SnapshotReadHostFake()

    val failure = assertFailsWith<CancellationException> {
      AndroidManagedCalendarSnapshotAcquirer(fake, HOST_ACCOUNT).acquire(hostScope()) {
        throw CancellationException("host acquisition cancelled")
      }
    }

    assertEquals("host acquisition cancelled", failure.message)
    assertTrue(fake.operations.isEmpty())
  }

  /** SecurityException 与真实 Provider 错误必须保持不同分型，供上游正确停止授权重试或报告 Provider 失败。 */
  @Test
  fun securityAndProviderFailuresKeepDistinctCategories() {
    val securityFake =
      SnapshotReadHostFake(eventFailure = SecurityException("host permission revoked"))
    val security = assertFailsWith<SecurityException> {
      AndroidManagedCalendarSnapshotAcquirer(securityFake, HOST_ACCOUNT).acquire(hostScope())
    }
    assertEquals("host permission revoked", security.message)

    val providerFake =
      SnapshotReadHostFake(eventFailure = IllegalStateException("host provider broken"))
    val providerFailure =
      assertFailsWith<AndroidScheduleCalendarGateway.CalendarProviderReadException> {
        AndroidManagedCalendarSnapshotAcquirer(providerFake, HOST_ACCOUNT).acquire(hostScope())
      }
    assertEquals("Cannot read managed calendar events", providerFailure.message)
    assertEquals("host provider broken", providerFailure.cause?.message)
  }

  /** 已确认属于应用的托管行无法 canonicalize 时要求整表重建，不能被折叠为空快照或直接增量写入。 */
  @Test
  fun canonicalizationFailureIsProviderReadFailure() {
    val scope = hostScope()
    val badRow = hostEventRow(hostProjectionId(scope), eventId = 93L).copy(allDay = 2)
    val fake = SnapshotReadHostFake(events = listOf(badRow))

    val failure = assertFailsWith<ManagedCalendarRebuildRequiredException> {
      AndroidManagedCalendarSnapshotAcquirer(fake, HOST_ACCOUNT).acquire(scope)
    }

    assertEquals("Invalid managed event ALL_DAY value: 93", failure.message)
    assertEquals(listOf("permission", "calendar", "events:all"), fake.operations)
  }

  /** 采集器和其窄读取端口不得暴露任何 Provider 写方法。 */
  @Test
  fun acquirerAndHostPortExposeNoWriteMethods() {
    val forbidden = listOf("create", "insert", "update", "delete", "applybatch")
    val acquirerMethods = AndroidManagedCalendarSnapshotAcquirer::class.java.methods
      .filter { it.declaringClass == AndroidManagedCalendarSnapshotAcquirer::class.java }
      .map { it.name.lowercase() }
    val portMethods =
      AndroidManagedCalendarSnapshotReadPlatform::class.java.methods.map { it.name.lowercase() }

    assertTrue(acquirerMethods.none { name -> forbidden.any(name::contains) })
    assertTrue(portMethods.none { name -> forbidden.any(name::contains) })
  }
}

/** 仅供 Android JVM host 测试复用的窄读取 fake；所有行都是内存普通值，不会触达 Android Provider。 */
internal class SnapshotReadHostFake(
  override val packageName: String = "com.cyxbs.schedule.host",
  var calendarId: Long? = 41L,
  var calendarIncarnation: String? = HOST_INCARNATION,
  var calendarProjectionVersion: String? = AndroidManagedCalendarRegistry.CURRENT_PROJECTION_VERSION,
  var events: List<AndroidManagedCalendarSnapshotEventRow>? = emptyList(),
  private val remindersByEventId: Map<Long, List<AndroidManagedCalendarSnapshotReminderRow>?> = emptyMap(),
  private val calendarFailure: Throwable? = null,
  private val eventFailure: Throwable? = null,
  private val reminderFailure: Throwable? = null,
  private val onEventCopyRows: (() -> Unit)? = null,
  private val onReminderCopyRows: (() -> Unit)? = null,
  private val eventCursorCloseFailure: Throwable? = null,
  private val recordCalendarRechecks: Boolean = false,
) : AndroidManagedCalendarSnapshotReadPlatform {
  val operations = mutableListOf<String>()
  private var calendarQueryCount = 0
  var hasReadEvents = false
    private set

  /** fake 权限检查只记录顺序；需要失败时由专门的 query failure 覆盖分型。 */
  override fun requireCalendarPermissions() {
    operations += "permission"
  }

  /**
   * 模拟 Calendar query 返回的只读 cursor，绝不创建日历。
   *
   * 旧 gateway fixture 只关心读取种类，默认不重复记录末尾 recheck；本类的 identity 漂移回归可显式开启完整顺序。
   */
  override fun queryCurrentManagedCalendar(
    accountId: String,
  ): AndroidManagedCalendarSnapshotRowCursor<AndroidManagedCalendarSnapshotCalendarRow>? {
    calendarQueryCount += 1
    if (calendarQueryCount == 1 || recordCalendarRechecks) operations += "calendar"
    calendarFailure?.let { throw it }
    return SnapshotReadHostRowCursor(
      calendarId?.let {
        listOf(
          AndroidManagedCalendarSnapshotCalendarRow(
            calendarId = it,
            incarnation = calendarIncarnation,
            projectionVersion = calendarProjectionVersion,
          ),
        )
      } ?: emptyList(),
    )
  }

  /** 模拟一个 Events 分页，保留 `null cursor` 可表达的 nullable 返回值。 */
  override fun queryEvents(
    calendarId: Long,
    scope: CalendarExportScope,
    scheduleIds: List<ScheduleId>?,
  ): AndroidManagedCalendarSnapshotRowCursor<AndroidManagedCalendarSnapshotEventRow>? {
    operations += "events:${scheduleIds?.size ?: "all"}"
    hasReadEvents = true
    eventFailure?.let { throw it }
    return events?.let { rows ->
      SnapshotReadHostRowCursor(
        rows = rows,
        onCopyRows = onEventCopyRows,
        closeFailure = eventCursorCloseFailure,
      )
    }
  }

  /** 模拟 event reminders 的独立读取，并可在 copy 窗口改变 fake Calendar identity 以覆盖末尾漂移门禁。 */
  override fun queryReminders(
    eventId: Long,
  ): AndroidManagedCalendarSnapshotRowCursor<AndroidManagedCalendarSnapshotReminderRow>? {
    operations += "reminders:$eventId"
    reminderFailure?.let { throw it }
    return if (remindersByEventId.containsKey(eventId)) {
      remindersByEventId[eventId]?.let { rows ->
        SnapshotReadHostRowCursor(rows, onCopyRows = onReminderCopyRows)
      }
    } else {
      SnapshotReadHostRowCursor(emptyList(), onCopyRows = onReminderCopyRows)
    }
  }
}

/**
 * host fake 的关闭型普通值 cursor。
 *
 * [onCopyRows] 与 [closeFailure] 仅模拟 copy/close 阻塞窗口中的状态变化或读取端口失败，绝不访问设备或真实 Provider。
 */
internal class SnapshotReadHostRowCursor<T>(
  private val rows: List<T>,
  private val onCopyRows: (() -> Unit)? = null,
  private val closeFailure: Throwable? = null,
) : AndroidManagedCalendarSnapshotRowCursor<T> {
  var closeCalls = 0
    private set

  /** 返回脱离 fake 内部列表的普通值副本，并在副本完成后模拟 copyRows 期间发生的 currentness 撤销。 */
  override fun copyRows(): List<T> = rows.toList().also { onCopyRows?.invoke() }

  /** 模拟 Provider cursor 资源关闭；失败会供采集器验证统一 Provider 失败分型。 */
  override fun close() {
    closeCalls += 1
    closeFailure?.let { throw it }
  }
}

/** currentness gate 的 host 专用异常，用于证明它不会变成 Provider 错误。 */
internal class HostCurrentnessRevoked : IllegalStateException("host currentness revoked")

/** 构造适合 UTC 单次 Timed event 的最小 canonical scope。 */
internal fun hostScope() = CalendarExportScope("host_snapshot_scope")

/** 构造可编码为 canonical v2 URI 的 host 投影身份。 */
internal fun hostProjectionId(scope: CalendarExportScope) = CalendarProjectionId(
  scope = scope,
  scheduleId = ScheduleId("018f0f7c-6000-7000-8000-000000000091"),
  kind = CalendarProjectionKind.SINGLE,
)

/** 构造与 gateway 历史 cursor 字段等价的普通 Events 行。 */
internal fun hostEventRow(
  projectionId: CalendarProjectionId,
  eventId: Long,
) = AndroidManagedCalendarSnapshotEventRow(
  eventId = eventId,
  customAppPackage = "com.cyxbs.schedule.host",
  customAppUri = CalendarProjectionUriCodec.encode(projectionId),
  title = "host title",
  description = "host description",
  dtStart = 1_735_689_600_000L,
  dtEnd = 1_735_693_200_000L,
  duration = null,
  eventTimeZone = "UTC",
  allDay = 0,
  recurrenceRule = null,
  rDate = null,
)

internal const val HOST_INCARNATION = "c1414141-4141-4141-8141-414141414141"
private const val HOST_ACCOUNT = "host-account"
