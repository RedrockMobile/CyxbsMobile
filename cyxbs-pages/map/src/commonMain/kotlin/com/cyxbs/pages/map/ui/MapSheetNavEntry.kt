package com.cyxbs.pages.map.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.scene.SceneStrategy
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_MAP_PLACE_DETAIL
import com.cyxbs.components.navigation.NAV_MAP_SEARCH
import com.cyxbs.components.navigation.appNavBackStack
import com.cyxbs.components.utils.compose.getWindowScreenSize
import com.cyxbs.components.view.ui.bottomsheet.BottomSheetCompose
import com.cyxbs.components.view.ui.bottomsheet.LocalBottomSheetScope
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
    if (vm.sheetVisible.value) {
      BottomSheetCompose(
        bottomSheetState = vm.bottomSheetState,
        modifier = mapSheetModifier(),
        peekHeight = 112.dp,
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
        modifier = mapSheetModifier(),
        peekHeight = 80.dp,
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
internal fun mapSheetModifier(): Modifier {
  val windowSize = getWindowScreenSize()
  // 限制外壳的命中区域，不能只缩窄内部卡片，否则透明区域仍会拦截地图。
  val bounds = if (windowSize.height / windowSize.width > 1.5f) {
    Modifier
  } else {
    Modifier.padding(start = 30.dp).width(windowSize.width / 3)
  }
  return bounds
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
