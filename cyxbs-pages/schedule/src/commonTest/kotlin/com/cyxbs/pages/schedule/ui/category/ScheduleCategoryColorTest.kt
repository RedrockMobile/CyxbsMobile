package com.cyxbs.pages.schedule.ui.category

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Category.color JSON 和固定调色板的纯值测试，不启动 Compose 或仓库。 */
class ScheduleCategoryColorTest {

  /** 18 个候选必须保持完整且互不重复，避免管理页出现视觉相同但持久化值不同的选项。 */
  @Test
  fun presetsContainEighteenDistinctColorPairs() {
    assertEquals(18, ScheduleCategoryColorPresets.size)
    assertEquals(18, ScheduleCategoryColorPresets.map { it.value }.toSet().size)
    assertEquals(ScheduleDefaultCategoryColorValue, ScheduleCategoryColorPresets.first().value)
  }

  /** 颜色按完整 JSON 往返，旧字符串或非法颜色返回 null，交由调用方回退到默认灰。 */
  @Test
  fun categoryColorJsonRoundTripsAndRejectsInvalidValues() {
    ScheduleCategoryColorPresets.forEach { preset ->
      val encoded = preset.value.encodeScheduleCategoryColor()
      assertTrue(encoded.startsWith("{"))
      assertEquals(preset.value, decodeScheduleCategoryColor(encoded))
    }
    assertNull(decodeScheduleCategoryColor("#FF3852DA"))
    assertNull(
      decodeScheduleCategoryColor(
      "{\"background\":\"bad\",\"content\":\"#FF000000\",\"darkBackground\":\"#FF000000\"}",
      ),
    )
  }
}
