package com.cyxbs.pages.home.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.home.api.HomeNavArgument
import com.cyxbs.pages.home.mobile.viewmodel.CourseBottomSheetViewModel
import platform.UIKit.UITabBarController

@Composable
internal actual fun PlatformMobileHomePage(
  argument: HomeNavArgument,
  content: @Composable () -> Unit,
) {
  content()
  val courseBottomNavViewModel = viewModel(CourseBottomSheetViewModel::class)
  DisposableEffect(Unit) {
    if (IOSHomeViewPager.getDefaultExpandCourse()
      && !IAccountService::class.impl().isTouristMode()
    ) {
      courseBottomNavViewModel.state.value = true
    }
    onDispose {  }
  }
}

interface IOSHomeViewPager {

  // 创建多 tab 页
  fun createTabBarController(): UITabBarController

  // 是否默认展开课表
  fun getDefaultExpandCourse(): Boolean

  companion object : IOSHomeViewPager by IOSHomeViewPager::class.impl()
}

