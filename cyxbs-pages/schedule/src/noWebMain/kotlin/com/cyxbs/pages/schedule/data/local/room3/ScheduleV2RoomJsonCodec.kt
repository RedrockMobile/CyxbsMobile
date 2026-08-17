package com.cyxbs.pages.schedule.data.local.room3

import com.cyxbs.pages.schedule.data.remote.v3.CategoryCurrent
import com.cyxbs.pages.schedule.data.remote.v3.CategoryInput
import com.cyxbs.pages.schedule.data.remote.v3.CompletionStatus
import com.cyxbs.pages.schedule.data.remote.v3.FieldPatch
import com.cyxbs.pages.schedule.data.remote.v3.OccurrenceOverrideCurrent
import com.cyxbs.pages.schedule.data.remote.v3.OccurrenceOverrideInput
import com.cyxbs.pages.schedule.data.remote.v3.PatchMode
import com.cyxbs.pages.schedule.data.remote.v3.RecurrenceFrequency
import com.cyxbs.pages.schedule.data.remote.v3.RecurrenceInput
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleCurrent
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleInput
import com.cyxbs.pages.schedule.data.remote.v3.TimingInput
import com.cyxbs.pages.schedule.data.remote.v3.TimingKind
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Room state 表的 typed JSON 转换器。
 *
 * 网络请求由 Ktorfit 负责，本对象只服务于 Room 的 String 列：写入完整 typed snapshot，读取时拒绝未知字段并校验
 * 会影响状态投影的基本不变量。它不扫描 JSON 原文，也不维护另一套 wire 协议。
 */
internal object ScheduleV2RoomJsonCodec {
  private val json = Json {
    ignoreUnknownKeys = false
    explicitNulls = true
    encodeDefaults = true
  }

  /** 编码 Category pending snapshot。 */
  fun encodeCategoryInput(value: CategoryInput): String =
    encode(CategoryInput.serializer(), value.also { it.validateForRoom() })

  /** 解码并校验 Category pending snapshot。 */
  fun decodeCategoryInput(raw: String): CategoryInput =
    decode(raw, CategoryInput.serializer(), "CategoryInput") { it.validateForRoom() }

  /** 编码已确认的 Category remote snapshot。 */
  fun encodeCategoryCurrent(value: CategoryCurrent): String {
    value.resource.validateForRoom()
    require(value.resource.version > 0uL) { "remote CategoryCurrent requires version>0" }
    return encode(CategoryCurrent.serializer(), value)
  }

  /** 解码已确认的 Category remote snapshot。 */
  fun decodeCategoryCurrent(raw: String): CategoryCurrent =
    decode(raw, CategoryCurrent.serializer(), "CategoryCurrent") {
      it.resource.validateForRoom()
      require(it.resource.version > 0uL) { "remote CategoryCurrent requires version>0" }
    }

  /** 编码 Schedule pending snapshot。 */
  fun encodeScheduleInput(value: ScheduleInput): String =
    encode(ScheduleInput.serializer(), value.also { it.validateForRoom() })

  /** 解码并校验 Schedule pending snapshot。 */
  fun decodeScheduleInput(raw: String): ScheduleInput =
    decode(raw, ScheduleInput.serializer(), "ScheduleInput") { it.validateForRoom() }

  /** 编码已确认的 Schedule remote snapshot。 */
  fun encodeScheduleCurrent(value: ScheduleCurrent): String {
    value.resource.validateForRoom()
    require(value.resource.version > 0uL) { "remote ScheduleCurrent requires version>0" }
    return encode(ScheduleCurrent.serializer(), value)
  }

  /** 解码已确认的 Schedule remote snapshot。 */
  fun decodeScheduleCurrent(raw: String): ScheduleCurrent =
    decode(raw, ScheduleCurrent.serializer(), "ScheduleCurrent") {
      it.resource.validateForRoom()
      require(it.resource.version > 0uL) { "remote ScheduleCurrent requires version>0" }
    }

  /** 编码 OccurrenceOverride pending snapshot。 */
  fun encodeOccurrenceOverrideInput(value: OccurrenceOverrideInput): String =
    encode(OccurrenceOverrideInput.serializer(), value.also { it.validateForRoom() })

  /** 解码并校验 OccurrenceOverride pending snapshot。 */
  fun decodeOccurrenceOverrideInput(raw: String): OccurrenceOverrideInput =
    decode(raw, OccurrenceOverrideInput.serializer(), "OccurrenceOverrideInput") { it.validateForRoom() }

  /** 编码已确认的 OccurrenceOverride remote snapshot。 */
  fun encodeOccurrenceOverrideCurrent(value: OccurrenceOverrideCurrent): String {
    value.resource.validateForRoom()
    require(value.resource.version > 0uL) { "remote OccurrenceOverrideCurrent requires version>0" }
    return encode(OccurrenceOverrideCurrent.serializer(), value)
  }

  /** 解码已确认的 OccurrenceOverride remote snapshot。 */
  fun decodeOccurrenceOverrideCurrent(raw: String): OccurrenceOverrideCurrent =
    decode(raw, OccurrenceOverrideCurrent.serializer(), "OccurrenceOverrideCurrent") {
      it.resource.validateForRoom()
      require(it.resource.version > 0uL) { "remote OccurrenceOverrideCurrent requires version>0" }
    }

  private fun <T> encode(serializer: KSerializer<T>, value: T): String =
    json.encodeToString(serializer, value)

  private fun <T> decode(
    raw: String,
    serializer: KSerializer<T>,
    label: String,
    validate: (T) -> Unit,
  ): T {
    val value = try {
      json.decodeFromString(serializer, raw)
    } catch (failure: SerializationException) {
      throw IllegalArgumentException("invalid Room $label JSON", failure)
    }
    validate(value)
    return value
  }
}

/** Room 中 Category snapshot 的最低可恢复约束。 */
private fun CategoryInput.validateForRoom() {
  require(id.validId() && name.data.isNotBlank() && (color.data == null || color.data.isNotBlank()))
}

/** Room 中 Schedule snapshot 的最低可恢复约束。 */
private fun ScheduleInput.validateForRoom() {
  require(id.validId() && categoryId.data.validId() && reminders.data.all { it.minutesBefore >= 0 })
  timing.data.validateForRoom()
  recurrence.data?.validateForRoom()
  require(recurrence.data == null || (timing.data.kind != TimingKind.UNSCHEDULED && completion.data == CompletionStatus.OPEN))
}

/** Room 中 occurrence override snapshot 的最低可恢复约束。 */
private fun OccurrenceOverrideInput.validateForRoom() {
  require(scheduleId.validId() && occurrenceDate.isDateSlot())
  title.data.validateForRoom()
  description.data.validateForRoom()
  reminders.data.validateForRoom()
  require(reminders.data.value?.all { it.minutesBefore >= 0 } != false)
}

private fun FieldPatch<*>.validateForRoom() {
  require((mode == PatchMode.REPLACE) == (value != null)) { "FieldPatch mode/value mismatch" }
}

private fun TimingInput.validateForRoom() {
  when (kind) {
    TimingKind.TIMED, TimingKind.ALL_DAY -> {
      require(startAt != null && endAt != null && startAt < endAt && dueAt == null)
      if (kind == TimingKind.ALL_DAY) require(startAt.isDateSlot() && endAt.isDateSlot())
    }
    TimingKind.DEADLINE -> require(dueAt != null && startAt == null && endAt == null)
    TimingKind.UNSCHEDULED -> require(startAt == null && endAt == null && dueAt == null)
  }
}

private fun RecurrenceInput.validateForRoom() {
  require(interval > 0 && anchorDate.isDateSlot())
  require(!(count != null && untilDate != null))
  require(count == null || count > 0)
  require(untilDate == null || untilDate.isDateSlot() && untilDate >= anchorDate)
  require(
    (frequency == RecurrenceFrequency.DAILY && weekdays.isEmpty()) ||
      (frequency == RecurrenceFrequency.WEEKLY && weekdays.isNotEmpty() && weekdays.distinct().size == weekdays.size),
  )
}

private fun String.validId(): Boolean = isNotEmpty() && trim() == this

private fun Long.isDateSlot(): Boolean = this % 86_400_000L == 0L
