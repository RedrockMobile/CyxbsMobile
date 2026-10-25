package com.cyxbs.pages.discover.home.bean

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * 校历图片缓存身份测试。
 */
class SchoolCalendarCdnBeanTest {

  /** URL 与版本都相同时应复用同一份缓存。 */
  @Test
  fun sameUrlAndVersionReuseCache() {
    val first = SchoolCalendarCdnBean(
      cdnUrl = "https://cdn.example.com/calendar.png",
      picVersion = 1,
    )
    val second = first.copy()

    assertEquals(first.imageCacheKey(), second.imageCacheKey())
  }

  /** CDN URL 变化时必须绕过旧缓存。 */
  @Test
  fun changedUrlInvalidatesCache() {
    val first = SchoolCalendarCdnBean(
      cdnUrl = "https://cdn.example.com/calendar-a.png",
      picVersion = 1,
    )
    val second = first.copy(cdnUrl = "https://cdn.example.com/calendar-b.png")

    assertNotEquals(first.imageCacheKey(), second.imageCacheKey())
  }

  /** 图片版本变化时即使 URL 不变也必须绕过旧缓存。 */
  @Test
  fun changedVersionInvalidatesCache() {
    val first = SchoolCalendarCdnBean(
      cdnUrl = "https://cdn.example.com/calendar.png",
      picVersion = 1,
    )
    val second = first.copy(picVersion = 2)

    assertNotEquals(first.imageCacheKey(), second.imageCacheKey())
    assertNotEquals(first.versionedImageUrl(), second.versionedImageUrl())
  }

  /** 版本参数应放在 fragment 前，并兼容已有 query。 */
  @Test
  fun versionedUrlKeepsQueryAndFragmentValid() {
    val calendar = SchoolCalendarCdnBean(
      cdnUrl = "https://cdn.example.com/calendar.png?source=app#preview",
      picVersion = 2,
    )

    assertEquals(
      "https://cdn.example.com/calendar.png?source=app&cyxbs_calendar=2#preview",
      calendar.versionedImageUrl(),
    )
  }
}
