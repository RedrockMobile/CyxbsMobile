package com.cyxbs.pages.schedule.ui.category

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.domain.model.CategoryId
import com.cyxbs.pages.schedule.domain.model.FieldPatch
import com.cyxbs.pages.schedule.domain.model.OccurrencePatch
import com.cyxbs.pages.schedule.domain.model.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrenceAdjustment
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.model.ScheduleTodoState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** 分组管理页引用数量的回归测试。 */
class ScheduleCategoryCatalogTest {

  /** 父日程未分组时，单次调整的分类引用仍必须进入计数和删除拦截。 */
  @Test
  fun occurrenceAdjustmentReferenceIsCountedWhenParentIsUncategorized() {
    val parent = schedule(SCHEDULE_A, categoryId = null)
    val adjustment = adjustment(
      scheduleId = parent.id,
      categoryPatch = FieldPatch.Replace(CATEGORY_A),
    )

    assertEquals(
      mapOf(CATEGORY_A to 1),
      categoryUsageCountById(listOf(parent), listOf(adjustment)),
    )
  }

  /** 同一日程的父资源和多个单次调整重复引用同一分类时只计为一项日程。 */
  @Test
  fun referencesAreDeduplicatedByScheduleId() {
    val parent = schedule(SCHEDULE_A, CATEGORY_A)
    val first = adjustment(parent.id, FieldPatch.Replace(CATEGORY_A), day = 3)
    val second = adjustment(parent.id, FieldPatch.Replace(CATEGORY_A), day = 4)

    assertEquals(
      mapOf(CATEGORY_A to 1),
      categoryUsageCountById(listOf(parent), listOf(first, second)),
    )
  }

  /** 继承和清空分类的单次调整不制造额外引用，不同日程则分别计数。 */
  @Test
  fun inheritedAndClearedPatchesAreIgnoredAndDistinctSchedulesAreCounted() {
    val first = schedule(SCHEDULE_A, CATEGORY_A)
    val second = schedule(SCHEDULE_B, CATEGORY_A)
    val inherited = adjustment(first.id, FieldPatch.Inherit, day = 3)
    val cleared = adjustment(second.id, FieldPatch.Clear, day = 4)

    assertEquals(
      mapOf(CATEGORY_A to 2),
      categoryUsageCountById(listOf(first, second), listOf(inherited, cleared)),
    )
  }

  /** 构造无需重复展开的测试日程。 */
  private fun schedule(id: ScheduleId, categoryId: CategoryId?) = Schedule(
    id = id,
    revision = 1,
    title = "测试日程",
    description = "",
    categoryId = categoryId,
    timing = ScheduleTiming.AllDay(Date(2026, 9, 3)),
    recurrence = null,
    reminder = null,
    todoState = ScheduleTodoState.PENDING,
    createdAt = NOW,
    updatedAt = NOW,
  )

  /** 构造只关注分类补丁的单次调整。 */
  private fun adjustment(
    scheduleId: ScheduleId,
    categoryPatch: FieldPatch<CategoryId>,
    day: Int = 3,
  ) = ScheduleOccurrenceAdjustment(
    scheduleId = scheduleId,
    recurrenceId = RecurrenceId(
      originalDateTime = MinuteTimeDate(2026, 9, day, 12, 0),
      timeZoneId = "Asia/Shanghai",
      allDay = false,
    ),
    revision = 1,
    status = OccurrenceStatus.ACTIVE,
    patch = OccurrencePatch(categoryId = categoryPatch),
    createdAt = NOW,
    updatedAt = NOW,
  )

  private companion object {
    val CATEGORY_A = CategoryId("01a06639-11a0-732f-b313-39fcde9c7e23")
    val SCHEDULE_A = ScheduleId("01a0659d-5c85-78c8-83b8-2059c81fa751")
    val SCHEDULE_B = ScheduleId("01a06583-0479-787a-bb50-2af04c471c06")
    val NOW = Instant.parse("2026-09-03T00:00:00Z")
  }
}
