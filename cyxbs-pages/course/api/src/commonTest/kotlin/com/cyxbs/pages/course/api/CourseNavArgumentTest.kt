package com.cyxbs.pages.course.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** 课表 deeplink 省略学号时的序列化合同测试。 */
class CourseNavArgumentTest {

  /** 协议层没有 stuNum 字段时应保留“当前账号”占位语义，而不是解析失败。 */
  @Test
  fun missingStudentNumberUsesCurrentAccountPlaceholder() {
    val argument = Json.decodeFromString<CourseNavArgument>("{}")

    assertEquals(null, argument.stuNum)
    assertEquals(null, argument.stableKey)
  }

  /** 显式学号仍须完整保留，供查找他人课表场景使用。 */
  @Test
  fun explicitStudentNumberIsPreserved() {
    val argument = Json.decodeFromString<CourseNavArgument>(
      """{"stuNum":"2020214988","stableKey":"find-detail"}""",
    )

    assertEquals("2020214988", argument.stuNum)
    assertEquals("find-detail", argument.stableKey)
  }
}
