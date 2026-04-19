package com.cyxbs.pages.course.frame.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.config.time.toMinuteTimeDate
import com.cyxbs.pages.course.dialog.CourseItemBottomSheetDialogExtension
import com.cyxbs.pages.course.dialog.CourseItemBottomSheetDialogState
import com.cyxbs.pages.course.dialog.LocalCourseItemBottomSheetDialog
import com.cyxbs.pages.course.dialog.item.AffairBottomSheetDialog
import com.cyxbs.pages.course.dialog.item.AffairBottomSheetDialogState
import com.cyxbs.pages.course.frame.header.CourseBottomSheetHeaderExtension
import com.cyxbs.pages.course.frame.header.CourseItemBottomSheetHeader
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.impl.CourseAffairItem
import com.cyxbs.pages.course.view.item.impl.PlatformCourseAffairItem
import com.cyxbs.pages.course.view.item.impl.PlatformCourseAffairItemFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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
object MobileCourseAffairItemFactory : PlatformCourseAffairItemFactory {
  override fun create(item: CourseAffairItem): PlatformCourseAffairItem {
    return MobileCourseAffairItem(item)
  }
}

private class MobileCourseAffairItem(val item: CourseAffairItem) : PlatformCourseAffairItem {

  init {
    item.extensions.add(MobileCourseAffairBottomSheetExtension(item))
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

private class MobileCourseAffairBottomSheetExtension(
  val itemKeyImpl: CourseAffairItem
) : CourseBottomSheetHeaderExtension, CourseItemBottomSheetDialogExtension {

  override val itemState: CourseItemState
    get() = itemKeyImpl.itemState

  // AffairBottomSheetDialog 状态
  // 因为外层 HorizontalPager 在开启无限循环后无法使用 key 来对应页面状态，在开启编辑后因为重组状态就会丢失
  // 所以提成一个全局的变量来保存状态
  private val affairBottomSheetDialogState = AffairBottomSheetDialogState(
    currentForm = AffairBottomSheetDialogState.CurrentForm.Show(itemKeyImpl.affairDateModel)
  )

  @Composable
  override fun CourseBottomSheetDialogContent(state: CourseItemBottomSheetDialogState) {
    AffairBottomSheetDialog(
      courseBottomSheetDialogState = state,
      affairBottomSheetDialogState = affairBottomSheetDialogState
    )
  }

  @Composable
  override fun CourseBottomSheetHeaderContent(modifier: Modifier) {
    val state = remember(this) { mutableStateOf("") }
    val itemBottomSheetDialog = LocalCourseItemBottomSheetDialog.current
    CourseItemBottomSheetHeader(
      modifier = modifier,
      state = state,
      title = itemKeyImpl.affairDateModel.idModel.title.collectAsState().value,
      content = itemKeyImpl.affairDateModel.idModel.content.collectAsState().value,
      beginTime = itemKeyImpl.affairDateModel.whatTime.value.timePair.value.first,
      finalTime = itemKeyImpl.affairDateModel.whatTime.value.timePair.value.second,
      onClickTitle = {
        itemBottomSheetDialog.showDialog(this)
      },
      onClickContent = {
      },
    )
    LaunchedEffect(this) {
      itemKeyImpl.whatTime.now.collectLatest { fixed ->
        val localDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val now = localDateTime.toMinuteTimeDate()
        if (now.date.dayOfWeek == fixed.dayOfWeek) {
          if (now.time < fixed.beginTime) {
            state.value = "下个事务"
            delay((fixed.beginTime.minuteOfDay - now.minuteOfDay).minutes + localDateTime.second.seconds)
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
}