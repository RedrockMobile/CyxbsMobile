package com.cyxbs.pages.course

import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
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

  @Composable
  override fun Content(argument: CourseNavArgument) {
    val courseFrameViewModel = viewModel { AdaptiveCourseFrameViewModel(argument.stuNum) }
    courseFrameViewModel.frame.HomeCourseContent(Modifier.systemBarsPadding())
  }
}