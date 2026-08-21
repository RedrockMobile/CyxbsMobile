package com.cyxbs.pages.schedule.ui.edit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 信息栏分行算法的纯逻辑测试，避免不同平台字体度量导致 UI 测试不稳定。 */
class EditScheduleInfoLayoutTest {

  /** 一行能够完整容纳时不应主动拆成两行。 */
  @Test
  fun keepSingleRowWhenAllItemsFit() {
    assertNull(
      chooseBalancedInfoRowSplit(
        itemWidths = listOf(80, 70, 60),
        horizontalSpacing = 10,
        maxWidth = 230,
      )
    )
  }

  /** 普通流式布局会在首行塞入三个项目；平衡布局应把第三项移到第二行。 */
  @Test
  fun moveTrailingItemToSecondRowForSmallerWidthDifference() {
    assertEquals(
      expected = 2,
      actual = chooseBalancedInfoRowSplit(
        itemWidths = listOf(80, 70, 60, 40),
        horizontalSpacing = 10,
        maxWidth = 230,
      ),
    )
  }

  /** 存在多个可行切分点时，仍优先保证第一行不短于第二行。 */
  @Test
  fun keepFirstRowNotShorterThanSecondRow() {
    assertEquals(
      expected = 1,
      actual = chooseBalancedInfoRowSplit(
        itemWidths = listOf(160, 30, 30),
        horizontalSpacing = 10,
        maxWidth = 210,
      ),
    )
  }
}
