package com.cyxbs.pages.course.view.item.impl

import androidx.compose.runtime.Composable
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.pages.course.api.LessonByWeeks
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
import com.cyxbs.pages.course.view.item.courseWidgetItemId
import com.cyxbs.pages.course.view.item.extension.IMovableItemExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.isoDayNumber

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
) : CourseItem(whatTime, coroutineScope), CourseWidgetRenderProvider {

  override val widgetItemId: String
    get() = lesson.courseWidgetItemId(isLinked = false)

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

  /** 导出与 Compose 课程卡片完全相同的时段配色和稳定点击身份。 */
  override fun createWidgetRenderItem(
    itemState: CourseItemState,
    visibleRanges: List<CourseWidgetVisibleRange>,
  ): CourseWidgetRenderItem {
    val fixed = whatTime.now.value
    val style = lesson.widgetStyle()
    return CourseWidgetRenderItem(
      id = widgetItemId,
      dayOfWeek = fixed.dayOfWeek.isoDayNumber,
      title = lesson.course,
      content = lesson.classroomSimplify,
      beginMinute = fixed.beginTime.minuteOfDay,
      endMinute = fixed.finalTime.minuteOfDay,
      visibleRanges = visibleRanges,
      lightStyle = style.first,
      darkStyle = style.second,
      backgroundPattern = CourseWidgetBackgroundPattern.SOLID,
      action = CourseWidgetAction(week = fixed.page, itemId = widgetItemId),
    )
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
    textColor = lesson.widgetStyle().first.contentArgb.dark(lesson.widgetStyle().second.contentArgb),
    backgroundColor = lesson.widgetStyle().first.backgroundArgb.dark(lesson.widgetStyle().second.backgroundArgb),
    onClick = onClick,
  )
}

/** 课程时段配色是 Compose 与 Widget 共享的单一事实来源。 */
private fun LessonByWeeks.widgetStyle(): Pair<CourseWidgetItemStyle, CourseWidgetItemStyle> {
  return when {
    beginTime < MinuteTime(12, 0) -> CourseWidgetItemStyle(0xFFFF8015, 0xFFF9E7D8) to
      CourseWidgetItemStyle(CourseWidgetDarkContentArgb, 0x26FFCCA1)
    beginTime < MinuteTime(18, 0) -> CourseWidgetItemStyle(0xFFFF6262, 0xFFF9E3E4) to
      CourseWidgetItemStyle(CourseWidgetDarkContentArgb, 0x26FF979B)
    else -> CourseWidgetItemStyle(0xFF4066EA, 0xFFDDE3F8) to
      CourseWidgetItemStyle(CourseWidgetDarkContentArgb, 0x269BB2FF)
  }
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
