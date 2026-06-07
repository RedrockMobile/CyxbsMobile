package com.cyxbs.pages.home.mobile.ui

import androidx.compose.runtime.Composable
import com.cyxbs.pages.home.api.HomeNavArgument

@Composable
internal actual fun PlatformMobileHomePage(
  argument: HomeNavArgument,
  content: @Composable () -> Unit,
) {
  content()
}
