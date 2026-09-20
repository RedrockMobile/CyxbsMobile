package com.cyxbs.pages.course.view.decoration

import com.cyxbs.pages.widget.api.CourseWidgetRenderItem

/**
 * 为不进入 [com.cyxbs.pages.course.view.item.CourseItemHierarchy] 的 Decoration 提供只读 Widget 快照出口。
 *
 * 全天日程等背景型 Decoration 没有可重叠的时间段 Item，仍可借此导出通用渲染 DTO；实现不得修改业务状态。
 */
interface CourseWidgetSnapshotContributor {
  /** 返回指定周的最终渲染项；调用方按周读取，空列表表示该 Decoration 在本周无数据。 */
  fun createWidgetSnapshotItems(week: Int): List<CourseWidgetRenderItem>
}
