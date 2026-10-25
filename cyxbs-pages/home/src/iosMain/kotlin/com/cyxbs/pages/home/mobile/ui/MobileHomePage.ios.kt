package com.cyxbs.pages.home.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.home.api.HomeNavArgument
import com.cyxbs.pages.home.mobile.viewmodel.CourseBottomSheetViewModel
import com.cyxbs.pages.mine.api.MineSettings

@Composable
internal actual fun PlatformMobileHomePage(
  argument: HomeNavArgument,
  content: @Composable () -> Unit,
) {
  content()
  val courseBottomNavViewModel = viewModel(CourseBottomSheetViewModel::class)
  // 保存主页实例是否处理过启动偏好，避免从设置等页面返回时再次展开课表。
  var initialLaunchHandled by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    if (initialLaunchHandled) return@LaunchedEffect
    initialLaunchHandled = true
    if (MineSettings.isShowCourseFirst()
      && !IAccountService::class.impl().isTouristMode()
    ) {
      courseBottomNavViewModel.state.value = true
    }
  }
}
