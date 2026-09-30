package com.cyxbs.pages.map.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.cyxbs.components.utils.compose.getWindowScreenSize
import com.cyxbs.pages.map.api.MapNavArgument
import com.cyxbs.pages.map.viewmodel.MapComposeViewModel
import com.cyxbs.pages.map.viewmodel.MapNavEvent
import com.cyxbs.pages.map.viewmodel.SearchViewModel

@Composable
internal fun MapScreen(argument: MapNavArgument) {
  val mapViewModel = viewModel<MapComposeViewModel>()
  val searchViewModel = viewModel<SearchViewModel>()
  LaunchedEffect(mapViewModel, searchViewModel) {
    snapshotFlow {
      MapNavEvent.MapPageChanged(
        mapViewModel.mapPagerState.value == 1,
        searchViewModel.mapSearchPagerState.value == 1,
      )
    }.collect { MapComposeViewModel.emitNavEvent(it) }
  }
  MapCompose(argument)
  if (getWindowScreenSize().height / getWindowScreenSize().width > 1.5f) {
    MapPageCompose(argument) {
      MapContent(argument = argument, modifier = Modifier.fillMaxWidth())
      MapBottomSheetEntryHost(landscape = false)
    }
  } else {
    MapPageCompose(argument) {
      Column {
        BackIconCompose(
          argument = argument,
          modifier = Modifier
            .padding(start = 12.dp, top = 12.dp)
            .width(32.dp)
            .height(32.dp),
        )
        MapFunctionImageCompose(
          modifier = Modifier
            .padding(top = 32.dp)
            .background(Color.Transparent),
        )
      }
      MapBottomSheetEntryHost(landscape = true)
    }
  }
}

@Composable
private fun MapPageCompose(
  argument: MapNavArgument,
  mapContent: @Composable () -> Unit,
) {
  val mapViewModel = viewModel<MapComposeViewModel>()
  val backState = rememberNavigationEventState(NavigationEventInfo.None)
  NavigationBackHandler(
    state = backState,
    onBackCompleted = {
      if (mapViewModel.mapPagerState.value == 1) {
        mapViewModel.mapPagerState.value = 0
      } else {
        popMapAndSheets(argument)
      }
    },
  )
  AnimatedContent(
    targetState = mapViewModel.mapPagerState.value,
    transitionSpec = {
      if (targetState > initialState) {
        slideInHorizontally { width -> width } togetherWith
            slideOutHorizontally { width -> -width }
      } else {
        slideInHorizontally { width -> -width } togetherWith
            slideOutHorizontally { width -> width }
      }
    },
  ) { targetPage ->
    if (targetPage == 1) {
      AllPictureCompose(
        modifier = Modifier.fillMaxSize(),
        images = mapViewModel.pictureImages.value,
        placeId = mapViewModel.picturePlaceId.value,
        onBack = { mapViewModel.mapPagerState.value = 0 },
      )
    } else {
      mapContent()
    }
  }
}
