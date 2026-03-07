package com.cyxbs.pages.course.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.view.ui.BottomSheetState
import com.cyxbs.pages.course.api.IMobileHomeCourseFrame
import com.cyxbs.pages.course.dialog.LocalCourseItemBottomSheetDialog
import com.cyxbs.pages.course.dialog.rememberCourseItemBottomSheetDialogState
import com.cyxbs.pages.course.frame.bottomsheet.MobileHomeBottomSheet
import com.cyxbs.pages.course.frame.header.MobileHomeCourseHeader
import com.cyxbs.pages.course.frame.item.MobilePlatformCourseAffairItemFactory
import com.cyxbs.pages.course.frame.item.MobilePlatformCourseCreateAffairItemFactory
import com.cyxbs.pages.course.frame.item.MobilePlatformCourseLinkLessonItemFactory
import com.cyxbs.pages.course.frame.item.MobilePlatformCourseSelfLessonItemFactory
import com.cyxbs.pages.course.view.decoration.CoursePageDecoration
import com.cyxbs.pages.course.view.frame.AbstractCourseFrame
import com.cyxbs.pages.course.view.frame.HomeCoursePageContent
import com.cyxbs.pages.course.view.frame.decoration.AffairDecorationViewModel
import com.cyxbs.pages.course.view.frame.decoration.CreateAffairDecorationViewModel
import com.cyxbs.pages.course.view.frame.decoration.LinkLessonDecorationViewModel
import com.cyxbs.pages.course.view.frame.decoration.SelfLessonDecorationViewModel
import com.cyxbs.pages.course.view.item.CourseItemHierarchy
import com.cyxbs.pages.course.view.item.CourseItemViewModel
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * 移动端主页课表框架
 *
 * 展开时：
 * 课表主体:     0.0 --------> 1.0
 * 课表头部:     0.0 -> 0.0 -> 1.0
 * 主界面头部:   1.0 -> 0.0 -> 0.0
 * 折叠时：
 * 课表主体:     1.0 --------> 0.0
 * 课表头部:     1.0 -> 0.0 -> 0.0
 * 主界面头部:   0.0 -> 0.0 -> 1.0
 *
 * @author 985892345
 * @date 2025/2/15
 */
@Stable
@ImplProvider(clazz = IMobileHomeCourseFrame::class)
class MobileHomeCourseFrame : AbstractCourseFrame(), IMobileHomeCourseFrame {

  // 底部抽屉状态
  override val bottomSheetState by lazy {
    BottomSheetState()
  }

  var peekHeight: Dp by mutableStateOf(70.dp)

  fun set(
    bottomBarHeight: Dp,
  ) {
    peekHeight = 70.dp + bottomBarHeight
  }

  @Composable
  override fun HomeCourseContent(modifier: Modifier, bottomBarHeight: Dp) {
    CompositionLocalProvider(
      LocalAbstractCourseFrame provides this
    ) {
      MobileHomeCourseFrameContent(
        modifier = modifier,
        frame = this,
      )
    }
    SideEffect {
      set(bottomBarHeight)
    }
  }
}

@Composable
private fun MobileHomeCourseFrameContent(
  modifier: Modifier,
  frame: MobileHomeCourseFrame,
) {
  val decorations = createCoursePageDecorations(frame)
  // item 点击后出现的 BottomSheetDialog
  val itemBottomSheetDialog = rememberCourseItemBottomSheetDialogState()
  CompositionLocalProvider(
    LocalCourseItemBottomSheetDialog provides itemBottomSheetDialog
  ) {
    MobileHomeBottomSheet(
      modifier = modifier,
      frame = frame,
      header = { MobileHomeCourseHeader(modifier = Modifier, frame = frame) },
    ) {
      HorizontalPager(
        modifier = Modifier.fillMaxSize().background(LocalAppColors.current.topBg).graphicsLayer {
          alpha = frame.bottomSheetState.fraction
        },
        state = frame.pagerState,
        pageContent = { page ->
          frame.HomeCoursePageContent(
            page = page,
            decorations = decorations,
          )
        },
      )
    }
  }
}

@Composable
private fun createCoursePageDecorations(
  frame: AbstractCourseFrame
): ImmutableList<CoursePageDecoration> {
  val selfLessonDecoration = viewModel {
    SelfLessonDecorationViewModel(
      hierarchy = CourseItemHierarchy(),
      platformItemFactory = MobilePlatformCourseSelfLessonItemFactory,
    )
  }

  val linkLessonDecoration = viewModel {
    LinkLessonDecorationViewModel(
      hierarchy = CourseItemHierarchy(),
      platformItemFactory = MobilePlatformCourseLinkLessonItemFactory,
    )
  }

  val affairDecoration = viewModel {
    AffairDecorationViewModel(
      courseFrame = frame,
      hierarchy = CourseItemHierarchy(),
      platformItemFactory = MobilePlatformCourseAffairItemFactory
    )
  }

  val createAffairDecoration = viewModel {
    CreateAffairDecorationViewModel(
      hierarchy = CourseItemHierarchy(),
      platformItemFactory = MobilePlatformCourseCreateAffairItemFactory
    )
  }

  viewModel {
    CourseItemViewModel(
      createAffairDecoration.hierarchy, // 创建事务在顶层计算重叠，但是布局位置在底层，item 通过 zIndex 显示在课程上
      selfLessonDecoration.hierarchy,
      affairDecoration.hierarchy,
      linkLessonDecoration.hierarchy,
    )
  }

  return remember {
    persistentListOf(
      selfLessonDecoration, // 自己的课程
      affairDecoration, // 自己的事务
      linkLessonDecoration, // 关联人的课程
      createAffairDecoration, // 长按创建事务
    )
  }
}