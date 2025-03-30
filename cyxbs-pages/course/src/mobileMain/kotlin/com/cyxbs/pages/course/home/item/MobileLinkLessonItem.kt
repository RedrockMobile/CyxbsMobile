package com.cyxbs.pages.course.home.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.toMinuteTimeDate
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.home.dialog.BottomSheetDialogContent
import com.cyxbs.pages.course.home.dialog.LessonBottomSheetDialog
import com.cyxbs.pages.course.home.dialog.MobileCourseBottomSheetDialog
import com.cyxbs.pages.course.home.header.BottomSheetItemHeader
import com.cyxbs.pages.course.home.header.CourseItemBottomSheetHeader
import com.cyxbs.pages.course.view.overlay.OverlayData
import com.cyxbs.pages.course.view.item.CourseDefaultItemContent
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
  override val page: Int, // 为 0 则表示整学期，否则表示第几周
  override val lesson: LessonByWeeks,
) : LinkLessonItem, BottomSheetItemHeader, BottomSheetDialogContent {

  @ImplProvider
  companion object : LinkLessonItemFactory {
    override fun createLinkLessonItem(page: Int, lesson: LessonByWeeks): LinkLessonItem {
      return MobileLinkLessonItem(page, lesson)
    }
  }

  override val key: String = hashCode().toString()

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

  @Composable
  override fun CourseItemContent(
    modifier: Modifier,
    overlap: OverlayData,
    timeline: CourseTimeline
  ) {
    val dialogContents = remember { mutableStateOf(emptyList<BottomSheetDialogContent>()) }
    CourseDefaultItemContent(
      modifier = modifier,
      timeline = timeline,
      overlap = overlap,
      topText = lesson.course,
      bottomText = lesson.classroomSimplify,
      textColor = 0xFF06A3FC.dark(0xFFF0F0F2),
      backgroundColor = 0xFFDFF3FC.dark(0x2690DBFB),
    ) { range ->
      dialogContents.value = listOf(this) + range.coveredItems.mapNotNull {
        it as? BottomSheetDialogContent
      }
    }
    MobileCourseBottomSheetDialog(dialogContents = dialogContents)
  }

  @Composable
  override fun BottomSheetHeaderContent(modifier: Modifier) {
    val state = remember(this) { mutableStateOf("") }
    val dialogContents = remember { mutableStateOf(emptyList<BottomSheetDialogContent>()) }
    CourseItemBottomSheetHeader(
      modifier = modifier,
      state = state,
      title = lesson.course,
      content = lesson.classroomSimplify,
      beginTime = lesson.beginTime,
      finalTime = lesson.finalTime,
      enableShowLandmark = true,
      onClickTitle = {
        dialogContents.value = listOf(this)
      },
      onClickContent = {
        // todo 跳转到地图页
//        startActivity(DISCOVER_MAP) {
//          putExtra(COURSE_POS_TO_MAP, header.content)
//        }
      },
    )
    MobileCourseBottomSheetDialog(dialogContents = dialogContents)
    LaunchedEffect(this) {
      val localDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
      val now = localDateTime.toMinuteTimeDate()
      if (now.date.dayOfWeek == dayOfWeek) {
        if (now.time < beginTime) {
          state.value = "Ta的下节课"
          delay((beginTime.minuteOfDay - now.minuteOfDay).minutes + localDateTime.second.seconds)
        }
        state.value = "Ta的课进行中..."
        // 后续会显示下一节课，会重新触发重组，不用再 delay
      } else {
        // 只有明天课程才会进入改分支
        state.value = "明天Ta的课"
      }
    }
  }

  @Composable
  override fun BottomSheetDialogContent() {
    LessonBottomSheetDialog(lesson, true)
  }
}