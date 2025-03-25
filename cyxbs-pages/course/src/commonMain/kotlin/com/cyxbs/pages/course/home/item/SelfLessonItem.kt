package com.cyxbs.pages.course.home.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.toMinuteTimeDate
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.view.data.OverlayData
import com.cyxbs.pages.course.view.item.CourseDefaultItemContent
import com.cyxbs.pages.course.view.item.CourseItemBottomSheetHeader
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/15
 */
@Stable
class SelfLessonItem(
  override val page: Int, // 为 0 则表示整学期，否则表示第几周
  lesson: LessonByWeeks,
) : LessonItem(lesson) {
  override val key: String = hashCode().toString()

  override fun toString(): String {
    return "SelfLessonItem(page=$page, dayOfWeek=$dayOfWeek, begin=$beginTime, final=$finalTime, " +
        "course=${lesson.course})"
  }

  @Composable
  override fun Content(modifier: Modifier, overlap: OverlayData, timeline: CourseTimeline) {
    val showDialog = remember { mutableStateOf(false) }
    CourseDefaultItemContent(
      modifier = modifier.clickableNoIndicator {
        showDialog.value = true
      },
      timeline = timeline,
      overlap = overlap,
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
    )
    CourseBottomSheetDialog(showDialog)
  }

  @Composable
  override fun HeaderContent(modifier: Modifier) {
    val state = remember(this) { mutableStateOf("") }
    CourseItemBottomSheetHeader(
      modifier = modifier,
      state = state,
      title = lesson.course,
      content = lesson.classroomSimplify,
      beginTime = lesson.beginTime,
      finalTime = lesson.finalTime,
      enableShowLandmark = true,
      onClickTitle = {
        // todo 弹起 BottomSheet dialog
        // Umeng 埋点统计
//        Umeng.sendEvent(Umeng.Event.CourseDetail(true))
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
      if (now.date.dayOfWeek == dayOfWeek) {
        if (now.time < beginTime) {
          state.value = "下节课"
          delay((beginTime.minuteOfDay - now.minuteOfDay).minutes + localDateTime.second.seconds)
        }
        state.value = "进行中..."
        // 后续会显示下一节课，会重新触发重组，不用再 delay
      } else {
        // 只有明天课程才会进入改分支
        state.value = "明天"
      }
    }
  }
}


