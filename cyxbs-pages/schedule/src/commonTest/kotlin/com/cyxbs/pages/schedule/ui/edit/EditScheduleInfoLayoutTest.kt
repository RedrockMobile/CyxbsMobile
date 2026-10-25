package com.cyxbs.pages.schedule.ui.edit

import kotlin.test.Test
import kotlin.test.assertEquals

/** 信息栏稳定换行缓存的纯逻辑测试。 */
class EditScheduleInfoLayoutTest {

  /** 单个文案变长时只移动该项，其他字段继续留在原来的行。 */
  @Test
  fun growingItemMovesWithoutCascadingOtherItems() {
    val cache = StableInfoRowLayoutCache()
    assertEquals(
      listOf(0, 0, 1, 1),
      cache.rowsFor(
        itemWidths = listOf(80, 70, 60, 40),
        suggestedRows = listOf(0, 0, 1, 1),
        horizontalSpacing = 10,
        maxWidth = 170,
      ),
    )
    assertEquals(
      listOf(0, 2, 1, 1),
      cache.rowsFor(
        itemWidths = listOf(80, 100, 60, 40),
        suggestedRows = listOf(0, 0, 1, 1),
        horizontalSpacing = 10,
        maxWidth = 170,
      ),
    )
  }

  /** 临时变长的文案恢复后优先回到自己的基准行。 */
  @Test
  fun shrinkingItemReturnsToBaseRow() {
    val cache = StableInfoRowLayoutCache()
    cache.rowsFor(
      itemWidths = listOf(80, 70, 60, 40),
      suggestedRows = listOf(0, 0, 1, 1),
      horizontalSpacing = 10,
      maxWidth = 170,
    )
    assertEquals(
      listOf(0, 2, 1, 1),
      cache.rowsFor(
        itemWidths = listOf(80, 100, 60, 40),
        suggestedRows = listOf(0, 0, 1, 1),
        horizontalSpacing = 10,
        maxWidth = 170,
      ),
    )
    assertEquals(
      listOf(0, 0, 1, 1),
      cache.rowsFor(
        itemWidths = listOf(80, 70, 60, 40),
        suggestedRows = listOf(0, 0, 1, 1),
        horizontalSpacing = 10,
        maxWidth = 170,
      ),
    )
  }

  /** 容器宽度变化时重新计算，使横竖屏或窗口尺寸变化后能够正常排布。 */
  @Test
  fun containerWidthChangeRecalculatesRows() {
    val cache = StableInfoRowLayoutCache()
    assertEquals(
      listOf(0, 0, 1),
      cache.rowsFor(
        itemWidths = listOf(80, 70, 60),
        suggestedRows = listOf(0, 0, 1),
        horizontalSpacing = 10,
        maxWidth = 170,
      ),
    )
    assertEquals(
      listOf(0, 0, 1),
      cache.rowsFor(
        itemWidths = listOf(80, 70, 60),
        suggestedRows = listOf(0, 0, 1),
        horizontalSpacing = 10,
        maxWidth = 230,
      ),
    )
  }

  /** 即使第一行空间充足，建议第二行的信息项也不会被提前。 */
  @Test
  fun suggestedRowPreventsMovingIntoEarlierFreeSpace() {
    assertEquals(
      listOf(0, 0, 1, 1),
      StableInfoRowLayoutCache().rowsFor(
        itemWidths = listOf(40, 40, 40, 40),
        suggestedRows = listOf(0, 0, 1, 1),
        horizontalSpacing = 10,
        maxWidth = 300,
      ),
    )
  }
}
