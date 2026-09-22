package com.cyxbs.pages.course.view.item.impl

import androidx.compose.runtime.Composable
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.api.courseItemDetailId
import com.cyxbs.pages.widget.api.CourseWidgetAction
import com.cyxbs.pages.widget.api.CourseWidgetBackgroundPattern
import com.cyxbs.pages.widget.api.CourseWidgetItemStyle
import com.cyxbs.pages.widget.api.CourseWidgetRenderItem
import com.cyxbs.pages.widget.api.CourseWidgetVisibleRange
import com.cyxbs.pages.course.view.item.CourseDefaultItemContent
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
import com.cyxbs.pages.course.view.item.CourseWidgetRenderProvider
import com.cyxbs.pages.course.view.item.CourseWidgetDarkContentArgb
import com.cyxbs.pages.course.view.item.extension.IMovableItemExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.isoDayNumber

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
  // 根据不同平台对 item 进行定制化操作
  platformItemFactory: PlatformCourseLinkLessonItemFactory,
) : CourseItem(whatTime, coroutineScope), CourseWidgetRenderProvider {

  override val widgetItemId: String
    get() = lesson.courseItemDetailId(isLinked = true)

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

  /** 导出关联课程的稳定身份与共享蓝色样式，供 Widget 与 Compose 保持一致。 */
  override fun createWidgetRenderItem(
    itemState: CourseItemState,
    visibleRanges: List<CourseWidgetVisibleRange>,
  ): CourseWidgetRenderItem {
    val fixed = whatTime.now.value
    return CourseWidgetRenderItem(
      id = widgetItemId,
      dayOfWeek = fixed.dayOfWeek.isoDayNumber,
      title = lesson.course,
      content = lesson.classroomSimplify,
      beginMinute = fixed.beginTime.minuteOfDay,
      endMinute = fixed.finalTime.minuteOfDay,
      visibleRanges = visibleRanges,
      lightStyle = LinkLessonLightStyle,
      darkStyle = LinkLessonDarkStyle,
      backgroundPattern = CourseWidgetBackgroundPattern.SOLID,
      action = CourseWidgetAction(week = fixed.page, itemId = widgetItemId),
    )
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
    textColor = LinkLessonLightStyle.contentArgb.dark(LinkLessonDarkStyle.contentArgb),
    backgroundColor = LinkLessonLightStyle.backgroundArgb.dark(LinkLessonDarkStyle.backgroundArgb),
    onClick = onClick,
  )
}

/** 关联课程蓝色样式是 Compose 与 Widget 的共享常量。 */
private val LinkLessonLightStyle = CourseWidgetItemStyle(0xFF06A3FC, 0xFFDFF3FC)
private val LinkLessonDarkStyle = CourseWidgetItemStyle(CourseWidgetDarkContentArgb, 0x2690DBFB)

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
