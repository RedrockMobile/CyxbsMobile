package com.cyxbs.pages.course.frame

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cyxbs.components.utils.compose.px2dpCompose
import com.cyxbs.components.view.ui.BottomSheetState
import com.cyxbs.pages.course.api.IMobileHomeCourseFrame
import com.cyxbs.pages.course.dialog.LocalCourseItemBottomSheetDialog
import com.cyxbs.pages.course.dialog.rememberCourseItemBottomSheetDialogState
import com.cyxbs.pages.course.frame.bottomsheet.MobileHomeBottomSheet
import com.cyxbs.pages.course.frame.header.MobileHomeCourseHeader
import com.cyxbs.pages.course.frame.item.MobileCourseAffairItemFactory
import com.cyxbs.pages.course.frame.item.MobileCourseCreateAffairItemFactory
import com.cyxbs.pages.course.frame.item.MobileCourseLinkLessonItemFactory
import com.cyxbs.pages.course.frame.item.MobileCourseSelfLessonItemFactory
import com.cyxbs.pages.course.view.AbstractCourseFrame
import com.cyxbs.pages.course.view.HomeCoursePageContent
import com.cyxbs.pages.course.view.decoration.CoursePageDecorationManager
import com.cyxbs.pages.course.view.decoration.impl.AffairPageDecoration
import com.cyxbs.pages.course.view.decoration.impl.CreateAffairPageDecoration
import com.cyxbs.pages.course.view.decoration.impl.LinkLessonPageDecoration
import com.cyxbs.pages.course.view.decoration.impl.SelfLessonPageDecoration
import com.g985892345.provider.api.annotation.ImplProvider

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

  val peekHeightState = mutableStateOf(70.dp)

  val bottomBarHeightState = mutableStateOf(0.dp)

  @Composable
  override fun HomeCourseContent(modifier: Modifier, bottomBarHeight: Dp) {
    val decorationManager = createCoursePageDecorationManager(this)
    CompositionLocalProvider(
      Local provides this,
      CoursePageDecorationManager.Local provides decorationManager,
    ) {
      MobileHomeCourseFrameContent(
        modifier = modifier,
        frame = this,
      )
    }
    SideEffect {
      bottomBarHeightState.value = bottomBarHeight
    }
  }
}

@Composable
private fun MobileHomeCourseFrameContent(
  modifier: Modifier,
  frame: MobileHomeCourseFrame,
) {
  // item 点击后出现的 BottomSheetDialog
  val itemBottomSheetDialog = rememberCourseItemBottomSheetDialogState()
  CompositionLocalProvider(
    LocalCourseItemBottomSheetDialog provides itemBottomSheetDialog
  ) {
    val density = LocalDensity.current
    val navigationBars = WindowInsets.navigationBars
    val navigationBarHeight = (navigationBars.getTop(density) + navigationBars.getBottom(density)).px2dpCompose
    MobileHomeBottomSheet(
      modifier = modifier.statusBarsPadding(),
      frame = frame,
      peekHeightExtra = frame.bottomBarHeightState.value + navigationBarHeight, // 额外添加底导和导航栏的高度
      header = { MobileHomeCourseHeader(modifier = Modifier, frame = frame) },
    ) {
      HorizontalPager(
        modifier = Modifier.navigationBarsPadding().fillMaxSize().graphicsLayer {
          alpha = frame.bottomSheetState.fraction
        },
        state = frame.pagerState,
        pageContent = { page ->
          frame.HomeCoursePageContent(
            page = page,
          )
        },
      )
    }
  }
}

@Composable
private fun createCoursePageDecorationManager(
  frame: AbstractCourseFrame
): CoursePageDecorationManager {
  val coroutineScope = rememberCoroutineScope()
  return remember {
    CoursePageDecorationManager(
      courseFrame = frame,
      courseCoroutineScope = coroutineScope,
      CreateAffairPageDecoration(courseFrame = frame, platformItemFactory = MobileCourseCreateAffairItemFactory), // 长按创建事务
      SelfLessonPageDecoration(platformItemFactory = MobileCourseSelfLessonItemFactory), // 自己的课程
      AffairPageDecoration(courseFrame = frame, platformItemFactory = MobileCourseAffairItemFactory), // 自己的事务
      LinkLessonPageDecoration(platformItemFactory = MobileCourseLinkLessonItemFactory), // 关联人的课程
    )
  }
}