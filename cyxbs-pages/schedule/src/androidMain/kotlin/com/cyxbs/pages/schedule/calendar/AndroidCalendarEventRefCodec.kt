package com.cyxbs.pages.schedule.calendar

import com.cyxbs.pages.schedule.domain.calendar.PlatformCalendarEventRef

/**
 * Android Calendar Provider row ID 与 common opaque 引用的严格适配器。
 *
 * common 不依赖 `Long`；只有 Android gateway 可以解析该值。拒绝零、符号、空白、前导零和溢出，防止损坏或
 * 来自其他平台的引用被近似成某个 Provider row 并触发误更新/误删除。
 */
internal object AndroidCalendarEventRefCodec {
  fun encode(eventId: Long): PlatformCalendarEventRef {
    require(eventId > 0) { "Calendar Provider event ID must be positive" }
    return PlatformCalendarEventRef(eventId.toString())
  }

  fun decodeOrNull(ref: PlatformCalendarEventRef): Long? {
    val value = ref.value
    if (!CANONICAL_POSITIVE_DECIMAL.matches(value)) return null
    return value.toLongOrNull()?.takeIf { it > 0 }
  }

  private val CANONICAL_POSITIVE_DECIMAL = Regex("^[1-9][0-9]*$")
}
