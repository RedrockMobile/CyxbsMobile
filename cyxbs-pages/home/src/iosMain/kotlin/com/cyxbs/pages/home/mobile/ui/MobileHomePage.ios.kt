package com.cyxbs.pages.home.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cyxbs.components.config.navigation.DestinationParcel
import com.cyxbs.components.config.navigation.HomeNavArgument

@Composable
internal actual fun PlatformMobileHomePage(
  parcel: DestinationParcel<HomeNavArgument>,
  content: @Composable () -> Unit,
) {
  content()
}

@Composable
internal actual fun HomeViewPagerCompose(
  parcel: DestinationParcel<HomeNavArgument>,
  modifier: Modifier,
) {
}
