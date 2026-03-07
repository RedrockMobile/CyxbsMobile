package com.cyxbs.pages.course.frame.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.cyxbs.components.config.time.toMinuteTimeDate
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.dialog.CourseItemBottomSheetDialogExtension
import com.cyxbs.pages.course.dialog.CourseItemBottomSheetDialogState
import com.cyxbs.pages.course.dialog.LocalCourseItemBottomSheetDialog
import com.cyxbs.pages.course.dialog.item.LessonBottomSheetDialog
import com.cyxbs.pages.course.frame.header.CourseBottomSheetHeaderExtension
import com.cyxbs.pages.course.frame.header.CourseItemBottomSheetHeader
import com.cyxbs.pages.course.view.frame.item.LinkLessonItem
import com.cyxbs.pages.course.view.frame.item.LinkLessonItemFactory
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

  override val extension = MobileLinkLessonItemExtensionGroupItem(this)

  @Composable
  override fun CourseItemContent() {
    val itemBottomSheetDialog = LocalCourseItemBottomSheetDialog.current
    val itemState = itemState
    CourseDefaultItemContent(
      itemState = itemState,
      topText = lesson.course,
      bottomText = lesson.classroomSimplify,
      textColor = 0xFF06A3FC.dark(0xFFF0F0F2),
      backgroundColor = 0xFFDFF3FC.dark(0x2690DBFB),
    ) {
      itemBottomSheetDialog.showDialog(itemState.overlap)
    }
  }
}

class MobileLinkLessonItemExtensionGroupItem(
  val itemKeyImpl: MobileLinkLessonItem
) : IMovableItemExtension by MobileLinkMovableItemExtension(itemKeyImpl)
  , CourseItemBottomSheetDialogExtension by MobileLinkCourseItemBottomSheetDialogExtension(itemKeyImpl)
  , CourseBottomSheetHeaderExtension by MobileLinkCourseBottomSheetHeaderExtension(itemKeyImpl)

private class MobileLinkMovableItemExtension(
  val itemKeyImpl: MobileLinkLessonItem
) : IMovableItemExtension {
  override fun enableExpandTimelineWhenMove(itemState: CourseItemState): Boolean {
    return false
  }
}

private class MobileLinkCourseItemBottomSheetDialogExtension(
  val itemKeyImpl: MobileLinkLessonItem
) : CourseItemBottomSheetDialogExtension {

  override val itemState: CourseItemState
    get() = itemKeyImpl.itemState

  @Composable
  override fun CourseBottomSheetDialogContent(state: CourseItemBottomSheetDialogState) {
    LessonBottomSheetDialog(itemKeyImpl.lesson, true)
  }
}

private class MobileLinkCourseBottomSheetHeaderExtension(
  val itemKeyImpl: MobileLinkLessonItem
) : CourseBottomSheetHeaderExtension {
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
        itemBottomSheetDialog.showDialog(itemKeyImpl.extension)
      },
      onClickContent = {
        // todo 跳转到地图页
//        startActivity(DISCOVER_MAP) {
//          putExtra(COURSE_POS_TO_MAP, header.content)
//        }
      },
    )
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