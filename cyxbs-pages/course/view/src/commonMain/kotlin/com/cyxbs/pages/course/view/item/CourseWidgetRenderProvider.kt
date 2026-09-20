package com.cyxbs.pages.course.view.item

import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceView
import com.cyxbs.pages.widget.api.CourseWidgetRenderItem
import com.cyxbs.pages.widget.api.CourseWidgetVisibleRange
import kotlinx.datetime.isoDayNumber

/** Widget 与 Compose 深色模式共用的纯 ARGB 文字色，避免把 Compose `Color` 泄漏到快照协议。 */
const val CourseWidgetDarkContentArgb: Long = 0xFFF0F0F2L

/** 纯事务在浅色模式使用的二级文字色，与主题 `AppColor.tvLv2` 保持一致。 */
const val CourseWidgetSecondaryContentArgb: Long = 0xFF112C57L

/**
 * 向桌面 Widget 导出最终渲染条目的可选能力。
 *
 * Provider 不泄漏自身业务来源；Manager 负责计算重叠后的 [visibleRanges] 与时间轴比例，具体 Item 仅提供
 * 稳定 id、文案和明暗样式，从而与 Compose 绘制共享同一套视觉决策。
 */
interface CourseWidgetRenderProvider {
  /** 跨周刷新仍稳定且在同周内可区分的渲染身份。 */
  val widgetItemId: String

  /**
   * 点击后实时查询详情使用的业务身份。
   *
   * 默认与渲染身份一致；同一业务可能生成多个渲染片段时可覆盖为业务主键，避免详情入口依赖课表 Frame。
   */
  val widgetDialogItemId: String
    get() = widgetItemId

  /** 根据当前 [CourseItemState] 与最终可见区间创建一个可持久化的渲染条目。 */
  fun createWidgetRenderItem(
    itemState: CourseItemState,
    visibleRanges: List<CourseWidgetVisibleRange>,
  ): CourseWidgetRenderItem?
}

/** 生成课程及关联课程共用的稳定 Widget 身份，供快照导出和独立详情查询使用。 */
fun LessonByWeeks.courseWidgetItemId(isLinked: Boolean): String {
  val prefix = if (isLinked) "link-lesson" else "lesson"
  return "$prefix:$courseNum:${dayOfWeek.isoDayNumber}:$beginLesson:$period:$rawWeek:$teacher:$classroom"
}

/** 生成日程详情业务身份；不包含课表切片信息，移动或跨日后仍可实时定位同一 occurrence。 */
fun ScheduleOccurrenceView.courseWidgetDialogItemId(): String = "schedule:$identity"
