package com.cyxbs.pages.course.view.item.impl

import com.cyxbs.pages.course.view.item.CourseWidgetSecondaryContentArgb
import com.cyxbs.pages.schedule.api.ScheduleId
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceColor
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceKind
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceTiming
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceView
import kotlin.test.Test
import kotlin.test.assertEquals

/** 验证日程 Compose 与 Widget 共用的纯样式决策，重点保护事务的分类文字色。 */
class CourseScheduleWidgetStyleTest {

  /** 关联到清单的事务应保留分类浅色文字，而不是降级为纯事务主题色。 */
  @Test
  fun linkedTodoAffairKeepsCategoryLightContent() {
    val category = ScheduleOccurrenceColor(
      lightBackgroundArgb = 0xFF112233,
      lightContentArgb = 0xFF445566,
      darkBackgroundArgb = 0xFF778899,
    )

    val styles = occurrence(isInTodoList = true, categoryColor = category).widgetStyles(isAffair = true)

    assertEquals(category.lightContentArgb, styles.first.contentArgb)
    assertEquals(0L, styles.first.backgroundArgb)
    assertEquals(category.lightBackgroundArgb, styles.first.stripeArgb)
  }

  /** 未关联清单的纯事务使用主题二级文字色，并维持透明背景与默认斜纹。 */
  @Test
  fun pureAffairUsesSecondaryContentAndTransparentBackground() {
    val styles = occurrence(isInTodoList = false, categoryColor = null).widgetStyles(isAffair = true)

    assertEquals(CourseWidgetSecondaryContentArgb, styles.first.contentArgb)
    assertEquals(0L, styles.first.backgroundArgb)
    assertEquals(0xFFE4E7EC, styles.first.stripeArgb)
  }

  /** 构造只用于样式判断的 occurrence，时间语义不参与本组纯函数测试。 */
  private fun occurrence(
    isInTodoList: Boolean,
    categoryColor: ScheduleOccurrenceColor?,
  ) = ScheduleOccurrenceView(
    identity = "test-occurrence",
    scheduleId = ScheduleId("00000000-0000-7000-8000-000000000000"),
    recurrenceId = null,
    kind = ScheduleOccurrenceKind.AFFAIR,
    isInTodoList = isInTodoList,
    title = "测试事务",
    description = "",
    timing = ScheduleOccurrenceTiming.Unscheduled,
    categoryColor = categoryColor,
  )
}
