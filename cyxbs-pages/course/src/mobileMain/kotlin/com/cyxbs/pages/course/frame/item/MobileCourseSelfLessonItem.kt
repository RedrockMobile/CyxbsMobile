package com.cyxbs.pages.course.frame.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.config.time.toMinuteTimeDate
import com.cyxbs.pages.course.dialog.CourseItemBottomSheetDialogExtension
import com.cyxbs.pages.course.dialog.CourseItemBottomSheetDialogState
import com.cyxbs.pages.course.dialog.LocalCourseItemBottomSheetDialog
import com.cyxbs.pages.course.dialog.item.LessonBottomSheetDialog
import com.cyxbs.pages.course.frame.header.CourseBottomSheetHeaderExtension
import com.cyxbs.pages.course.frame.header.CourseItemBottomSheetHeader
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.impl.CourseLessonItem
import com.cyxbs.pages.course.view.item.impl.PlatformCourseLessonItem
import com.cyxbs.pages.course.view.item.impl.PlatformCourseLessonItemFactory
import com.cyxbs.pages.map.api.MapNavArgument
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * .
 *
 * @author 985892345
 * @date 2026/3/7
 */
object MobileCourseSelfLessonItemFactory : PlatformCourseLessonItemFactory {
  override fun create(item: CourseLessonItem): PlatformCourseLessonItem {
    return MobileCourseSelfLessonItem(item)
  }
}

private class MobileCourseSelfLessonItem(
  val item: CourseLessonItem
) : PlatformCourseLessonItem {

  init {
    item.extensions.add(MobileSelfCourseBottomSheetExtension(item))
  }

  @Composable
  override fun CourseItemContentWrapper(
    content: @Composable ((onClick: ((MinuteTimePair) -> Unit)?) -> Unit)
  ) {
    val itemBottomSheetDialog = LocalCourseItemBottomSheetDialog.current
    content.invoke {
      // 点击事件
      itemBottomSheetDialog.showDialog(item.itemState.overlap)
    }
  }
}


private class MobileSelfCourseBottomSheetExtension(
  val itemKeyImpl: CourseLessonItem
) : CourseBottomSheetHeaderExtension, CourseItemBottomSheetDialogExtension {

  override val itemState: CourseItemState
    get() = itemKeyImpl.itemState

  @Composable
  override fun CourseBottomSheetDialogContent(state: CourseItemBottomSheetDialogState) {
    LessonBottomSheetDialog(itemKeyImpl.lesson, false)
  }

  @Composable
  override fun CourseBottomSheetHeaderContent(modifier: Modifier) {
    val state = remember(this) { mutableStateOf("") }
    val itemBottomSheetDialog = LocalCourseItemBottomSheetDialog.current
    CourseItemBottomSheetHeader(
      modifier = modifier,
      state = state,
      title = itemKeyImpl.lesson.course,
      content = itemKeyImpl.lesson.classroomSimplify,
      beginTime = itemKeyImpl.lesson.beginTime,
      finalTime = itemKeyImpl.lesson.finalTime,
      enableShowLandmark = true,
      onClickTitle = {
        itemBottomSheetDialog.showDialog(itemKeyImpl.extensions.get(CourseItemBottomSheetDialogExtension::class)!!)
      },
      onClickContent = {
        // 跳转到地图页
        MapNavArgument(itemKeyImpl.lesson.classroom).navigate()
      },
    )
    LaunchedEffect(this) {
      val localDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
      val now = localDateTime.toMinuteTimeDate()
      if (now.date.dayOfWeek == itemKeyImpl.lesson.dayOfWeek) {
        if (now.time < itemKeyImpl.lesson.beginTime) {
          state.value = "下节课"
          delay((itemKeyImpl.lesson.beginTime.minuteOfDay - now.minuteOfDay).minutes + localDateTime.second.seconds)
        }
        state.value = "进行中..."
        // 后续会显示下一节课，会重新触发重组，不用再 delay
      } else {
        // 只有明天课程才会进入该分支
        state.value = "明天"
      }
    }
  }
}