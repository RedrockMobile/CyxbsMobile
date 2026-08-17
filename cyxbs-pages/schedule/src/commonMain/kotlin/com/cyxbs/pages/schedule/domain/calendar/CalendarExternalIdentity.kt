package com.cyxbs.pages.schedule.domain.calendar

import com.cyxbs.components.config.time.MinuteTimeDateSerializer
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.validation.ScheduleValidator
import com.eygraber.uri.Uri
import kotlin.jvm.JvmInline

private val CALENDAR_SCOPE_PATTERN = Regex("^[a-z0-9_-]{6,128}$")

/**
 * 一个账号在外部日历中的稳定导出空间。
 *
 * 该值只用于隔离和重新识别受管事件，不是授权凭据或 Deep Link token。平台层可直接使用稳定账号标识；
 * 格式限制为 URI-safe 小写文本，避免同一 scope 出现多种编码。
 */
@JvmInline
value class CalendarExportScope private constructor(val value: String) {
  override fun toString(): String = value

  companion object {
    /**
     * 校验并创建导出空间。
     *
     * @throws IllegalArgumentException 输入不是 6..128 位 URI-safe 小写标识时抛出。
     */
    operator fun invoke(value: String): CalendarExportScope {
      require(CALENDAR_SCOPE_PATTERN.matches(value)) { "Invalid calendar export scope" }
      return CalendarExportScope(value)
    }

    /** 解析来自系统日历等不可信边界的 scope；格式非法时返回 `null`。 */
    fun parseOrNull(value: String): CalendarExportScope? =
      if (CALENDAR_SCOPE_PATTERN.matches(value)) CalendarExportScope(value) else null
  }
}

/** v2 外部日历投影的显式类型；不能再通过 RRULE 是否存在来猜测资源身份。 */
enum class CalendarProjectionKind(val uriValue: String) {
  /** 非重复定时或全天日程。 */
  SINGLE("single"),
  /** 截止事项的一分钟平台投影；显式 kind 保证回读幂等，禁止按时长猜测。 */
  DEADLINE("deadline"),
  /** 重复系列的主事件。 */
  SERIES_MASTER("series"),
  /** 重复系列中的原生例外；切片 1 只预留身份，尚不生成此类投影。 */
  OCCURRENCE_EXCEPTION("occurrence");

  companion object {
    /** 按稳定 URI 文本解析投影类型，未知值返回 `null`。 */
    fun fromUriValue(value: String): CalendarProjectionKind? = entries.firstOrNull { it.uriValue == value }
  }
}

/**
 * 与 Android Provider eventId 无关的外部投影身份。
 *
 * [recurrenceId] 仅允许出现在 [CalendarProjectionKind.OCCURRENCE_EXCEPTION]，并且必须保留规则原始生成的
 * 墙上时间、时区和全天属性。实例改期只能改变投影内容，不能改变此身份。
 */
data class CalendarProjectionId(
  val scope: CalendarExportScope,
  val scheduleId: ScheduleId,
  val kind: CalendarProjectionKind,
  val recurrenceId: RecurrenceId? = null,
) {
  init {
    require((kind == CalendarProjectionKind.OCCURRENCE_EXCEPTION) == (recurrenceId != null)) {
      "Only occurrence projection requires recurrence identity"
    }
  }
}

/**
 * `CUSTOM_APP_URI` 使用的 v2 身份协议。
 *
 * 编码结果只承载恢复投影身份所需的版本、scope、Schedule UUID 与 kind，不包含标题或 Provider eventId。
 * 解码面对不可信系统数据时严格拒绝重复参数、未知参数、非法 UUID 和不完整 occurrence identity。
 */
object CalendarProjectionUriCodec {
  /** 将稳定投影身份编码为 canonical v2 URI；query 转义统一交给 uri-kmp。 */
  fun encode(id: CalendarProjectionId): String = Uri.Builder()
    .scheme(SCHEME)
    .authority(AUTHORITY)
    .appendQueryParameter("v", VERSION)
    .appendQueryParameter("scope", id.scope.value)
    .appendQueryParameter("scheduleId", id.scheduleId.value)
    .appendQueryParameter("kind", id.kind.uriValue)
    .apply {
      id.recurrenceId?.let { recurrence ->
        appendQueryParameter("recurrenceLocal", recurrence.originalDateTime.toString())
        recurrence.timeZoneId?.let { appendQueryParameter("timeZoneId", it) }
        appendQueryParameter("allDay", recurrence.allDay.toString())
      }
    }
    .build()
    .toString()

  /**
   * 解析不可信 v2 URI。
   *
   * @return 完整且规范时返回身份；任何结构、版本或字段错误均返回 `null`，不会抛出格式异常。
   */
  fun decodeOrNull(uri: String): CalendarProjectionId? = runCatching {
    val parsed = Uri.parse(uri)
    if (parsed.scheme != SCHEME || parsed.authority != AUTHORITY || parsed.path.orEmpty().isNotEmpty() ||
      parsed.fragment != null || !parsed.isHierarchical
    ) return null
    val names = parsed.getQueryParameterNames()
    if (names.any { it !in orderedKeys }) return null
    // uri-kmp 的 names 是集合，因此还要逐项限制为单值，明确拒绝重复参数形成身份别名。
    val values = names.associateWith { key ->
      parsed.getQueryParameters(key).singleOrNull() ?: return null
    }
    if (values["v"] != VERSION) return null
    val scope = values["scope"]?.let(CalendarExportScope::parseOrNull) ?: return null
    val scheduleId = values["scheduleId"]?.let(ScheduleId::parseOrNull) ?: return null
    val kind = values["kind"]?.let(CalendarProjectionKind::fromUriValue) ?: return null
    val recurrenceKeysPresent = recurrenceKeys.any(values::containsKey)
    val recurrenceId = if (kind == CalendarProjectionKind.OCCURRENCE_EXCEPTION) {
      val local = values["recurrenceLocal"]?.let(MinuteTimeDateSerializer::deserialize) ?: return null
      val allDay = values["allDay"]?.toBooleanStrictOrNull() ?: return null
      val zone = values["timeZoneId"]
      if (allDay == (zone != null)) return null
      RecurrenceId(local, zone, allDay).also {
        if (ScheduleValidator.validate(it).isNotEmpty()) return null
      }
    } else {
      if (recurrenceKeysPresent) return null
      null
    }
    val id = CalendarProjectionId(scope, scheduleId, kind, recurrenceId)
    // 仍只接受本编码器的唯一 canonical 文本，拒绝参数乱序和非必要转义形成身份别名。
    if (encode(id) != uri) return null
    id
  }.getOrNull()

  private const val SCHEME = "cyxbs"
  private const val AUTHORITY = "schedule"
  private const val VERSION = "2"
  private val recurrenceKeys = setOf("recurrenceLocal", "timeZoneId", "allDay")
  private val orderedKeys = setOf("v", "scope", "scheduleId", "kind") + recurrenceKeys
}
