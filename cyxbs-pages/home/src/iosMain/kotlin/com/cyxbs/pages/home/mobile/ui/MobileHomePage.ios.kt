package com.cyxbs.pages.home.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cyxbs.components.config.navigation.DestinationParcel
import com.cyxbs.components.config.navigation.HomeArgument

@Composable
internal actual fun PlatformMobileHomePage(
  parcel: DestinationParcel<HomeArgument>,
  content: @Composable () -> Unit,
) {
  content()
}

@Composable
internal actual fun HomeViewPagerCompose(modifier: Modifier) {
}
