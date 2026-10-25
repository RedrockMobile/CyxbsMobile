package com.cyxbs.pages.schedule.data.local.room3

import com.cyxbs.components.utils.extensions.log
import com.cyxbs.pages.schedule.data.remote.SCHEDULE_DETAIL_LOG_TAG
import com.cyxbs.pages.schedule.data.remote.diagnosticSummary
import com.cyxbs.pages.schedule.data.repository.toWire
import kotlinx.datetime.TimeZone

/**
 * 逐条输出 Room 中当前可恢复的日程。
 *
 * pending DELETE 的 effective resource 为 null，此时回退到 remoteSnapshot 仅用于诊断标题和时间；该回退不会参与业务合并。
 */
internal fun ScheduleCommonAccountState.logScheduleState(
  label: String,
  timeZone: TimeZone,
  scheduleIds: Set<String>? = null,
) {
  val categoryByLocalId = categories
    .mapNotNull { it.effectiveResource() ?: it.remoteSnapshot?.resource }
    .associateBy { it.identity.id }
  val selectedSchedules = schedules.filter { scheduleIds == null || it.identity.id in scheduleIds }
  if (selectedSchedules.isEmpty()) {
    log(SCHEDULE_DETAIL_LOG_TAG, "$label: EMPTY")
    return
  }
  selectedSchedules.forEachIndexed { index, state ->
    val resource = state.effectiveResource() ?: state.remoteSnapshot?.resource
    val pending = state.pending?.let { it::class.simpleName } ?: "NONE"
    log(
      SCHEDULE_DETAIL_LOG_TAG,
      "$label[$index]: pending=$pending, " +
        (resource?.toWire(categoryByLocalId::get)?.diagnosticSummary(timeZone)
          ?: "id=${state.identity.id}, resource=DELETED"),
    )
  }
}
