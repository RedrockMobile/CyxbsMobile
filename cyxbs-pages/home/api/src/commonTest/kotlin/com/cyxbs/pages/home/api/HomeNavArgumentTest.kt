package com.cyxbs.pages.home.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** 主页桌面入口的序列化合同测试。 */
class HomeNavArgumentTest {

  /** 普通主页 deeplink 不携带课程定位参数。 */
  @Test
  fun plainHomeUsesDefaultCourseSelection() {
    val argument = Json.decodeFromString<HomeNavArgument>("{}")

    assertEquals("discover", argument.page)
    assertEquals(null, argument.courseItemId)
  }

  /** 小组件仅传包含教学周的 Item ID，不附带快照的重叠 ID 或内容。 */
  @Test
  fun widgetSelectionRoundTripsWithoutSnapshotContent() {
    val source = HomeNavArgument(
      courseItemId = "{\"week\":5,\"itemKey\":\"segment-1\",\"detailId\":\"schedule:occurrence-123\"}",
    )

    assertEquals(source, Json.decodeFromString<HomeNavArgument>(Json.encodeToString(source)))
  }

  /** 点击序号仅驱动本次重组，不能随着导航栈序列化而在恢复后重新弹窗。 */
  @Test
  fun widgetOpenIdIsNotPersisted() {
    val source = HomeNavArgument(
      courseItemId = "{\"week\":5,\"itemKey\":\"lesson:123\",\"detailId\":\"lesson:123\"}",
      courseOpenId = 8,
    )

    val restored = Json.decodeFromString<HomeNavArgument>(Json.encodeToString(source))
    assertEquals(null, restored.courseOpenId)
    assertEquals(source.courseItemId, restored.courseItemId)
  }
}
