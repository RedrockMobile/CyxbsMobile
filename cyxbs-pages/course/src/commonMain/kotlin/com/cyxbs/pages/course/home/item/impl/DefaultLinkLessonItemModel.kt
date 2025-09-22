package com.cyxbs.pages.course.home.item.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.home.item.LinkLessonItemFactory
import com.cyxbs.pages.course.home.item.LinkLessonItemModel
import com.cyxbs.pages.course.view.item.CourseDefaultItemContent
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.IMovableItemModel
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.datetime.DayOfWeek

/**
 * .
 *
 * @author 985892345
 * @date 2025/9/23
 */
@Stable
class DefaultLinkLessonItemModel(
  override val page: Int, // 为 0 则表示整学期，否则表示第几周
  override val lesson: LessonByWeeks,
) : LinkLessonItemModel, IMovableItemModel {

  @ImplProvider
  companion object Companion : LinkLessonItemFactory {
    override fun createLinkLessonItemModel(page: Int, lesson: LessonByWeeks): LinkLessonItemModel {
      return DefaultLinkLessonItemModel(page, lesson)
    }
  }

  override val weekItemKey: String = lesson.hashCode().toString()

  override val dayOfWeek: DayOfWeek
    get() = lesson.dayOfWeek
  override val beginTime: MinuteTime
    get() = lesson.beginTime
  override val finalTime: MinuteTime
    get() = lesson.finalTime

  override fun toString(): String {
    return "MobileLinkLessonItem(page=$page, dayOfWeek=$dayOfWeek, begin=$beginTime, final=$finalTime, " +
        "course=${lesson.course})"
  }

  override fun enableExpandTimelineWhenMove(): Boolean {
    return true
//    return false
  }

  @Composable
  override fun CourseItemContent(modifier: Modifier, itemState: CourseItemState) {
    CourseDefaultItemContent(
      modifier = modifier,
      itemState = itemState,
      topText = lesson.course,
      bottomText = lesson.classroomSimplify,
      textColor = 0xFF06A3FC.dark(0xFFF0F0F2),
      backgroundColor = 0xFFDFF3FC.dark(0x2690DBFB),
    ) {
      toast(lesson.course)
    }
  }
}