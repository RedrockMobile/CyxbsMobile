package com.cyxbs.pages.course.home.item.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.home.item.SelfLessonItemFactory
import com.cyxbs.pages.course.home.item.SelfLessonItemModel
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
class DefaultSelfLessonItemModel(
  override val page: Int, // 为 0 则表示整学期，否则表示第几周
  override val lesson: LessonByWeeks,
) : SelfLessonItemModel, IMovableItemModel {

  @ImplProvider
  companion object Companion : SelfLessonItemFactory {
    override fun createSelfLessonItemModel(page: Int, lesson: LessonByWeeks): SelfLessonItemModel {
      return DefaultSelfLessonItemModel(page, lesson)
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
    return "MobileSelfLessonItem(page=$page, dayOfWeek=$dayOfWeek, begin=$beginTime, final=$finalTime, " +
        "course=${lesson.course})"
  }

  @Composable
  override fun CourseItemContent(modifier: Modifier, itemState: CourseItemState) {
    CourseDefaultItemContent(
      modifier = modifier,
      itemState = itemState,
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
    ) {
      toast(lesson.course)
    }
  }
}

