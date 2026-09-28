package com.cyxbs.pages.map.ui

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.scene.SceneStrategy
import com.cyxbs.components.config.Platform
import com.cyxbs.components.config.appPlatform
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_MAP_PLACE_DETAIL
import com.cyxbs.components.navigation.NAV_MAP_SEARCH
import com.cyxbs.components.navigation.appNavBackStack
import com.cyxbs.components.view.ui.BottomSheetCompose
import com.cyxbs.components.view.ui.LocalBottomSheetScope
import com.cyxbs.pages.map.util.MapOverlaySceneStrategy
import com.cyxbs.pages.map.viewmodel.MapComposeViewModel
import com.cyxbs.pages.map.viewmodel.MapNavEvent
import com.cyxbs.pages.map.viewmodel.PlaceDetailViewModel
import com.cyxbs.pages.map.viewmodel.SearchViewModel
import com.cyxbs.pages.map.widget.PlaceDetailBottomSheetContent
import com.cyxbs.pages.map.widget.SearchBottomSheetContent
import kotlinx.serialization.Serializable

@Serializable
data class PlaceDetailNavArgument(
  val placeId: String,
  val expanded: Boolean? = false,
) : AppNavArgument

@AppNav(route = NAV_MAP_PLACE_DETAIL)
class PlaceDetailNavEntry : AppNavEntry<PlaceDetailNavArgument>() {
  override fun isNeedLogin(argument: PlaceDetailNavArgument) = false
  override fun getContentKey(argument: PlaceDetailNavArgument) = NAV_MAP_PLACE_DETAIL
  override fun getSceneStrategy(): SceneStrategy<AppNavArgument> = MapOverlaySceneStrategy()
  override fun buildMetadata(argument: PlaceDetailNavArgument) = MapOverlaySceneStrategy.metadata()

  @Composable
  override fun Content(argument: PlaceDetailNavArgument) {
    val vm = viewModel { PlaceDetailViewModel() }
    LaunchedEffect(argument.placeId) {
      if (vm.placeDetailsId.value != argument.placeId) {
        vm.showPlace(argument.placeId, argument.expanded)
      }
    }
    if (vm.placeDetails.value != null && vm.sheetVisible.value) {
      BottomSheetCompose(
        bottomSheetState = vm.bottomSheetState,
        modifier = if (appPlatform == Platform.Android) Modifier.navigationBarsPadding() else Modifier,
        peekHeight = 112.dp + if (appPlatform == Platform.IOS) 12.dp else 0.dp,
        dismissOnBackPress = false,
        dismissOnClickOutside = false,
        scrimColor = Color.Transparent,
      ) {
        CompositionLocalProvider(LocalBottomSheetScope provides this) {
          PlaceDetailBottomSheetContent()
        }
      }
    }
  }
}

@Serializable
object SearchNavArgument : AppNavArgument

@AppNav(route = NAV_MAP_SEARCH)
class SearchNavEntry : AppNavEntry<SearchNavArgument>() {
  override fun isNeedLogin(argument: SearchNavArgument) = false
  override fun getContentKey(argument: SearchNavArgument) = NAV_MAP_SEARCH
  override fun getSceneStrategy(): SceneStrategy<AppNavArgument> = MapOverlaySceneStrategy()
  override fun buildMetadata(argument: SearchNavArgument) = MapOverlaySceneStrategy.metadata()

  @Composable
  override fun Content(argument: SearchNavArgument) {
    val vm = viewModel { SearchViewModel() }
    if (vm.sheetVisible.value) {
      BottomSheetCompose(
        bottomSheetState = vm.searchBottomSheetState,
        modifier = if (appPlatform == Platform.Android) Modifier.navigationBarsPadding() else Modifier,
        peekHeight = 80.dp + if (appPlatform == Platform.IOS) 12.dp else 0.dp,
        dismissOnBackPress = false,
        dismissOnClickOutside = false,
        scrimColor = Color.Transparent,
      ) {
        CompositionLocalProvider(LocalBottomSheetScope provides this) {
          SearchBottomSheetContent()
        }
      }
    }
  }
}

@Composable
fun MapBottomSheetEntryHost(landscape: Boolean) {
  LaunchedEffect(landscape) {
    if (landscape && SearchNavArgument !in appNavBackStack) {
      val detail = appNavBackStack.filterIsInstance<PlaceDetailNavArgument>().lastOrNull()
      Snapshot.withMutableSnapshot {
        detail?.popBackStack()
        SearchNavArgument.navigate()
        detail?.navigate()
      }
    }
  }
}

internal suspend fun showPlaceDetail(placeId: String, expanded: Boolean?) {
  val current = appNavBackStack.filterIsInstance<PlaceDetailNavArgument>().lastOrNull()
  // 新 entry 从参数初始化，不依赖它尚未开始收集的事件流。
  if (current == null) {
    PlaceDetailNavArgument(placeId, expanded).navigate()
  } else {
    val next = PlaceDetailNavArgument(placeId, expanded)
    if (current != next) {
      // 同一 contentKey 在一次快照内替换参数，保留 entry 的 VM。
      Snapshot.withMutableSnapshot {
        current.popBackStack()
        next.navigate()
      }
    }
    MapComposeViewModel.emitNavEvent(MapNavEvent.ShowPlaceDetail(placeId, expanded))
  }
}
