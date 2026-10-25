package com.cyxbs.pages.schedule.data.local.room3

import androidx.room3.ColumnTypeConverter
import com.cyxbs.pages.schedule.data.remote.CategoryInput
import com.cyxbs.pages.schedule.data.remote.OccurrenceAdjustmentInput
import com.cyxbs.pages.schedule.data.remote.ScheduleInput

/**
 * 日程双快照的 Room JSON 转换器。
 *
 * 远端快照和待提交快照使用同一份协议类型；两者由状态行所在列区分，不再增加 Current、Tombstone 等包装。
 */
internal class ScheduleRoomConverters {
  /** 在 String 列与分类完整快照之间转换。 */
  @ColumnTypeConverter
  fun categoryInputToJson(value: CategoryInput?): String? =
    value?.let(ScheduleRoomJsonCodec::encodeCategoryInput)

  @ColumnTypeConverter
  fun categoryInputFromJson(value: String?): CategoryInput? =
    value?.let(ScheduleRoomJsonCodec::decodeCategoryInput)

  /** 在 String 列与日程完整快照之间转换。 */
  @ColumnTypeConverter
  fun scheduleInputToJson(value: ScheduleInput?): String? =
    value?.let(ScheduleRoomJsonCodec::encodeScheduleInput)

  @ColumnTypeConverter
  fun scheduleInputFromJson(value: String?): ScheduleInput? =
    value?.let(ScheduleRoomJsonCodec::decodeScheduleInput)

  /** 在 String 列与单次调整完整快照之间转换。 */
  @ColumnTypeConverter
  fun occurrenceAdjustmentInputToJson(value: OccurrenceAdjustmentInput?): String? =
    value?.let(ScheduleRoomJsonCodec::encodeOccurrenceAdjustmentInput)

  @ColumnTypeConverter
  fun occurrenceAdjustmentInputFromJson(value: String?): OccurrenceAdjustmentInput? =
    value?.let(ScheduleRoomJsonCodec::decodeOccurrenceAdjustmentInput)
}
