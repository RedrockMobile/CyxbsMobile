package com.cyxbs.pages.course.home.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.cyxbs.components.config.time.toMinuteTimeDate
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.home.dialog.CourseBottomSheetDialogExtension
import com.cyxbs.pages.course.home.dialog.MobileCourseBottomSheetDialog
import com.cyxbs.pages.course.home.dialog.compose.LessonBottomSheetDialog
import com.cyxbs.pages.course.home.dialog.rememberCourseBottomSheetDialogState
import com.cyxbs.pages.course.home.header.CourseBottomSheetHeaderExtension
import com.cyxbs.pages.course.home.header.CourseItemBottomSheetHeader
import com.cyxbs.pages.course.view.item.CourseDefaultItemContent
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
import com.cyxbs.pages.course.view.item.extension.IMovableItemExtension
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.CoroutineScope
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
 * @date 2025/3/22
 */
@Stable
class MobileLinkLessonItem(
  whatTime: CourseItemWhatTime,
  coroutineScope: CoroutineScope,
  lesson: LessonByWeeks
) : LinkLessonItem(whatTime, coroutineScope, lesson) {
  @ImplProvider
  companion object Companion : LinkLessonItemFactory {
    override fun createLinkLessonItem(
      whatTime: CourseItemWhatTime,
      coroutineScope: CoroutineScope,
      lesson: LessonByWeeks
    ): LinkLessonItem {
      return MobileLinkLessonItem(whatTime, coroutineScope, lesson)
    }
  }

  override val extension = MobileLinkLessonItemExtensionGroup(this)

  @Composable
  override fun CourseItemContent() {
    val bottomSheetDialogState = rememberCourseBottomSheetDialogState()
    val itemState = itemState
    CourseDefaultItemContent(
      itemState = itemState,
      topText = lesson.course,
      bottomText = lesson.classroomSimplify,
      textColor = 0xFF06A3FC.dark(0xFFF0F0F2),
      backgroundColor = 0xFFDFF3FC.dark(0x2690DBFB),
    ) {
      bottomSheetDialogState.showDialog(itemState.overlap)
    }
    MobileCourseBottomSheetDialog(bottomSheetDialogState)
  }
}

class MobileLinkLessonItemExtensionGroup(
  val itemKeyImpl: MobileLinkLessonItem
) : IMovableItemExtension by MobileLinkMovableItemExtension(itemKeyImpl)
  , CourseBottomSheetDialogExtension by MobileLinkCourseBottomSheetDialogExtension(itemKeyImpl)
  , CourseBottomSheetHeaderExtension by MobileLinkCourseBottomSheetHeaderExtension(itemKeyImpl)

private class MobileLinkMovableItemExtension(
  val itemKeyImpl: MobileLinkLessonItem
) : IMovableItemExtension {
  override fun enableExpandTimelineWhenMove(itemState: CourseItemState): Boolean {
    return true // todo 测试功能
  }
}

private class MobileLinkCourseBottomSheetDialogExtension(
  val itemKeyImpl: MobileLinkLessonItem
) : CourseBottomSheetDialogExtension {
  override val courseBottomSheetDialogContent: @Composable (() -> Unit) = {
    LessonBottomSheetDialog(itemKeyImpl.lesson, false)
  }
}

private class MobileLinkCourseBottomSheetHeaderExtension(
  val itemKeyImpl: MobileLinkLessonItem
) : CourseBottomSheetHeaderExtension {
  @Composable
  override fun CourseBottomSheetHeaderContent(modifier: Modifier) {
    val state = remember(this) { mutableStateOf("") }
    val bottomSheetDialogState = rememberCourseBottomSheetDialogState()
    CourseItemBottomSheetHeader(
      modifier = modifier,
      state = state,
      title = itemKeyImpl.lesson.course,
      content = itemKeyImpl.lesson.classroomSimplify,
      beginTime = itemKeyImpl.lesson.beginTime,
      finalTime = itemKeyImpl.lesson.finalTime,
      enableShowLandmark = true,
      onClickTitle = {
        bottomSheetDialogState.showDialog(itemKeyImpl.extension)
      },
      onClickContent = {
        // todo 跳转到地图页
//        startActivity(DISCOVER_MAP) {
//          putExtra(COURSE_POS_TO_MAP, header.content)
//        }
      },
    )
    MobileCourseBottomSheetDialog(bottomSheetDialogState)
    LaunchedEffect(this) {
      val localDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
      val now = localDateTime.toMinuteTimeDate()
      if (now.date.dayOfWeek == itemKeyImpl.lesson.dayOfWeek) {
        if (now.time < itemKeyImpl.lesson.beginTime) {
          state.value = "Ta的下节课"
          delay((itemKeyImpl.lesson.beginTime.minuteOfDay - now.minuteOfDay).minutes + localDateTime.second.seconds)
        }
        state.value = "Ta的课进行中..."
        // 后续会显示下一节课，会重新触发重组，不用再 delay
      } else {
        // 只有明天课程才会进入改分支
        state.value = "明天Ta的课"
      }
    }
  }
}