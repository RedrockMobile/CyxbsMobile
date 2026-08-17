package com.cyxbs.pages.schedule.domain.calendar

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone

/**
 * 从系统日历 Provider 读取并规范化后的受管字段。
 *
 * 该类型只服务单向导出的“现有副本与目标投影比较”，不保存双向同步基线、冲突状态或本地操作身份。
 * reminder 集合会在构造时复制，避免调用方后续修改破坏指纹比较。
 */
@ConsistentCopyVisibility
data class CanonicalCalendarFields private constructor(
  val title: String,
  val description: String,
  val timing: CalendarTiming,
  val recurrenceRule: String?,
  private val immutableDeviceReminderMinutes: ImmutableReminderMinutes,
) {
  /** 创建经过完整规范校验的 Provider 字段快照。 */
  constructor(
    title: String,
    description: String,
    timing: CalendarTiming,
    recurrenceRule: String?,
    deviceReminderMinutes: List<Int>,
  ) : this(
    title,
    description,
    timing,
    recurrenceRule,
    ImmutableReminderMinutes(deviceReminderMinutes),
  )

  /** 已复制且保持严格递增的 DEVICE 提醒分钟数。 */
  val deviceReminderMinutes: List<Int>
    get() = immutableDeviceReminderMinutes

  init {
    CanonicalCalendarFieldsValidator.validate(
      title,
      description,
      timing,
      recurrenceRule,
      immutableDeviceReminderMinutes,
    )
  }
}

/** 只实现只读列表，防止构造参数中的 MutableList 被保留或向下转型。 */
private class ImmutableReminderMinutes(source: List<Int>) : List<Int> by source.toList() {
  override fun equals(other: Any?): Boolean = other is List<*> && toList() == other

  override fun hashCode(): Int = toList().hashCode()

  override fun toString(): String = toList().toString()
}

/** 单向 Provider 快照的规范字段校验。 */
private object CanonicalCalendarFieldsValidator {
  /**
   * 校验 Provider 读取结果能够被当前投影器无损理解。
   *
   * RRULE 只接受本模块当前支持的 canonical 形式；不认识的外部编辑会在快照读取边界失败关闭，避免近似覆盖。
   */
  fun validate(
    title: String,
    description: String,
    timing: CalendarTiming,
    recurrenceRule: String?,
    deviceReminderMinutes: List<Int>,
  ) {
    require(title.isNotBlank() && title == title.trim()) { "title must be trimmed non-blank text" }
    requireWellFormedUtf16(title, "title")
    require(description == description.trim()) { "description must be trimmed" }
    requireWellFormedUtf16(description, "description")
    validateTiming(timing)
    recurrenceRule?.let { rule ->
      require(rule.isNotBlank()) { "recurrenceRule must not be blank" }
      requireWellFormedUtf16(rule, "recurrenceRule")
      val canonical = CalendarRecurrenceCanonicalizer.canonicalizeOrNull(
        rule,
        allDay = timing is CalendarTiming.AllDay,
      )
      require(canonical == rule) { "recurrenceRule must use supported canonical RRULE" }
      requireSemanticallyValidUntil(rule, timing is CalendarTiming.AllDay)
    }
    require(deviceReminderMinutes.all { it >= 0 }) { "DEVICE reminders must be non-negative" }
    require(deviceReminderMinutes.zipWithNext().all { (previous, next) -> previous < next }) {
      "DEVICE reminders must be unique and strictly increasing"
    }
  }

  /** 文本结构校验之外，再验证 UNTIL 确实表示真实日期或时间。 */
  private fun requireSemanticallyValidUntil(rule: String, allDay: Boolean) {
    val until = rule.split(';').firstOrNull { it.startsWith("UNTIL=") }?.substringAfter('=') ?: return
    val valid = runCatching {
      if (allDay) {
        require(until.length == 8)
        LocalDate(
          until.substring(0, 4).toInt(),
          Month(until.substring(4, 6).toInt()),
          until.substring(6, 8).toInt(),
        )
      } else {
        require(until.length == 16 && until[8] == 'T' && until.last() == 'Z')
        LocalDateTime(
          until.substring(0, 4).toInt(),
          Month(until.substring(4, 6).toInt()),
          until.substring(6, 8).toInt(),
          until.substring(9, 11).toInt(),
          until.substring(11, 13).toInt(),
          until.substring(13, 15).toInt(),
        )
      }
    }.isSuccess
    require(valid) { "recurrenceRule UNTIL must be a real calendar date-time" }
  }

  /** timing 必须满足 gateway 能精确写回的正时长与合法时区约束。 */
  private fun validateTiming(value: CalendarTiming) {
    when (value) {
      is CalendarTiming.Timed -> {
        require(value.durationMinutes > 0) { "timed durationMinutes must be positive" }
        TimeZone.of(value.timeZoneId)
      }
      is CalendarTiming.AllDay -> require(value.durationDays > 0) {
        "all-day durationDays must be positive"
      }
      is CalendarTiming.Deadline -> TimeZone.of(value.timeZoneId)
    }
  }

  /** 拒绝孤立 UTF-16 surrogate，避免 Provider 文本在后续编码时被替换。 */
  private fun requireWellFormedUtf16(value: String, field: String) {
    var index = 0
    while (index < value.length) {
      when (value[index]) {
        in '\uD800'..'\uDBFF' -> {
          require(index + 1 < value.length && value[index + 1] in '\uDC00'..'\uDFFF') {
            "$field must not contain an unpaired UTF-16 surrogate"
          }
          index += 2
        }
        in '\uDC00'..'\uDFFF' -> require(false) {
          "$field must not contain an unpaired UTF-16 surrogate"
        }
        else -> index++
      }
    }
  }
}

/** 将 Schedule 目标投影复制为系统日历侧的 canonical 比较字段，不保存双向基线或冲突状态。 */
object CalendarCanonicalBaselineMapper {
  /**
   * 生成单向 adapter 用于幂等比较的字段快照。
   *
   * 参数中的列表会由 [CanonicalCalendarFields] 复制并校验；本方法不访问 Provider/EventKit，也不持久化状态。
   */
  fun toCalendarFields(projection: CalendarEventProjection): CanonicalCalendarFields =
    CanonicalCalendarFields(
      title = projection.title,
      description = projection.description,
      timing = projection.timing,
      recurrenceRule = projection.recurrenceRule,
      deviceReminderMinutes = projection.deviceReminderMinutes,
    )
}

private const val ANDROID_MANAGED_CALENDAR_IDENTIFIER_PREFIX = "android-calendar-row:v2:"

/**
 * Android 受管 Calendar row 的结构身份。
 *
 * 数字 row id 可能被 Provider 复用，因此必须同时携带创建时写入 `CAL_SYNC1` 的 lowercase UUID incarnation。
 * 该值只属于设备侧受管投影，不会上传 Schedule 后端。
 */
data class AndroidManagedCalendarIdentifier(
  val calendarRowId: Long,
  val incarnation: String,
) {
  init {
    require(calendarRowId > 0) { "Android managed calendar row id must be positive" }
    require(isCanonicalAndroidManagedCalendarIncarnation(incarnation)) {
      "Android managed calendar incarnation must be a canonical lowercase UUID"
    }
  }
}

/** UUID 仅接受 lowercase ASCII hex 与固定连字符位置，避免平台 parser 接受同值别名。 */
private fun isCanonicalAndroidManagedCalendarIncarnation(value: String): Boolean {
  if (value.length != 36) return false
  return value.indices.all { index ->
    when (index) {
      8, 13, 18, 23 -> value[index] == '-'
      else -> value[index] in '0'..'9' || value[index] in 'a'..'f'
    }
  }
}

/** Android 受管 Calendar row 与 incarnation 的严格文本 codec。 */
object AndroidManagedCalendarIdentifierCodec {
  /** 编码前校验 row id 与 incarnation，返回唯一 canonical 文本。 */
  fun encode(calendarRowId: Long, incarnation: String): String =
    encode(AndroidManagedCalendarIdentifier(calendarRowId, incarnation))

  /** 编码已校验的结构身份；结果只用于本地 Provider ownership 与 locator。 */
  fun encode(identifier: AndroidManagedCalendarIdentifier): String =
    "$ANDROID_MANAGED_CALENDAR_IDENTIFIER_PREFIX${identifier.calendarRowId}:${identifier.incarnation}"

  /**
   * 从不可信 Provider/cache 文本解析完整身份。
   *
   * 非 canonical 十进制、UUID 大小写别名、溢出或额外分段均返回 `null`，调用方必须 fail-closed。
   */
  fun decodeOrNull(identifier: String): AndroidManagedCalendarIdentifier? {
    if (!identifier.startsWith(ANDROID_MANAGED_CALENDAR_IDENTIFIER_PREFIX)) return null
    val payload = identifier.removePrefix(ANDROID_MANAGED_CALENDAR_IDENTIFIER_PREFIX)
    val separator = payload.indexOf(':')
    if (separator <= 0 || separator != payload.lastIndexOf(':')) return null
    val decimal = payload.substring(0, separator)
    if (decimal.isEmpty() || decimal.first() !in '1'..'9' || decimal.any { it !in '0'..'9' }) return null
    val calendarRowId = decimal.toLongOrNull() ?: return null
    val decoded = runCatching {
      AndroidManagedCalendarIdentifier(calendarRowId, payload.substring(separator + 1))
    }.getOrNull() ?: return null
    return decoded.takeIf { encode(it) == identifier }
  }
}
