package com.cyxbs.pages.course

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_COURSE
import com.cyxbs.pages.course.api.CourseNavArgument
import com.cyxbs.pages.course.viewmodel.AdaptiveCourseFrameViewModel

/**
 * 课表单页
 *
 * @author 985892345
 * @date 2025/11/16
 */
@AppNav(route = NAV_COURSE)
class CourseNavEntry : AppNavEntry<CourseNavArgument>() {

  override fun isNeedLogin(argument: CourseNavArgument): Boolean {
    return true
  }

  // 当 argument.stableKey != null 时，复用同一个 NavEntry（仅 stuNum 变更不重建 ViewModel）
  override fun getContentKey(argument: CourseNavArgument): String {
    return argument.stableKey ?: "course:${argument.stuNum}"
  }

  @OptIn(ExperimentalMaterial3AdaptiveApi::class)
  override fun buildMetadata(argument: CourseNavArgument): Map<String, Any> {
    return if (argument.stableKey != null) {
      // 由查找页等场景以 ListDetailSceneStrategy 作为 detailPane 调起
      ListDetailSceneStrategy.detailPane()
    } else {
      emptyMap()
    }
  }

  @Composable
  override fun Content(argument: CourseNavArgument) {
    val courseFrameViewModel = viewModel { AdaptiveCourseFrameViewModel(argument.stuNum) }
    // 当复用同一个 NavEntry 但 argument.stuNum 变化时，触发 frame 内部 stuNum 更新，
    // CoursePageDecorationManager 会重建以订阅新学号的课表数据
    LaunchedEffect(argument.stuNum) {
      courseFrameViewModel.frame.updateStuNum(argument.stuNum)
    }
    courseFrameViewModel.frame.HomeCourseContent(modifier = Modifier)
  }
}
