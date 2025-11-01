package com.cyxbs.pages.home.mobile.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.navigation.DestinationParcel
import com.cyxbs.components.config.navigation.HomeArgument
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.home.mobile.viewmodel.BottomNavViewModel
import com.cyxbs.pages.home.mobile.viewmodel.CourseBottomSheetViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import platform.UIKit.UITabBarController

@Composable
internal actual fun PlatformMobileHomePage(
  parcel: DestinationParcel<HomeArgument>,
  content: @Composable () -> Unit,
) {
  content()
}

interface IOSHomeViewPager {

  // 创建多 tab 页
  fun createTabBarController(): UITabBarController

  // 是否默认展开课表
  fun getDefaultExpandCourse(): Boolean

  companion object : IOSHomeViewPager by IOSHomeViewPager::class.impl()
}

@Composable
internal actual fun HomeViewPagerCompose(modifier: Modifier) {
  val bottomNavViewModel = viewModel(BottomNavViewModel::class)
  val courseBottomNavViewModel = viewModel(CourseBottomSheetViewModel::class)
  val coroutineScope = rememberCoroutineScope()
  UIKitViewController(
    factory = {
      IOSHomeViewPager.createTabBarController().also { tabBar ->
        bottomNavViewModel.selectedItem.map {
          bottomNavViewModel.items.indexOf(it)
        }.onEach {
          tabBar.setSelectedIndex(it.toULong())
        }.launchIn(coroutineScope)
      }
    },
    modifier = modifier.fillMaxSize()
      .navigationBarsPadding(),
  )
  LaunchedEffect(Unit) {
    if (IOSHomeViewPager.getDefaultExpandCourse()
      && !IAccountService::class.impl().isTouristMode()
    ) {
      courseBottomNavViewModel.state.value = true
    }
  }
}
