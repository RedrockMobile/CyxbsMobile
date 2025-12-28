package com.cyxbs.pages.course.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
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
import com.cyxbs.pages.course.frame.bottomsheet.MobileHomeBottomSheet
import com.cyxbs.pages.course.frame.decoration.AffairDecorationViewModel
import com.cyxbs.pages.course.frame.decoration.LinkLessonDecorationViewModel
import com.cyxbs.pages.course.frame.decoration.SelfLessonDecorationViewModel
import com.cyxbs.pages.course.frame.header.MobileHomeCourseHeader
import com.cyxbs.pages.course.frame.item.CourseAffairItem
import com.cyxbs.pages.course.frame.item.LinkLessonItem
import com.cyxbs.pages.course.frame.item.SelfLessonItem
import com.cyxbs.pages.course.view.decoration.CoursePageDecoration
import com.cyxbs.pages.course.view.item.CourseItemHierarchy
import com.cyxbs.pages.course.view.item.CourseItemViewModel
import com.cyxbs.pages.course.view.item.affair.CreateAffairDecorationViewModel
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
    MobileHomeCourseFrameContent(
      modifier = modifier,
      frame = this,
    )
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
  val decorations = getDecoration()
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

@Composable
private fun getDecoration(): ImmutableList<CoursePageDecoration> {
  val selfLessonHierarchy = remember { CourseItemHierarchy<SelfLessonItem>() }
  val selfLessonDecoration = viewModel { SelfLessonDecorationViewModel(selfLessonHierarchy) }

  val linkLessonHierarchy = remember { CourseItemHierarchy<LinkLessonItem>() }
  val linkLessonDecoration = viewModel { LinkLessonDecorationViewModel(linkLessonHierarchy) }

  val affairHierarchy = remember { CourseItemHierarchy<CourseAffairItem>() }
  val affairDecoration = viewModel { AffairDecorationViewModel(affairHierarchy) }

  val createAffairDecoration = viewModel { CreateAffairDecorationViewModel() }


  val courseItemViewModel = viewModel { CourseItemViewModel(selfLessonHierarchy, linkLessonHierarchy, affairHierarchy) }
  return remember {
    persistentListOf(
      selfLessonDecoration, // 自己的课程
      affairDecoration, // 自己的事务
      linkLessonDecoration, // 关联人的课程
      createAffairDecoration, // 长按创建事务
    )
  }
}