package com.cyxbs.pages.course.view.source

import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import kotlinx.datetime.DayOfWeek

/**
 * .
 *
 * @author 985892345
 * @date 2025/2/15
 */
@Stable
interface CourseSource {

  /**
   * 得到单天的 item 数据
   * - 返回值可被观察重组
   *
   * @param beginDate 课表开始日期，传 null 时表示学期开始日期存在问题，比如学校在开学前就更新了课程
   * @param week 周数，传 0 时表示整学期课表
   * @param dayOfWeek 周几
   */
  fun getDayItems(
    beginDate: Date?,
    week: Int,
    dayOfWeek: DayOfWeek,
    timeline: CourseTimeline,
  ): SnapshotStateList<CourseItem>?
}