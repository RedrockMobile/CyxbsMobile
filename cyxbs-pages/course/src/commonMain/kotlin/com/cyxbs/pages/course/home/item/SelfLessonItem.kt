package com.cyxbs.pages.course.home.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.view.data.OverlayData
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.item.DefaultContent
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import kotlinx.datetime.DayOfWeek

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/15
 */
@Stable
class SelfLessonItem(
  override val page: Int, // 为 0 则表示整学期，否则表示第几周
  val lesson: LessonByWeeks,
) : CourseItem {
  override val key: String = hashCode().toString()
  override val dayOfWeek: DayOfWeek
    get() = lesson.dayOfWeek
  override val beginTime: MinuteTime
    get() = lesson.beginTime
  override val finalTime: MinuteTime
    get() = lesson.finalTime

  @Composable
  override fun Content(modifier: Modifier, overlap: OverlayData, timeline: CourseTimeline) {
    DefaultContent(
      modifier = modifier,
      timeline = timeline,
      topText = lesson.course,
      bottomText = lesson.classroomSimplify,
      textColor = when {
        beginTime < MinuteTime(12, 0) -> 0xFFFF8015.dark(0xFFF0F0F2)
        beginTime < MinuteTime(18, 0) -> 0xFFFF6262.dark(0xFFF0F0F2)
        else -> 0xFF4066EA.dark(0xFFF0F0F2)
      },
      backgroundColor = when {
        beginTime < MinuteTime(12, 0) -> 0xFFF9E7D8.dark(0x26FFCCA1)
        beginTime < MinuteTime(18, 0) -> 0xFFF9E3E4.dark(0x26FF979B)
        else -> 0xFFDDE3F8.dark(0x269BB2FF)
      },
    )
  }
}