package com.cyxbs.pages.schedule.data.remote

import com.cyxbs.components.utils.extensions.log
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Schedule 详细业务日志。
 *
 * 该日志会包含用户填写的分类名与日程标题，仅用于联调增删改查与服务端合并结果；不记录描述、提醒文案、
 * token、header 或完整请求体。分类配色是受控的业务配置 JSON，可以逐字段往返核对。
 */
internal const val SCHEDULE_DETAIL_LOG_TAG = "ScheduleDetail"

/** 输出完整 Sync 中客户端已持有的 identity，以及三类资源的待提交操作。 */
internal fun SyncRequest.logScheduleRequest(timeZone: TimeZone) {
  log(SCHEDULE_DETAIL_LOG_TAG, "REQUEST SYNC")
  logItems(
    label = "REQUEST SYNC category confirmed",
    items = categories.confirmed.map { "id=${it.id}, version=${it.version}" },
  )
  logItems(
    label = "REQUEST SYNC category upsert",
    items = categories.upserts.map { it.diagnosticSummary() },
  )
  logItems(
    label = "REQUEST SYNC category delete",
    items = categories.deletes.map { "id=${it.id}" },
  )
  logItems(
    label = "REQUEST SYNC confirmed",
    items = schedules.confirmed.map { "id=${it.id}, version=${it.version}" },
  )
  logItems(
    label = "REQUEST SYNC upsert",
    items = schedules.upserts.map { it.diagnosticSummary(timeZone) },
  )
  logItems(
    label = "REQUEST SYNC delete",
    items = schedules.deletes.map { "id=${it.id}" },
  )
  logItems(
    label = "REQUEST SYNC adjustment confirmed",
    items = occurrenceAdjustments.confirmed.map { "id=${it.id}, version=${it.version}" },
  )
  logItems(
    label = "REQUEST SYNC adjustment upsert",
    items = occurrenceAdjustments.upserts.map { it.diagnosticSummary() },
  )
  logItems(
    label = "REQUEST SYNC adjustment delete",
    items = occurrenceAdjustments.deletes.map { "id=${it.id}" },
  )
}

/** 输出日常请求中三类资源实际新增、修改及删除的内容。 */
internal fun MutationRequest.logScheduleRequest(operation: String, timeZone: TimeZone) {
  log(SCHEDULE_DETAIL_LOG_TAG, operation)
  logItems(
    label = "$operation category upsert",
    items = categories.upserts.map { it.diagnosticSummary() },
  )
  logItems(
    label = "$operation category delete",
    items = categories.deletes.map { "id=${it.id}" },
  )
  logItems(
    label = "$operation upsert",
    items = schedules.upserts.map { it.diagnosticSummary(timeZone) },
  )
  logItems(
    label = "$operation delete",
    items = schedules.deletes.map { "id=${it.id}" },
  )
  logItems(
    label = "$operation adjustment upsert",
    items = occurrenceAdjustments.upserts.map { it.diagnosticSummary() },
  )
  logItems(
    label = "$operation adjustment delete",
    items = occurrenceAdjustments.deletes.map { "id=${it.id}" },
  )
}

/** 输出完整 Sync 返回的 inventory 核对、发现资源和 mutation 结果。 */
internal fun SyncResponse.logScheduleResponse(timeZone: TimeZone) {
  log(SCHEDULE_DETAIL_LOG_TAG, "RESPONSE SYNC")
  logItems(
    label = "RESPONSE SYNC category confirmedResult",
    items = categories.confirmedResults.map {
      "id=${it.id}, result=${it.result}, resource=${it.resource?.diagnosticSummary()}"
    },
  )
  logItems(
    label = "RESPONSE SYNC category discoveredResult",
    items = categories.discoveredResults.map { it.diagnosticSummary() },
  )
  logItems(
    label = "RESPONSE SYNC category upsertResult",
    items = categories.upsertResults.map {
      "result=${it.result}, reason=${it.reason}, info=${it.info}, " +
        "resource=${it.resource?.diagnosticSummary()}"
    },
  )
  logItems(
    label = "RESPONSE SYNC category deleteResult",
    items = categories.deleteResults.map {
      "id=${it.id}, result=${it.result}, reason=${it.reason}, info=${it.info}, " +
        "resource=${it.resource?.diagnosticSummary()}"
    },
  )
  logItems(
    label = "RESPONSE SYNC confirmedResult",
    items = schedules.confirmedResults.map {
      "id=${it.id}, result=${it.result}, " +
        "resource=${it.resource?.diagnosticSummary(timeZone)}"
    },
  )
  logItems(
    label = "RESPONSE SYNC discoveredResult",
    items = schedules.discoveredResults.map { it.diagnosticSummary(timeZone) },
  )
  logItems(
    label = "RESPONSE SYNC upsertResult",
    items = schedules.upsertResults.map { result ->
      "result=${result.result}, reason=${result.reason}, info=${result.info}, " +
        "resource=${result.resource?.diagnosticSummary(timeZone)}"
    },
  )
  logItems(
    label = "RESPONSE SYNC deleteResult",
    items = schedules.deleteResults.map { result ->
      "id=${result.id}, result=${result.result}, reason=${result.reason}, info=${result.info}, " +
        "resource=${result.resource?.diagnosticSummary(timeZone)}"
    },
  )
  logItems(
    label = "RESPONSE SYNC adjustment confirmedResult",
    items = occurrenceAdjustments.confirmedResults.map {
      "id=${it.id}, result=${it.result}, resource=${it.resource?.diagnosticSummary()}"
    },
  )
  logItems(
    label = "RESPONSE SYNC adjustment discoveredResult",
    items = occurrenceAdjustments.discoveredResults.map { it.diagnosticSummary() },
  )
  logItems(
    label = "RESPONSE SYNC adjustment upsertResult",
    items = occurrenceAdjustments.upsertResults.map {
      "result=${it.result}, reason=${it.reason}, info=${it.info}, " +
        "resource=${it.resource?.diagnosticSummary()}"
    },
  )
  logItems(
    label = "RESPONSE SYNC adjustment deleteResult",
    items = occurrenceAdjustments.deleteResults.map {
      "id=${it.id}, result=${it.result}, reason=${it.reason}, info=${it.info}, " +
        "resource=${it.resource?.diagnosticSummary()}"
    },
  )
}

/** 输出日常逐资源结果和服务端合并后的 canonical 日程。 */
internal fun MutationResponse.logScheduleResponse(timeZone: TimeZone) {
  log(SCHEDULE_DETAIL_LOG_TAG, "RESPONSE MUTATION")
  logItems(
    label = "RESPONSE MUTATION category upsertResult",
    items = categories.upsertResults.map {
      "result=${it.result}, reason=${it.reason}, info=${it.info}, " +
        "resource=${it.resource?.diagnosticSummary()}"
    },
  )
  logItems(
    label = "RESPONSE MUTATION category deleteResult",
    items = categories.deleteResults.map {
      "id=${it.id}, result=${it.result}, reason=${it.reason}, info=${it.info}, " +
        "resource=${it.resource?.diagnosticSummary()}"
    },
  )
  logItems(
    label = "RESPONSE MUTATION upsertResult",
    items = schedules.upsertResults.map {
      "result=${it.result}, reason=${it.reason}, info=${it.info}, " +
        "resource=${it.resource?.diagnosticSummary(timeZone)}"
    },
  )
  logItems(
    label = "RESPONSE MUTATION deleteResult",
    items = schedules.deleteResults.map {
      "id=${it.id}, result=${it.result}, reason=${it.reason}, info=${it.info}, " +
        "resource=${it.resource?.diagnosticSummary(timeZone)}"
    },
  )
  logItems(
    label = "RESPONSE MUTATION adjustment upsertResult",
    items = occurrenceAdjustments.upsertResults.map {
      "result=${it.result}, reason=${it.reason}, info=${it.info}, " +
        "resource=${it.resource?.diagnosticSummary()}"
    },
  )
  logItems(
    label = "RESPONSE MUTATION adjustment deleteResult",
    items = occurrenceAdjustments.deleteResults.map {
      "id=${it.id}, result=${it.result}, reason=${it.reason}, info=${it.info}, " +
        "resource=${it.resource?.diagnosticSummary()}"
    },
  )
}

/** 将分类压缩为 identity、名称、排序与受控配色，便于核对本地到远端的完整往返。 */
private fun CategoryInput.diagnosticSummary(): String =
  "localId=$localId, id=$id, version=$version, name=${name.data.singleLine()}, " +
    "sortOrder=${sortOrder.data}, color=${color.data?.singleLine()}"

/** 将 wire 日程压缩为标题、日期时间与周期，不包含描述等额外用户内容。 */
internal fun ScheduleInput.diagnosticSummary(timeZone: TimeZone): String =
  "id=$id, version=$version, title=${title.data.singleLine()}, " +
    "timing=${timing.data.diagnosticSummary(timeZone)}, " +
    "recurrence=${recurrence.data.diagnosticSummary(timeZone)}"

/**
 * 将单次调整压缩为可安全记录的结构摘要。
 *
 * 日志只记录资源 identity、原始日期槽、版本、状态和各字段 Patch 模式；不记录标题、描述、
 * 分类值、提醒值等用户输入，既能定位合并路径，也不会把业务内容或凭证写入日志。
 */
private fun OccurrenceAdjustmentInput.diagnosticSummary(): String =
  "localId=$localId, id=$id, scheduleId=$scheduleId, " +
    "originalDate=${originalOccurrenceDate.localDateTime(TimeZone.UTC).date}, version=$version, " +
    "status=${status.data}, patches=" +
    "date:${date.data.mode},time:${time.data.mode},title:${title.data.mode}," +
    "description:${description.data.mode},category:${categoryId.data.mode}," +
    "reminder:${reminder.data.mode}"

/** 将 wire 时间联合值转换为设备时区下可读的日期时间。 */
private fun TimingInput.diagnosticSummary(timeZone: TimeZone): String = when (kind) {
  TimingKind.TIMED ->
    "TIMED ${startAt?.localDateTime(timeZone)}..${endAt?.localDateTime(timeZone)}"
  TimingKind.DEADLINE -> "DEADLINE ${dueAt?.localDateTime(timeZone)}"
  // 全天协议只携带 UTC 午夜日期槽，不能读取 TIMED 专用的 startAt/endAt。
  TimingKind.ALL_DAY -> "ALL_DAY ${date?.localDateTime(TimeZone.UTC)?.date}"
  TimingKind.UNSCHEDULED -> "UNSCHEDULED"
}

/** 输出 wire 重复频率、间隔、星期和终止条件。 */
private fun RecurrenceInput?.diagnosticSummary(timeZone: TimeZone): String = when (this) {
  null -> "NONE"
  else -> buildString {
    append(frequency)
    append("(interval=")
    append(interval)
    append(", anchor=")
    append(anchorDate.localDateTime(timeZone).date)
    if (weekdays.isNotEmpty()) append(", weekdays=$weekdays")
    if (count != null) append(", count=$count")
    if (untilDate != null) append(", until=${untilDate.localDateTime(timeZone).date}")
    append(')')
  }
}

/** 将毫秒时间戳转换成设备时区下的日期时间；nullable 字段由调用方保留 null 以便诊断。 */
private fun Long.localDateTime(timeZone: TimeZone): LocalDateTime =
  Instant.fromEpochMilliseconds(this).toLocalDateTime(timeZone)

/** 防止标题中的换行破坏一条日程对应一条日志的结构。 */
private fun String.singleLine(): String = replace('\n', ' ').replace('\r', ' ')

/** 列表为空时也输出 EMPTY，避免无法区分“未打印”和“确实没有返回”。 */
private fun logItems(label: String, items: List<String>) {
  if (items.isEmpty()) {
    log(SCHEDULE_DETAIL_LOG_TAG, "$label: EMPTY")
  } else {
    items.forEachIndexed { index, item ->
      log(SCHEDULE_DETAIL_LOG_TAG, "$label[$index]: $item")
    }
  }
}
