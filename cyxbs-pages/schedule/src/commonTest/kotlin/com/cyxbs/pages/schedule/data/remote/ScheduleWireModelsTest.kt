package com.cyxbs.pages.schedule.data.remote

import com.cyxbs.components.config.serializable.defaultJson
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.serialization.json.jsonObject

/** 校验网络共用 JSON 配置下，必填 null 与可选字段省略的 wire 差异。 */
class ScheduleWireModelsTest {

  /** 必填 nullable 原子必须显式编码 data=null，避免服务端把“清空”误判成字段缺失。 */
  @Test
  fun requiredNullableAtomicDataIsEncodedAsNull() {
    val categoryJson = defaultJson.encodeToString(
      CategoryInput(
        localId = "019d0000-0000-7000-8000-000000000001",
        version = 0uL,
        name = AtomicField("分类", 1),
        color = AtomicField(null, 1),
        sortOrder = AtomicField(0, 1),
      ),
    )
    val scheduleJson = defaultJson.encodeToString(
      ScheduleInput(
        id = "schedule-1",
        version = 0uL,
        kind = ScheduleKind.AFFAIR,
        title = AtomicField("事务", 1),
        description = AtomicField("", 1),
        categoryId = AtomicField(null, 1),
        timing = AtomicField(
          TimingInput(TimingKind.TIMED, startAt = 1_000, endAt = 2_000),
          1,
        ),
        recurrence = AtomicField(null, 1),
        reminder = AtomicField(null, 1),
        todoState = AtomicField(null, 1),
        linkedToCourse = AtomicField(true, 1),
      ),
    )

    assertContains(categoryJson, "\"color\":{\"data\":null")
    assertContains(scheduleJson, "\"categoryId\":{\"data\":null")
    assertContains(scheduleJson, "\"recurrence\":{\"data\":null")
    assertContains(scheduleJson, "\"reminder\":{\"data\":null")
    assertContains(scheduleJson, "\"todoState\":{\"data\":null")
  }

  /** 联合类型和 Patch 的可选成员为 null 时必须省略，不能混同于可显式清空的 AtomicField.data。 */
  @Test
  fun optionalNullableMembersAreOmitted() {
    val timingJson = defaultJson.encodeToString(TimingInput(TimingKind.UNSCHEDULED))
    val recurrenceJson = defaultJson.encodeToString(
      RecurrenceInput(
        frequency = RecurrenceFrequency.DAILY,
        interval = 1,
        anchorDate = 0,
        weekdays = emptyList(),
        monthDays = emptyList(),
        months = emptyList(),
      ),
    )
    val patchJson = defaultJson.encodeToString(FieldPatch<String>(PatchMode.INHERIT))

    assertFalse("startAt" in timingJson)
    assertFalse("endAt" in timingJson)
    assertFalse("dueAt" in timingJson)
    assertFalse("count" in recurrenceJson)
    assertFalse("untilDate" in recurrenceJson)
    assertFalse("value" in patchJson)
  }

  /** 首次创建单次调整上传临时 localId、逻辑槽和业务原子，不沿用旧 occurrenceDate 字段。 */
  @Test
  fun occurrenceCreateUsesCurrentContractFieldNames() {
    val stamp = 100L
    val json = defaultJson.encodeToString(
      OccurrenceAdjustmentInput(
        localId = "019d0000-0000-7000-8000-000000000002",
        scheduleId = "019d0000-0000-7000-8000-000000000001",
        originalOccurrenceDate = 200,
        version = 0uL,
        status = AtomicField(OccurrenceStatus.ACTIVE, stamp),
        date = AtomicField(FieldPatch(PatchMode.INHERIT), stamp),
        time = AtomicField(FieldPatch(PatchMode.INHERIT), stamp),
        title = AtomicField(FieldPatch(PatchMode.INHERIT), stamp),
        description = AtomicField(FieldPatch(PatchMode.INHERIT), stamp),
        categoryId = AtomicField(FieldPatch(PatchMode.INHERIT), stamp),
        reminder = AtomicField(FieldPatch(PatchMode.INHERIT), stamp),
      ),
    )
    val keys = defaultJson.parseToJsonElement(json).jsonObject.keys

    assertEquals(
      setOf(
        "localId",
        "scheduleId",
        "originalOccurrenceDate",
        "version",
        "status",
        "date",
        "time",
        "title",
        "description",
        "categoryId",
        "reminder",
      ),
      keys,
    )
  }

  /** 物理删除协议只发送资源 ID，响应也不再携带 tombstone 或删除时间。 */
  @Test
  fun physicalDeleteContainsOnlyIdentity() {
    val requestJson = defaultJson.encodeToString(
      DeleteResource(id = 12L),
    )
    val resultJson = defaultJson.encodeToString(
      DeleteResult<Long, OccurrenceAdjustmentInput>(
        id = 12L,
        result = MutationResultCode.SUCCESS,
      ),
    )

    assertEquals(setOf("id"), defaultJson.parseToJsonElement(requestJson).jsonObject.keys)
    assertEquals(
      setOf("id", "result"),
      defaultJson.parseToJsonElement(resultJson).jsonObject.keys,
    )
    assertFalse("tombstone" in resultJson)
    assertFalse("deletedAt" in resultJson)
  }
}
