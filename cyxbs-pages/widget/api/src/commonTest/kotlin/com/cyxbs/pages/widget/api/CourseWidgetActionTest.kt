package com.cyxbs.pages.widget.api

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** Widget 点击协议的序列化合同测试。 */
class CourseWidgetActionTest {

  /** 主条目和完全重叠条目的顺序必须在 Activity 参数往返后保持不变。 */
  @Test
  fun overlapItemIdsRoundTripInOrder() {
    val expected = CourseWidgetAction(
      week = 6,
      itemId = "lesson:大学物理/周二",
      overlapItemIds = listOf("schedule:todo?1", "link:2026#秋"),
    )

    val actual = Json.decodeFromString<CourseWidgetAction>(Json.encodeToString(expected))

    assertEquals(expected, actual)
  }

  /** 圆角底卡颜色必须随样式协议序列化，避免事务透明区再次透出时间轴背景。 */
  @Test
  fun itemContainerColorRoundTrips() {
    val expected = CourseWidgetItemStyle(
      contentArgb = 0xFF112C57,
      backgroundArgb = 0,
      stripeArgb = 0xFFE4E7EC,
      containerArgb = 0xFFFFFFFF,
    )

    val actual = Json.decodeFromString<CourseWidgetItemStyle>(Json.encodeToString(expected))

    assertEquals(expected, actual)
  }

  /** 大号课表分段必须完整保留折叠文案、分钟范围和展开权重。 */
  @Test
  fun oversizedTimelineSectionRoundTrips() {
    val expected = CourseWidgetTimelineSection(
      id = "before-8",
      collapsedLabel = "···",
      expandedLabel = "早晨",
      beginMinute = 0,
      endMinute = 8 * 60,
      collapsedWeight = 0.1f,
      expandedWeight = 8f,
      expandable = true,
    )

    val actual = Json.decodeFromString<CourseWidgetTimelineSection>(Json.encodeToString(expected))

    assertEquals(expected, actual)
  }
}
