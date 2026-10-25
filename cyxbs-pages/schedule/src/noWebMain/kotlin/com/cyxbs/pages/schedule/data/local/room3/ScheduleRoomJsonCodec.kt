package com.cyxbs.pages.schedule.data.local.room3

import com.cyxbs.pages.schedule.data.remote.CategoryInput
import com.cyxbs.pages.schedule.data.remote.FieldPatch
import com.cyxbs.pages.schedule.data.remote.OccurrenceAdjustmentInput
import com.cyxbs.pages.schedule.data.remote.OccurrenceTimeInput
import com.cyxbs.pages.schedule.data.remote.OccurrenceTimeKind
import com.cyxbs.pages.schedule.data.remote.PatchMode
import com.cyxbs.pages.schedule.data.remote.RecurrenceFrequency
import com.cyxbs.pages.schedule.data.remote.RecurrenceInput
import com.cyxbs.pages.schedule.data.remote.ScheduleInput
import com.cyxbs.pages.schedule.data.remote.ScheduleKind
import com.cyxbs.pages.schedule.data.remote.TimingInput
import com.cyxbs.pages.schedule.data.remote.TimingKind
import com.cyxbs.pages.schedule.data.remote.TodoState
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Room 状态表的 typed JSON 边界。
 *
 * 网络和 Room 共用协议快照类型，但本对象只负责 String 列的编解码。读取时拒绝未知字段并检查会影响本地投影的
 * 基本约束，防止损坏数据进入同步状态机。
 */
internal object ScheduleRoomJsonCodec {
  private val json = Json {
    ignoreUnknownKeys = false
    explicitNulls = true
    encodeDefaults = true
  }

  /** 编解码分类完整快照。 */
  fun encodeCategoryInput(value: CategoryInput): String =
    encode(CategoryInput.serializer(), value.also { it.validateForRoom() })

  fun decodeCategoryInput(raw: String): CategoryInput =
    decode(raw, CategoryInput.serializer(), "CategoryInput") { it.validateForRoom() }

  /** 编解码日程完整快照。 */
  fun encodeScheduleInput(value: ScheduleInput): String =
    encode(ScheduleInput.serializer(), value.also { it.validateForRoom() })

  fun decodeScheduleInput(raw: String): ScheduleInput =
    decode(raw, ScheduleInput.serializer(), "ScheduleInput") { it.validateForRoom() }

  /** 编解码单次调整完整快照。 */
  fun encodeOccurrenceAdjustmentInput(value: OccurrenceAdjustmentInput): String =
    encode(OccurrenceAdjustmentInput.serializer(), value.also { it.validateForRoom() })

  fun decodeOccurrenceAdjustmentInput(raw: String): OccurrenceAdjustmentInput =
    decode(raw, OccurrenceAdjustmentInput.serializer(), "OccurrenceAdjustmentInput") {
      it.validateForRoom()
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

/** 分类创建态与远端态使用互斥的 ID 组合。 */
private fun CategoryInput.validateForRoom() {
  require(
    (id == null && localId.validLocalId() && version == 0uL) ||
        (id != null && id > 0 && localId == null && version > 0uL),
  ) { "category id/version shape is invalid" }
  require(name.data.isNotBlank())
  require(color.data == null || color.data.isNotBlank())
}

/** 日程快照的最低可恢复约束；UNSCHEDULED 只兼容旧清单。 */
private fun ScheduleInput.validateForRoom() {
  require(id.validLocalId() && title.data.isNotBlank())
  require(categoryLocalId == null || categoryId.data == null)
  require(categoryLocalId == null || categoryLocalId.validLocalId())
  require(categoryId.data == null || categoryId.data > 0)
  require((reminder.data?.minutesBefore ?: 0) >= 0)
  timing.data.validateForRoom()
  recurrence.data?.validateForRoom()
  if (timing.data.kind == TimingKind.UNSCHEDULED) {
    require(recurrence.data == null && reminder.data == null && !linkedToCourse.data)
  }
  when (kind) {
    ScheduleKind.TODO -> {
      require(todoState.data != null)
      require(recurrence.data == null || todoState.data != TodoState.COMPLETED)
    }

    ScheduleKind.AFFAIR -> {
      // 原生事务始终属于课表；关联清单后以 todoState 保存完成态，因此这里不能再强制为空。
      require(linkedToCourse.data)
      require(timing.data.kind == TimingKind.TIMED)
    }
  }
}

/** 单次调整创建态与远端态同样使用互斥 ID，并固定所属逻辑槽。 */
private fun OccurrenceAdjustmentInput.validateForRoom() {
  require(scheduleId.validLocalId() && originalOccurrenceDate.isDateSlot())
  require(
    (id == null && localId.validLocalId() && version == 0uL) ||
        (id != null && id > 0 && localId == null && version > 0uL),
  ) { "occurrence adjustment id/version shape is invalid" }
  date.data.validateForRoom()
  time.data.validateForRoom()
  title.data.validateForRoom()
  description.data.validateForRoom()
  reminder.data.validateForRoom()
  require(title.data.mode != PatchMode.CLEAR) { "occurrence title cannot be cleared" }
  require(title.data.value?.isNotBlank() != false)
  require(date.data.value?.isDateSlot() != false)
  time.data.value?.validateForRoom()
  require((reminder.data.value?.minutesBefore ?: 0) >= 0)
  if (categoryLocalId != null) {
    require(categoryLocalId.validLocalId())
    require(categoryId.data.mode == PatchMode.REPLACE && categoryId.data.value == null)
  } else {
    categoryId.data.validateForRoom()
    require(categoryId.data.value == null || categoryId.data.value > 0)
  }
}

/** 普通三态字段只有 REPLACE 携带值。 */
private fun FieldPatch<*>.validateForRoom() {
  require((mode == PatchMode.REPLACE) == (value != null)) { "FieldPatch mode/value mismatch" }
}

private fun OccurrenceTimeInput.validateForRoom() {
  when (kind) {
    OccurrenceTimeKind.TIME_RANGE -> require(
      startMinuteOfDay in 0 until 24 * 60 && durationMinutes != null && durationMinutes > 0 &&
          minuteOfDay == null,
    )
    OccurrenceTimeKind.TIME_POINT -> require(
      minuteOfDay in 0 until 24 * 60 && startMinuteOfDay == null && durationMinutes == null,
    )
    OccurrenceTimeKind.ALL_DAY -> require(
      startMinuteOfDay == null && durationMinutes == null && minuteOfDay == null,
    )
  }
}

private fun TimingInput.validateForRoom() {
  when (kind) {
    TimingKind.TIMED -> require(startAt != null && endAt != null && startAt < endAt && dueAt == null && date == null)
    TimingKind.ALL_DAY -> require(date != null && date.isDateSlot() && startAt == null && endAt == null && dueAt == null)
    TimingKind.DEADLINE -> require(dueAt != null && startAt == null && endAt == null && date == null)
    TimingKind.UNSCHEDULED -> require(startAt == null && endAt == null && dueAt == null && date == null)
  }
}

private fun RecurrenceInput.validateForRoom() {
  require(interval > 0 && anchorDate.isDateSlot())
  require(count == null || untilDate == null)
  require(count == null || count > 0)
  require(untilDate == null || untilDate.isDateSlot() && untilDate >= anchorDate)
  require(
    when (frequency) {
      RecurrenceFrequency.DAILY -> weekdays.isEmpty() && monthDays.isEmpty() && months.isEmpty()
      RecurrenceFrequency.WEEKLY -> weekdays.isNotEmpty() && weekdays.distinct().size == weekdays.size &&
          monthDays.isEmpty() && months.isEmpty()
      RecurrenceFrequency.MONTHLY -> weekdays.isEmpty() && monthDays.isNotEmpty() &&
          monthDays.distinct().size == monthDays.size && monthDays.all { it in 1..31 } && months.isEmpty()
      RecurrenceFrequency.YEARLY -> weekdays.isEmpty() && monthDays.isNotEmpty() &&
          monthDays.distinct().size == monthDays.size && monthDays.all { it in 1..31 } &&
          months.isNotEmpty() && months.distinct().size == months.size && months.all { it in 1..12 }
    },
  )
}

private fun String?.validLocalId(): Boolean = this != null && isNotBlank() && trim() == this
private fun Long.isDateSlot(): Boolean = this % 86_400_000L == 0L
