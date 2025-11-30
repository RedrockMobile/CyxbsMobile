package com.cyxbs.pages.course.home.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastForEach
import com.cyxbs.components.config.time.toMinuteTimeDate
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.home.dialog.CourseBottomSheetDialogExtension
import com.cyxbs.pages.course.home.dialog.MobileCourseBottomSheetDialog
import com.cyxbs.pages.course.home.dialog.compose.LessonBottomSheetDialog
import com.cyxbs.pages.course.home.header.CourseBottomSheetHeaderExtension
import com.cyxbs.pages.course.home.header.CourseItemBottomSheetHeader
import com.cyxbs.pages.course.view.item.CourseDefaultItemContent
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
import com.cyxbs.pages.course.view.item.IMovableItemExtension
import com.cyxbs.pages.course.view.overlay.OverlapResult
import com.g985892345.provider.api.annotation.ImplProvider
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
data class MobileLinkLessonItem(
  override val page: Int, // 为 0 则表示整学期，否则表示第几周
  override val lesson: LessonByWeeks,
) : LinkLessonItem {
  @ImplProvider
  companion object Companion : LinkLessonItemFactory {
    override fun createLinkLessonItem(page: Int, lesson: LessonByWeeks): LinkLessonItem {
      return MobileLinkLessonItem(page, lesson)
    }
  }

  override val whatTime = CourseItemWhatTime.Fixed(
    page = page,
    dayOfWeek = lesson.dayOfWeek,
    beginTime = lesson.beginTime,
    finalTime = lesson.finalTime,
  )

  override val extension = MobileLinkLessonItemExtensionGroup(this)

  @Composable
  override fun CourseItemContent() {
    val dialogContents = remember { mutableStateOf(emptyList<CourseBottomSheetDialogExtension>()) }
    val itemState = itemState
    CourseDefaultItemContent(
      itemState = itemState,
      topText = lesson.course,
      bottomText = lesson.classroomSimplify,
      textColor = 0xFF06A3FC.dark(0xFFF0F0F2),
      backgroundColor = 0xFFDFF3FC.dark(0x2690DBFB),
    ) {
      fun collectCoveredItems(
        set: MutableSet<CourseItem>,
        data: OverlapResult,
      ): Set<CourseItem> {
        data.coveredItemList.fastForEach {
          val item = it.result.itemState.item
          if (item.whatTime.now.beginTime < whatTime.now.finalTime && item.whatTime.now.finalTime > whatTime.now.beginTime) {
            set.add(item)
          }
          collectCoveredItems(set, it.result)
        }
        return set
      }
      val overlap = itemState.overlap
      dialogContents.value =
        if (overlap == null) listOf(extension)
        else collectCoveredItems(linkedSetOf(this), overlap)
          .mapNotNull { it.extension as? CourseBottomSheetDialogExtension }
    }
    MobileCourseBottomSheetDialog(dialogContents = dialogContents)
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
    val dialogContents = remember { mutableStateOf(emptyList<CourseBottomSheetDialogExtension>()) }
    CourseItemBottomSheetHeader(
      modifier = modifier,
      state = state,
      title = itemKeyImpl.lesson.course,
      content = itemKeyImpl.lesson.classroomSimplify,
      beginTime = itemKeyImpl.lesson.beginTime,
      finalTime = itemKeyImpl.lesson.finalTime,
      enableShowLandmark = true,
      onClickTitle = {
        dialogContents.value = listOf(itemKeyImpl.extension)
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