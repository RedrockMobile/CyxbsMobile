package com.cyxbs.pages.course.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.course.home.item.MobileCourseCreateItemFactory
import com.cyxbs.pages.course.home.item.MobileCourseLessonItemFactory
import com.cyxbs.pages.course.home.item.MobileCourseLinkLessonItemFactory
import com.cyxbs.pages.course.home.item.MobileScheduleItemFactory
import com.cyxbs.pages.course.view.AbstractCourseFrame
import com.cyxbs.pages.course.view.HomeCoursePageContent
import com.cyxbs.pages.course.view.decoration.impl.CourseLessonPageDecoration
import com.cyxbs.pages.course.view.decoration.impl.CreateItemPageDecoration
import com.cyxbs.pages.course.view.decoration.impl.LinkLessonPageDecoration
import com.cyxbs.pages.course.view.decoration.impl.ScheduleAffairPageDecoration
import com.cyxbs.pages.course.view.decoration.impl.ScheduleAllDayPageDecoration
import com.cyxbs.pages.course.view.decoration.impl.ScheduleDeadlinePageDecoration
import com.cyxbs.pages.course.view.decoration.impl.ScheduleTodoTimedPageDecoration
import com.cyxbs.pages.course.view.decoration.impl.SelfLessonPageDecoration
import com.cyxbs.pages.course.view.item.extension.LocalCourseItemBottomSheetDialog
import com.cyxbs.pages.course.view.item.extension.rememberCourseItemBottomSheetDialogState
import com.cyxbs.pages.course.view.page.CourseFrameHeader

/**
 * 支持自适应宽高的课表框架
 *
 * @author 985892345
 * @date 2025/9/22
 */
@Stable
class AdaptiveCourseFrame private constructor(
  initialStuNum: String,
) : AbstractCourseFrame() {

  companion object {

    /** 创建并立即登记到 [owner] 的自适应课表 Frame，禁止绕过生命周期归属直接构造。 */
    fun create(owner: ViewModel, initialStuNum: String): AdaptiveCourseFrame {
      return AdaptiveCourseFrame(initialStuNum).also(owner::addCloseable)
    }
  }

  private val accountService = IAccountService::class.impl()

  /** 最近一次账号快照，用于决定展示自己的完整课表还是只展示指定学号课程。 */
  private var selfStuNum: String? = accountService.stuNum

  // 当前展示的学号，由外部通过 [updateStuNum] 更新；
  // 切换后 CoursePageDecorationManager 会按新的 stuNum 重建以订阅对应课表数据
  var stuNum: String by mutableStateOf(initialStuNum)
    private set

  init {
    updateDecorationManager()
    launchInCourseFrameScope {
      accountService.stuNumFlow.collect { newSelfStuNum ->
        if (newSelfStuNum != selfStuNum) {
          selfStuNum = newSelfStuNum
          updateDecorationManager()
        }
      }
    }
  }

  /** 更新目标学号，并只重建归属于当前 Frame 的 Manager 子会话。 */
  fun updateStuNum(num: String) {
    if (num != stuNum) {
      stuNum = num
      updateDecorationManager()
    }
  }

  @Composable
  fun HomeCourseContent(modifier: Modifier) {
    CourseFrameComposition {
      AdaptiveHomeCourseFrameContent(
        modifier = modifier,
        frame = this,
      )
    }
  }

  /** 按当前目标与登录账号配置绘制层级；作用域和旧 Manager 的取消均由父类负责。 */
  private fun updateDecorationManager() {
    if (selfStuNum != stuNum) {
      updateCoursePageDecorations(
        CourseLessonPageDecoration(
          stuNum = stuNum,
          platformItemFactory = MobileCourseLessonItemFactory,
        ),
      )
    } else {
      updateCoursePageDecorations(
        ScheduleDeadlinePageDecoration(
          platformItemFactory = MobileScheduleItemFactory,
        ), // 截止时间点始终位于课表最上层
        CreateItemPageDecoration(
          platformItemFactory = MobileCourseCreateItemFactory
        ), // 长按创建事务
        SelfLessonPageDecoration(
          platformItemFactory = MobileCourseLessonItemFactory,
        ), // 自己的课程
        ScheduleTodoTimedPageDecoration(
          platformItemFactory = MobileScheduleItemFactory,
        ), // 清单时间段独立位于事务上方
        ScheduleAffairPageDecoration(
          platformItemFactory = MobileScheduleItemFactory,
        ), // Schedule 原生事务使用独立层级
        LinkLessonPageDecoration(
          platformItemFactory = MobileCourseLinkLessonItemFactory,
        ), // 关联人的课程
        ScheduleAllDayPageDecoration(
          platformItemFactory = MobileScheduleItemFactory,
        ), // 全天背景不参与重叠，固定放在最底层
      )
    }
  }
}

@Composable
private fun AdaptiveHomeCourseFrameContent(
  modifier: Modifier,
  frame: AdaptiveCourseFrame,
) {
  // 原移动端详情容器已经下沉到 commonMain；自适应端复用同一个宿主，不再维护简化版分支。
  val itemBottomSheetDialog = rememberCourseItemBottomSheetDialogState()
  CompositionLocalProvider(
    LocalCourseItemBottomSheetDialog provides itemBottomSheetDialog,
  ) {
    Column(modifier = modifier.background(LocalAppColors.current.topBg).systemBarsPadding()) {
      CourseFrameHeader(
        modifier = Modifier.height(50.dp),
        frame = frame,
        linkBtnVisibility = false,
      )
      HorizontalPager(
        modifier = Modifier.fillMaxSize(),
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
