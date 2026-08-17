package com.cyxbs.pages.schedule.calendar

import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.pages.schedule.domain.calendar.CalendarExportScope
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionId
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionUriCodec
import com.cyxbs.pages.schedule.domain.calendar.PlatformCalendarEventRef

/**
 * iOS 日历导出使用的账号级偏好端口。
 *
 * 运行时只通过该端口读写 source、calendar 与事件定位缓存；测试可注入纯内存实现，避免构造平台 Settings 或读取
 * 用户数据。事件引用是已验证 locator，不是 EventKit 所有权证明。
 */
internal interface IosScheduleCalendarPreferenceStore {
  fun get(accountId: String): IosScheduleCalendarExportSettings.Preference

  fun updateSourceIdentifier(accountId: String, sourceIdentifier: String)

  fun updateCalendarIdentifier(accountId: String, calendarIdentifier: String?)

  fun setEnabled(accountId: String, enabled: Boolean)

  /** 仅在本轮严格验证或写后回读成功后替换一个投影的 opaque event locator。 */
  fun replaceEventReference(
    accountId: String,
    projectionId: CalendarProjectionId,
    eventRef: PlatformCalendarEventRef,
  )

  /** 仅在 event 缺失或严格删除确认后移除一个 locator。 */
  fun removeEventReference(accountId: String, projectionId: CalendarProjectionId)

  /** source 切换或 calendar cache 无效时清空所有事件 locator，禁止跨 source 复用。 */
  fun clearEventReferences(accountId: String)
}

/**
 * iOS 系统日历导出的账号级偏好。
 *
 * 所有键均以规范化小写 accountId 分区。`EVENT_REFERENCE_LEDGER_KEY` 是唯一的严格序列化 ledger：每条记录以
 * canonical v2 URI 绑定平台 event identifier，读回时必须再次解析 URI 并验证 account scope，损坏条目不会成为
 * 可用引用。source/calendar/event identifier 都只用于精确定位，不构成日历所有权。
 */
internal object IosScheduleCalendarExportSettings : IosScheduleCalendarPreferenceStore {
  private const val ENABLED_KEY = "schedule.ios_calendar_export_enabled"
  private const val SOURCE_IDENTIFIER_KEY = "schedule.ios_calendar_source_identifier"
  private const val CALENDAR_IDENTIFIER_KEY = "schedule.ios_calendar_identifier"
  private const val EVENT_REFERENCE_LEDGER_KEY = "schedule.ios_calendar_event_reference_ledger_v1"

  /** 一次读取的稳定快照，供 controller/runtime 在冻结 session 内复核。 */
  data class Preference(
    val enabled: Boolean,
    val sourceIdentifier: String?,
    val calendarIdentifier: String?,
    val eventReferences: Map<CalendarProjectionId, PlatformCalendarEventRef> = emptyMap(),
  )

  /** 读取指定账号设置，禁止使用会随登录状态变化的 [AccountSettings.now]。 */
  override fun get(accountId: String): Preference {
    val canonicalAccountId = canonicalAccountId(accountId)
    val settings = AccountSettings.get(canonicalAccountId)
    return Preference(
      enabled = settings.getBoolean(ENABLED_KEY, false),
      sourceIdentifier = settings.getStringOrNull(SOURCE_IDENTIFIER_KEY),
      calendarIdentifier = settings.getStringOrNull(CALENDAR_IDENTIFIER_KEY),
      eventReferences = decodeLedger(
        serialized = settings.getStringOrNull(EVENT_REFERENCE_LEDGER_KEY),
        scope = CalendarExportScope(canonicalAccountId),
      ),
    )
  }

  /** 保存用户明确选择的 source；controller 会同步处理旧 calendar 与 event locator。 */
  override fun updateSourceIdentifier(accountId: String, sourceIdentifier: String) {
    require(sourceIdentifier.isNotBlank()) { "sourceIdentifier must not be blank" }
    AccountSettings.get(canonicalAccountId(accountId)).putString(SOURCE_IDENTIFIER_KEY, sourceIdentifier)
  }

  /** 更新 nullable calendar cache；设置阶段绝不预创建 calendar。 */
  override fun updateCalendarIdentifier(accountId: String, calendarIdentifier: String?) {
    val settings = AccountSettings.get(canonicalAccountId(accountId))
    if (calendarIdentifier == null) settings.remove(CALENDAR_IDENTIFIER_KEY)
    else settings.putString(CALENDAR_IDENTIFIER_KEY, calendarIdentifier)
  }

  /** 写入未来 runtime intent；关闭不删除 EventKit 数据或 locator。 */
  override fun setEnabled(accountId: String, enabled: Boolean) {
    AccountSettings.get(canonicalAccountId(accountId)).putBoolean(ENABLED_KEY, enabled)
  }

  /** 严格替换单条 locator，不接受跨账号 scope 的 projection identity。 */
  override fun replaceEventReference(
    accountId: String,
    projectionId: CalendarProjectionId,
    eventRef: PlatformCalendarEventRef,
  ) {
    val canonicalAccountId = canonicalAccountId(accountId)
    require(projectionId.scope == CalendarExportScope(canonicalAccountId)) {
      "Projection scope does not match calendar preference account"
    }
    val updated = get(canonicalAccountId).eventReferences.toMutableMap().apply {
      put(projectionId, eventRef)
    }
    writeLedger(canonicalAccountId, updated)
  }

  /** 严格移除单条 locator；没有该条目时不产生其他 cache 副作用。 */
  override fun removeEventReference(accountId: String, projectionId: CalendarProjectionId) {
    val canonicalAccountId = canonicalAccountId(accountId)
    val updated = get(canonicalAccountId).eventReferences.toMutableMap()
    if (updated.remove(projectionId) != null) writeLedger(canonicalAccountId, updated)
  }

  /** source/calendar 绑定失效后清空整份 locator ledger，避免旧 source 的 event id 被复用。 */
  override fun clearEventReferences(accountId: String) {
    AccountSettings.get(canonicalAccountId(accountId)).remove(EVENT_REFERENCE_LEDGER_KEY)
  }

  /** #281 首个 atomic calendar+event commit 成功后才可以调用，不能在 #280 设置阶段预创建日历。 */
  fun updateCalendarCacheAfterAtomicExport(accountId: String, calendarIdentifier: String) {
    require(calendarIdentifier.isNotBlank()) { "calendarIdentifier must not be blank" }
    updateCalendarIdentifier(accountId, calendarIdentifier)
  }

  /** 账号 scope 是 canonical lowercase accountId，不是 token 或 EventKit 所有权证据。 */
  fun scopeForAccount(accountId: String): CalendarExportScope =
    CalendarExportScope(canonicalAccountId(accountId))

  /**
   * 用长度前缀编码一份 ledger，避免 opaque EventKit identifier 中的分隔符改变记录边界。
   *
   * 只有完整消费字符串、唯一 canonical URI、当前 scope 与可构造 locator 全部成立时才接受记录；任一残缺记录使
   * 整份 ledger fail-closed 为空，而不是部分保留可能错位的引用。
   */
  private fun decodeLedger(
    serialized: String?,
    scope: CalendarExportScope,
  ): Map<CalendarProjectionId, PlatformCalendarEventRef> {
    if (serialized.isNullOrEmpty()) return emptyMap()
    val result = linkedMapOf<CalendarProjectionId, PlatformCalendarEventRef>()
    var offset = 0
    while (offset < serialized.length) {
      val uri = readLengthPrefixed(serialized, offset) ?: return emptyMap()
      offset = uri.nextOffset
      val ref = readLengthPrefixed(serialized, offset) ?: return emptyMap()
      offset = ref.nextOffset
      val id = CalendarProjectionUriCodec.decodeOrNull(uri.value)
        ?.takeIf { it.scope == scope }
        ?: return emptyMap()
      val eventRef = runCatching { PlatformCalendarEventRef(ref.value) }.getOrNull() ?: return emptyMap()
      if (result.put(id, eventRef) != null) return emptyMap()
    }
    return result
  }

  /**
   * 仅供 iosTest 验证 ledger 编解码的无副作用入口。
   *
   * 它只处理调用方给出的 canonical identity/opaque locator，不能访问 `AccountSettings`、账号当前态或 EventKit，避免
   * 测试为构造损坏持久化文本而获得设置读写权限。
   */
  internal fun encodeEventReferenceLedgerForTest(
    values: Map<CalendarProjectionId, PlatformCalendarEventRef>,
  ): String = encodeLedger(values)

  /** 仅供 iosTest 验证损坏文本 fail-closed 的无副作用入口。 */
  internal fun decodeEventReferenceLedgerForTest(
    serialized: String?,
    scope: CalendarExportScope,
  ): Map<CalendarProjectionId, PlatformCalendarEventRef> = decodeLedger(serialized, scope)

  /** 以 projection URI 的稳定排序写入整个 ledger，确保同一 map 产生唯一 durable 文本。 */
  private fun writeLedger(
    accountId: String,
    values: Map<CalendarProjectionId, PlatformCalendarEventRef>,
  ) {
    val settings = AccountSettings.get(accountId)
    if (values.isEmpty()) {
      settings.remove(EVENT_REFERENCE_LEDGER_KEY)
      return
    }
    settings.putString(EVENT_REFERENCE_LEDGER_KEY, encodeLedger(values))
  }

  /** 将仅含严格 identity/locator 的 ledger 规范化为唯一 durable 文本。 */
  private fun encodeLedger(values: Map<CalendarProjectionId, PlatformCalendarEventRef>): String = values.entries
    .sortedBy { CalendarProjectionUriCodec.encode(it.key) }
    .joinToString(separator = "") { (id, ref) ->
      val uri = CalendarProjectionUriCodec.encode(id)
      "${uri.length}:$uri${ref.value.length}:${ref.value}"
    }

  private data class ParsedValue(val value: String, val nextOffset: Int)

  /** 读取一段 `length:value`；负数、非数字、越界与不完整内容都拒绝。 */
  private fun readLengthPrefixed(value: String, start: Int): ParsedValue? {
    val separator = value.indexOf(':', start)
    if (separator == -1) return null
    val length = value.substring(start, separator).toIntOrNull() ?: return null
    val contentStart = separator + 1
    // 先以减法验证剩余容量，禁止 `contentStart + length` 在损坏 Int 前缀下溢出后进入 substring。
    if (length < 0 || contentStart > value.length || length > value.length - contentStart) return null
    val contentEnd = contentStart + length
    return ParsedValue(value.substring(contentStart, contentEnd), contentEnd)
  }

  private fun canonicalAccountId(accountId: String): String =
    accountId.trim().lowercase().also { require(it.isNotBlank()) { "accountId must not be blank" } }
}
