package com.cyxbs.pages.course.view.source

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
class CourseLessonSource : CourseSource {
  override fun getDayItems(
    beginDate: Date?,
    week: Int,
    dayOfWeek: DayOfWeek,
    timeline: CourseTimeline
  ): SnapshotStateList<CourseItem> {
    return SnapshotStateList()
  }
}