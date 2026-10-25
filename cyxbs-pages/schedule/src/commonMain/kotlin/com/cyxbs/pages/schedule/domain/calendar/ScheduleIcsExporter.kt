package com.cyxbs.pages.schedule.domain.calendar

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.repository.ScheduleSnapshot
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * 一次 ICS 导出的完整结果。
 *
 * [scheduleCount] 按用户创建的日程计数，单次调整不会重复计入；[adjustmentCount] 单独说明文件内额外生成的
 * occurrence 组件数量。[skippedScheduleCount] 包含未设置日期和已完成的非重复清单等当前不可投影项。
 */
data class ScheduleIcsDocument(
  val content: String,
  val scheduleCount: Int,
  val adjustmentCount: Int,
  val skippedScheduleCount: Int,
)

/**
 * 将当前 Schedule 快照导出为 RFC 5545 VCALENDAR 文本。
 *
 * 该导出复用 [ScheduleCalendarProjectionFactory]，因此与 Android/iOS 系统日历保持相同的完成态、提醒、重复规则
 * 和单次调整语义。导出是一次性文件快照，不包含账号、token、远端版本或本地 pending 等同步信息。
 */
object ScheduleIcsExporter {
  private val exportScope = CalendarExportScope("ics-export")

  /**
   * 生成一份可导入主流日历应用的 ICS 文本。
   *
   * @param snapshot 当前账号已经发布的仓库快照。
   * @param generatedAt 文件生成时刻，只用于各 VEVENT 的 DTSTAMP。
   */
  fun export(snapshot: ScheduleSnapshot, generatedAt: Instant): ScheduleIcsDocument {
    val projection = ScheduleCalendarProjectionFactory.project(
      source = ScheduleCalendarSource(
        schedules = snapshot.schedules,
        occurrenceAdjustments = snapshot.occurrenceAdjustments,
      ),
      scope = exportScope,
      capabilities = setOf(ScheduleCalendarProjectionCapability.NATIVE_OCCURRENCE_EXCEPTIONS),
    )
    check(projection.unsupported.isEmpty()) {
      "ICS exporter does not support schedules: ${projection.unsupported.map { it.scheduleId }}"
    }
    val stamp = generatedAt.toIcsUtcDateTime()
    val lines = buildList {
      add("BEGIN:VCALENDAR")
      add("PRODID:-//Redrock Team//Cyxbs Schedule//CN")
      add("VERSION:2.0")
      add("CALSCALE:GREGORIAN")
      add("METHOD:PUBLISH")
      add("X-WR-CALNAME:${escapeText("掌邮日程")}")
      projection.events.forEach { event ->
        addAll(event.toIcsLines(stamp))
        event.nativeOccurrenceExceptions.forEach { adjustment ->
          addAll(adjustment.toIcsLines(stamp))
        }
      }
      add("END:VCALENDAR")
    }
    val content = lines
      .flatMap(::foldContentLine)
      .joinToString(separator = "\r\n", postfix = "\r\n")
    val adjustmentCount = projection.events.sumOf { it.nativeOccurrenceExceptions.size }
    return ScheduleIcsDocument(
      content = content,
      scheduleCount = projection.events.size,
      adjustmentCount = adjustmentCount,
      skippedScheduleCount = snapshot.schedules.size - projection.events.size,
    )
  }

  /** 使用设备当前日期生成便于识别且不会包含账号信息的默认文件名。 */
  fun suggestedFileName(generatedAt: Instant, timeZone: TimeZone): String {
    val date = generatedAt.toLocalDateTime(timeZone).date
    return "掌邮日程-${date.year.toString().padStart(4, '0')}" +
      "${date.month.number.toString().padStart(2, '0')}" +
      "${date.day.toString().padStart(2, '0')}.ics"
  }

  /** 系列 master 与普通事件共用同一字段顺序，方便导入器和测试稳定比较。 */
  private fun CalendarEventProjection.toIcsLines(stamp: String): List<String> = buildList {
    add("BEGIN:VEVENT")
    add("UID:${icsUid(id)}")
    add("DTSTAMP:$stamp")
    add("SUMMARY:${escapeText(title)}")
    if (description.isNotEmpty()) add("DESCRIPTION:${escapeText(description)}")
    addAll(timing.toIcsTimingLines())
    recurrenceRule?.let { add("RRULE:$it") }
    add("TRANSP:OPAQUE")
    deviceReminderMinutes.forEach { minutes -> addAll(alarmLines(title, minutes)) }
    add("END:VEVENT")
  }

  /**
   * 单次调整与 master 使用相同 UID，并用原始发生身份填写 RECURRENCE-ID。
   *
   * 被移动后的时间只写入 DTSTART/DTEND，不能反向覆盖 RECURRENCE-ID；否则日历应用会把它视为另一条实例。
   */
  private fun CalendarOccurrenceExceptionProjection.toIcsLines(stamp: String): List<String> = buildList {
    add("BEGIN:VEVENT")
    add("UID:${icsUid(id)}")
    add("RECURRENCE-ID${requireNotNull(id.recurrenceId).toIcsPropertySuffixAndValue()}")
    add("DTSTAMP:$stamp")
    add("SUMMARY:${escapeText(title)}")
    if (description.isNotEmpty()) add("DESCRIPTION:${escapeText(description)}")
    addAll(timing.toIcsTimingLines())
    if (operation == CalendarOccurrenceExceptionOperation.CANCEL) {
      add("STATUS:CANCELLED")
    } else {
      add("TRANSP:OPAQUE")
      deviceReminderMinutes.forEach { minutes -> addAll(alarmLines(title, minutes)) }
    }
    add("END:VEVENT")
  }

  /** UID 只使用跨设备稳定的 Schedule UUID，不暴露账号或本地数据库标识。 */
  private fun icsUid(id: CalendarProjectionId): String = "${id.scheduleId.value}@schedule.cyxbs"

  /** 按 DATE、带 TZID 的本地 DATE-TIME 和零时长时间点分别编码。 */
  private fun CalendarTiming.toIcsTimingLines(): List<String> = when (this) {
    is CalendarTiming.Timed -> listOf(
      "DTSTART;TZID=$timeZoneId:${start.toIcsLocalDateTime()}",
      "DTEND;TZID=$timeZoneId:${start.plusMinutes(durationMinutes).toIcsLocalDateTime()}",
    )
    is CalendarTiming.Deadline -> {
      val value = due.toIcsLocalDateTime()
      listOf(
        "DTSTART;TZID=$timeZoneId:$value",
        "DTEND;TZID=$timeZoneId:$value",
      )
    }
    is CalendarTiming.AllDay -> listOf(
      "DTSTART;VALUE=DATE:${startDate.toIcsDate()}",
      "DTEND;VALUE=DATE:${startDate.plusDays(durationDays).toIcsDate()}",
    )
  }

  /** RFC 5545 的负时长表示提前提醒；零分钟保留为准时提醒。 */
  private fun alarmLines(title: String, minutesBefore: Int): List<String> {
    require(minutesBefore >= 0) { "Reminder minutes must not be negative" }
    val trigger = if (minutesBefore == 0) "PT0M" else "-PT${minutesBefore}M"
    return listOf(
      "BEGIN:VALARM",
      "TRIGGER:$trigger",
      "ACTION:DISPLAY",
      "DESCRIPTION:${escapeText(title)}",
      "END:VALARM",
    )
  }

  /** 原始全天 occurrence 使用 DATE；定时 occurrence 必须携带自己的 IANA 时区。 */
  private fun RecurrenceId.toIcsPropertySuffixAndValue(): String {
    return if (allDay) {
      require(timeZoneId == null) { "All-day recurrence identity must not contain a time zone" }
      ";VALUE=DATE:${originalDateTime.date.toIcsDate()}"
    } else {
      val zoneId = requireNotNull(timeZoneId) { "Timed recurrence identity requires a time zone" }
      ";TZID=$zoneId:${originalDateTime.toIcsLocalDateTime()}"
    }
  }

  /** 文本值先统一换行，再转义 RFC 5545 规定的反斜杠、分号、逗号与换行。 */
  private fun escapeText(value: String): String = value
    .replace("\r\n", "\n")
    .replace('\r', '\n')
    .replace("\\", "\\\\")
    .replace(";", "\\;")
    .replace(",", "\\,")
    .replace("\n", "\\n")

  /**
   * 将 content line 折叠到最多 75 个 UTF-8 octet。
   *
   * 后续行以一个空格开头，因此正文最多占 74 byte；切点会退到 UTF-8 code point 边界，避免中文或 emoji 被截断。
   */
  private fun foldContentLine(line: String): List<String> {
    val bytes = line.encodeToByteArray()
    if (bytes.size <= MAX_CONTENT_LINE_BYTES) return listOf(line)
    val result = mutableListOf<String>()
    var offset = 0
    var first = true
    while (offset < bytes.size) {
      val contentLimit = if (first) MAX_CONTENT_LINE_BYTES else MAX_CONTENT_LINE_BYTES - 1
      var end = minOf(offset + contentLimit, bytes.size)
      while (end < bytes.size && end > offset &&
        (bytes[end].toInt() and UTF8_CONTINUATION_MASK) == UTF8_CONTINUATION_PREFIX
      ) {
        end -= 1
      }
      check(end > offset) { "Unable to fold UTF-8 content line" }
      val content = bytes.copyOfRange(offset, end).decodeToString()
      result += if (first) content else " $content"
      offset = end
      first = false
    }
    return result
  }

  private fun MinuteTimeDate.toIcsLocalDateTime(): String =
    "${date.toIcsDate()}T${time.hour.toString().padStart(2, '0')}" +
      "${time.minute.toString().padStart(2, '0')}00"

  private fun Date.toIcsDate(): String =
    year.toString().padStart(4, '0') +
      monthNumber.toString().padStart(2, '0') +
      dayOfMonth.toString().padStart(2, '0')

  private fun Instant.toIcsUtcDateTime(): String {
    val utc = toLocalDateTime(TimeZone.UTC)
    return utc.date.year.toString().padStart(4, '0') +
      utc.date.month.number.toString().padStart(2, '0') +
      utc.date.day.toString().padStart(2, '0') +
      "T${utc.hour.toString().padStart(2, '0')}" +
      utc.minute.toString().padStart(2, '0') +
      utc.second.toString().padStart(2, '0') +
      "Z"
  }

  private const val MAX_CONTENT_LINE_BYTES = 75
  private const val UTF8_CONTINUATION_MASK = 0xC0
  private const val UTF8_CONTINUATION_PREFIX = 0x80
}
