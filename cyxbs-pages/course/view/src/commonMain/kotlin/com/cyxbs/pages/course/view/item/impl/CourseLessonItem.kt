package com.cyxbs.pages.course.view.item.impl

import androidx.compose.runtime.Composable
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.view.item.CourseDefaultItemContent
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
import com.cyxbs.pages.course.view.item.extension.IMovableItemExtension
import kotlinx.coroutines.CoroutineScope

/**
 * .
 *
 * @author 985892345
 * @date 2026/3/7
 */
class CourseLessonItem(
  whatTime: CourseItemWhatTime,
  coroutineScope: CoroutineScope,
  val lesson: LessonByWeeks,
  // 根据不同平台对 item 进行定制化操作
  platformItemFactory: PlatformCourseLessonItemFactory,
) : CourseItem(whatTime, coroutineScope) {

  init {
    extensions.add(CourseLessonMovableItemExtension())
  }

  // 下层到每个平台的课程配置
  private val platform = platformItemFactory.create(this)

  @Composable
  override fun CourseItemContent() {
    platform.CourseItemContentWrapper {
      Content(onClick = it)
    }
  }
}

@Composable
private fun CourseLessonItem.Content(
  onClick: ((MinuteTimePair) -> Unit)?,
) {
  CourseDefaultItemContent(
    itemState = itemState,
    topText = lesson.course,
    bottomText = lesson.classroomSimplify,
    textColor = when {
      lesson.beginTime < MinuteTime(12, 0) -> 0xFFFF8015.dark(0xFFF0F0F2)
      lesson.beginTime < MinuteTime(18, 0) -> 0xFFFF6262.dark(0xFFF0F0F2)
      else -> 0xFF4066EA.dark(0xFFF0F0F2)
    },
    backgroundColor = when {
      lesson.beginTime < MinuteTime(12, 0) -> 0xFFF9E7D8.dark(0x26FFCCA1)
      lesson.beginTime < MinuteTime(18, 0) -> 0xFFF9E3E4.dark(0x26FF979B)
      else -> 0xFFDDE3F8.dark(0x269BB2FF)
    },
    onClick = onClick,
  )
}


// 课程长按移动
private class CourseLessonMovableItemExtension : IMovableItemExtension {
  override fun enableExpandTimelineWhenMove(itemState: CourseItemState): Boolean {
    return false
  }
}


// 下层到每个平台的课程配置
interface PlatformCourseLessonItemFactory {
  fun create(item: CourseLessonItem): PlatformCourseLessonItem
}

interface PlatformCourseLessonItem {
  @Composable
  fun CourseItemContentWrapper(content: @Composable (onClick: ((MinuteTimePair) -> Unit)?) -> Unit)
}
