package com.cyxbs.pages.schedule.recurrence

import com.cyxbs.components.config.time.Date

/**
 * RFC5545 RRULE 字符串 <-> [RRule] 的解析与序列化，并支持把整条 [Recurrence]
 * 序列化为含 RRULE/RDATE/EXDATE 行的文本（用于 .ics 导出与后端 opaque 存储）。
 *
 * 注意：commonMain 不可用 `String.format`，日期格式化一律用 padStart 手写。
 */
object RecurrenceParser {

  /** ISO 星期码，下标 +1 = [Date.dayOfWeekNumber]（周一=1）。 */
  private val DAY_CODES = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")

  /**
   * 解析 RRULE 字符串，如 "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE;UNTIL=20260101;COUNT=5"。
   * 允许带 "RRULE:" 前缀。UNTIL 接受 DATE 或 DATE-TIME 形式（只取日期部分）。
   */
  fun parse(rrule: String): RRule {
    val parts = rrule.trim().removePrefix("RRULE:").split(";").filter { it.isNotBlank() }
    var freq: Freq? = null
    var interval = 1
    var byDay = emptyList<Int>()
    var byMonthDay = emptyList<Int>()
    var byMonth = emptyList<Int>()
    var until: Date? = null
    var count: Int? = null
    for (p in parts) {
      val eq = p.indexOf('=')
      if (eq < 0) continue
      val key = p.substring(0, eq).trim().uppercase()
      val value = p.substring(eq + 1).trim()
      when (key) {
        "FREQ" -> freq = Freq.valueOf(value.uppercase())
        "INTERVAL" -> interval = value.toIntOrNull() ?: 1
        "BYDAY" -> byDay = value.split(",").mapNotNull { token ->
          // v1 不支持序号前缀（如 1MO），仅取末尾两位星期码
          val code = token.trim().takeLast(2).uppercase()
          DAY_CODES.indexOf(code).let { if (it >= 0) it + 1 else null }
        }
        "BYMONTHDAY" -> byMonthDay = value.split(",").mapNotNull { it.trim().toIntOrNull() }
        "BYMONTH" -> byMonth = value.split(",").mapNotNull { it.trim().toIntOrNull() }
        "UNTIL" -> until = parseDate(value)
        "COUNT" -> count = value.toIntOrNull()
      }
    }
    requireNotNull(freq) { "RRULE 缺少 FREQ: $rrule" }
    return RRule(freq, interval, byDay, byMonthDay, byMonth, until, count)
  }

  /** 序列化 [RRule] 为 RFC5545 字符串（BYDAY 升序归一，便于往返一致）。 */
  fun serialize(rule: RRule): String {
    val sb = StringBuilder("FREQ=").append(rule.freq.name)
    if (rule.interval != 1) sb.append(";INTERVAL=").append(rule.interval)
    if (rule.byDay.isNotEmpty()) {
      sb.append(";BYDAY=").append(rule.byDay.sorted().joinToString(",") { DAY_CODES[it - 1] })
    }
    if (rule.byMonthDay.isNotEmpty()) sb.append(";BYMONTHDAY=").append(rule.byMonthDay.joinToString(","))
    if (rule.byMonth.isNotEmpty()) sb.append(";BYMONTH=").append(rule.byMonth.joinToString(","))
    rule.until?.let { sb.append(";UNTIL=").append(fmtDate(it)) }
    rule.count?.let { sb.append(";COUNT=").append(it) }
    return sb.toString()
  }

  /** 把整条 [Recurrence] 序列化为多行（RRULE/RDATE/EXDATE），用于 .ics 导出。 */
  fun serializeFull(r: Recurrence): String {
    val lines = mutableListOf<String>()
    r.rrule?.let { lines.add("RRULE:" + serialize(it)) }
    if (r.rdate.isNotEmpty()) {
      lines.add("RDATE;VALUE=DATE:" + r.rdate.joinToString(",") { fmtDate(it) })
    }
    if (r.exdate.isNotEmpty()) {
      lines.add("EXDATE;VALUE=DATE:" + r.exdate.joinToString(",") { fmtDate(it) })
    }
    return lines.joinToString("\n")
  }

  /** 解析 "yyyyMMdd"（或 "yyyyMMddT..." 取前 8 位）为 [Date]。 */
  fun parseDate(value: String): Date {
    val digits = value.substringBefore('T').filter { it.isDigit() }
    require(digits.length >= 8) { "非法日期: $value" }
    return Date(
      digits.substring(0, 4).toInt(),
      digits.substring(4, 6).toInt(),
      digits.substring(6, 8).toInt(),
    )
  }

  /** [Date] -> "yyyyMMdd"。 */
  fun fmtDate(d: Date): String =
    d.year.toString().padStart(4, '0') +
      d.monthNumber.toString().padStart(2, '0') +
      d.dayOfMonth.toString().padStart(2, '0')
}
