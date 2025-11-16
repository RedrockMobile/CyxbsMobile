package com.cyxbs.pages.course.page

import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.navigation.DestinationParcel
import com.cyxbs.components.config.navigation.MainNavDestination
import com.cyxbs.components.config.navigation.NAV_COURSE
import com.cyxbs.pages.course.api.CourseNavArgument
import com.cyxbs.pages.course.viewmodel.AdaptiveCourseFrameViewModel
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * 课表单页
 *
 * @author 985892345
 * @date 2025/11/16
 */
@ImplProvider(clazz = MainNavDestination::class, name = NAV_COURSE)
class CourseNavDestination : MainNavDestination<CourseNavArgument>(CourseNavArgument::class) {

  override val needLogin: Boolean
    get() = true

  @Composable
  override fun DestinationContent(parcel: DestinationParcel<CourseNavArgument>) {
    val courseFrameViewModel = viewModel { AdaptiveCourseFrameViewModel() }
    courseFrameViewModel.frame.HomeCourseContent(Modifier.systemBarsPadding())
  }
}