package com.cyxbs.pages.course.view.item.impl

import androidx.compose.runtime.Composable
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.extensions.toast
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
class CourseLinkLessonItem(
  whatTime: CourseItemWhatTime,
  coroutineScope: CoroutineScope,
  val lesson: LessonByWeeks,
  platformItemFactory: PlatformCourseLinkLessonItemFactory,
) : CourseItem(whatTime, coroutineScope) {

  init {
    extensions.add(CourseLinkLessonMovableItemExtension())
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
private fun CourseLinkLessonItem.Content(
  onClick: ((MinuteTimePair) -> Unit)?,
) {
  CourseDefaultItemContent(
    itemState = itemState,
    topText = lesson.course,
    bottomText = lesson.classroomSimplify,
    textColor = 0xFF06A3FC.dark(0xFFF0F0F2),
    backgroundColor = 0xFFDFF3FC.dark(0x2690DBFB),
    onClick = onClick,
  )
}

// 课程长按移动
private class CourseLinkLessonMovableItemExtension : IMovableItemExtension {
  override fun enableExpandTimelineWhenMove(itemState: CourseItemState): Boolean {
    return false
  }
}


// 下层到每个平台的课程配置
interface PlatformCourseLinkLessonItemFactory {
  fun create(item: CourseLinkLessonItem): PlatformCourseLinkLessonItem
}

interface PlatformCourseLinkLessonItem {
  @Composable
  fun CourseItemContentWrapper(content: @Composable (onClick: ((MinuteTimePair) -> Unit)?) -> Unit)
}
