package com.cyxbs.pages.home.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.navigation.DestinationParcel
import com.cyxbs.components.config.navigation.HomeNavArgument
import com.cyxbs.pages.home.viewmodel.AdaptiveCourseFrameViewModel

/**
 * 默认的 PlatformHomePage 实现，自适应宽高的主页
 *
 * @author 985892345
 * @date 2025/9/22
 */
@Composable
fun AdaptiveHomePage(parcel: DestinationParcel<HomeNavArgument>) {
  val courseFrameViewModel = viewModel { AdaptiveCourseFrameViewModel() }
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    courseFrameViewModel.frame.HomeCourseContent(
      modifier = Modifier.systemBarsPadding(),
    )

//    Text(text = "退出登录", modifier = Modifier.clickable {
//      IAccountEditService::class.impl().onLogout()
//      LoginArgument.navigate(HomeArgument, clearStack = true)
//    })
  }
}