package com.cyxbs.pages.home.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cyxbs.pages.home.api.HomeNavArgument

@Composable
internal actual fun PlatformMobileHomePage(
  argument: HomeNavArgument,
  content: @Composable () -> Unit,
) {
  content()
}

@Composable
internal actual fun HomeViewPagerCompose(
  argument: HomeNavArgument,
  modifier: Modifier,
) {
}
