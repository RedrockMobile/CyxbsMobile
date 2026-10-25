package com.cyxbs.pages.schedule.data.failure

import com.cyxbs.pages.schedule.data.remote.CategoryMutationRequest
import com.cyxbs.pages.schedule.data.remote.MutationRequest
import com.cyxbs.pages.schedule.data.remote.OccurrenceAdjustmentMutationRequest
import com.cyxbs.pages.schedule.data.remote.ScheduleMutationRequest
import com.cyxbs.pages.schedule.data.repository.testScheduleResource
import com.cyxbs.pages.schedule.data.repository.toWire
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.PropertiesSettings
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals

/** 使用 JVM 内存 Properties 验证失败记录 Settings 持久化与覆盖规则。 */
@OptIn(ExperimentalSettingsImplementation::class)
class SettingsScheduleFailureRecordStoreDesktopTest {

  /** 同一日程连续失败只保留最新记录，源数据、原因和时间必须一起更新。 */
  @Test
  fun repeatedFailureReplacesPreviousRecordForSameSchedule() {
    val settings = PropertiesSettings(Properties())
    val store = SettingsScheduleFailureRecordStore { settings }
    val first = failureRecord(title = "第一次提交", failedAt = 100, reason = "OLD_REASON")
    val latest = failureRecord(title = "修正后再次提交", failedAt = 200, reason = "LATEST_REASON")

    store.record("account", listOf(first))
    store.record("account", listOf(latest))

    val records = store.observe("account").value
    assertEquals(1, records.size)
    assertEquals(200, records.single().failedAt)
    assertEquals("LATEST_REASON", records.single().reasonCode)
    assertEquals("修正后再次提交", records.single().sourceSchedule.title.data)

    val restored = SettingsScheduleFailureRecordStore { settings }.observe("account").value.single()
    assertEquals(200, restored.failedAt)
    assertEquals("LATEST_REASON", restored.reasonCode)
    assertEquals("修正后再次提交", restored.sourceSchedule.title.data)
  }

  /** 构造只包含业务字段的失败记录；网络凭证和 Header 在类型层没有存储入口。 */
  private fun failureRecord(title: String, failedAt: Long, reason: String): ScheduleFailureRecord {
    val source = testScheduleResource(version = 0, title = title).toWire { null }
    return ScheduleFailureRecord(
      scheduleId = source.id,
      failedAt = failedAt,
      operation = ScheduleFailureOperation.CREATE,
      reasonCode = reason,
      message = "可安全展示的业务原因",
      sourceSchedule = source,
      sourceRequest = MutationRequest(
        categories = CategoryMutationRequest(emptyList(), emptyList()),
        schedules = ScheduleMutationRequest(listOf(source), emptyList()),
        occurrenceAdjustments = OccurrenceAdjustmentMutationRequest(emptyList(), emptyList()),
      ),
    )
  }
}
