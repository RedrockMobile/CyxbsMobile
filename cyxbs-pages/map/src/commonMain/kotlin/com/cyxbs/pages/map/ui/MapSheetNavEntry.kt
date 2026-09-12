package com.cyxbs.pages.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation3.scene.SceneStrategy
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_MAP_PLACE_DETAIL
import com.cyxbs.components.navigation.NAV_MAP_SEARCH
import com.cyxbs.components.navigation.appNavBackStack
import com.cyxbs.components.utils.compose.getWindowScreenSize
import com.cyxbs.components.view.ui.bottomsheet.BottomSheetSceneStrategy
import com.cyxbs.components.view.ui.bottomsheet.BottomSheetSceneStrategy.Companion.Properties
import com.cyxbs.pages.map.widget.PlaceDetailBottomSheetContent
import com.cyxbs.pages.map.widget.SearchBottomSheetContent
import com.cyxbs.pages.map.viewmodel.PlaceDetailViewModel
import com.cyxbs.pages.map.viewmodel.SearchViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.pages.map.viewmodel.CommonMapComposeViewModel
import com.cyxbs.pages.map.viewmodel.MapNavEvent
import kotlinx.serialization.Serializable

/**
 * 地点详情 / 搜索两个 bottomSheet 的 NavEntry 改造。
 *
 * - 通过重写 [AppNavEntry.getSceneStrategy] 注入 [BottomSheetSceneStrategy]，把自身渲染成 overlay 的 bottomSheet。
 * - [AppNavEntry.buildMetadata] 里用 [Properties.stateProvider] 复用 [MapComposeViewModel] 现有的
 *   bottomSheetState / searchBottomSheetState，使 MapUiController 的联动逻辑零改动。
 * - [AppNavEntry.Content] 直接获取当前 NavEntry 自己的 ViewModel，避免跨 NavEntry 共享 owner。
 *
 * 进出栈时机由 [MapBottomSheetEntryHost] 统一随地图页处理（[Properties.popOnHide] = false）。
 *
 * @author zzx
 */

@Serializable
data class PlaceDetailNavArgument(val placeId: String) : AppNavArgument

@AppNav(route = NAV_MAP_PLACE_DETAIL)
class PlaceDetailNavEntry : AppNavEntry<PlaceDetailNavArgument>() {

  override fun isNeedLogin(argument: PlaceDetailNavArgument): Boolean = false

  override fun getSceneStrategy(): SceneStrategy<AppNavArgument> = BottomSheetSceneStrategy()

  override fun buildMetadata(argument: PlaceDetailNavArgument): Map<String, Any> {
    return BottomSheetSceneStrategy.bottomSheet(
      Properties(
        // 仅当有地点数据、且处于地图主页面（非"全部图片"页 / 非竖屏搜索整页）时才渲染，避免空 peek 或漏显到其他页之上。
        // 横屏下 mapPagerState/mapSearchPagerState 恒为 0，这两个条件不影响横屏正常显示。
        stateProvider = {
          viewModel<PlaceDetailViewModel>()
            ?.takeIf {
              it.placeDetails.value != null &&
                  it.mapPagerState.value == 0 &&
                  it.mapSearchPagerState.value == 0
            }
            ?.bottomSheetState
        },
        // 系统导航栏的剩余高度由 BottomSheetCompose 自动加入，无需为 iOS 手动预留常量。
        peekHeight = 112.dp,
        dismissOnBackPress = false,
        dismissOnClickOutside = false,
        scrimColor = Color.Transparent,
        navigationBarContent = { MapBottomSheetNavigationBarContent() },
        modifier = Modifier,
        // 由 MapBottomSheetEntryHost 统一随地图页进出栈，不按单个 sheet 的 Hide 出栈（保证与 Search 的稳定 z-order）
        popOnHide = false,
      )
    )
  }

  @Composable
  override fun Content(argument: PlaceDetailNavArgument) {
    val viewModel: PlaceDetailViewModel = viewModel()
    LaunchedEffect(argument.placeId) {
      viewModel.getPlaceDetails(argument.placeId)
    }
    PlaceDetailBottomSheetContent()
  }
}

@Serializable
object SearchNavArgument : AppNavArgument

@AppNav(route = NAV_MAP_SEARCH)
class SearchNavEntry : AppNavEntry<SearchNavArgument>() {

  override fun isNeedLogin(argument: SearchNavArgument): Boolean = false

  // 单例 overlay，使用固定 contentKey
  override fun getContentKey(argument: SearchNavArgument): String = NAV_MAP_SEARCH

  override fun getSceneStrategy(): SceneStrategy<AppNavArgument> = BottomSheetSceneStrategy()

  override fun buildMetadata(argument: SearchNavArgument): Map<String, Any> {
    return BottomSheetSceneStrategy.bottomSheet(
      Properties(
        stateProvider = {
          viewModel<SearchViewModel>()
            ?.takeIf { it.mapPagerState.value == 0 }
            ?.searchBottomSheetState
        },
        peekHeight = 80.dp,
        dismissOnBackPress = false,
        dismissOnClickOutside = false,
        scrimColor = Color.Transparent,
        navigationBarContent = { MapBottomSheetNavigationBarContent() },
        modifier = Modifier,
        // 由 MapBottomSheetEntryHost 统一随地图页进出栈（搜索 sheet 本就 hideable=false，不会 Hide）
        popOnHide = false,
      )
    )
  }

  @Composable
  override fun Content(argument: SearchNavArgument) {
    SearchBottomSheetContent()
  }
}

@Composable
fun MapBottomSheetEntryHost(landscape: Boolean) {
  LaunchedEffect(landscape) {
    if (landscape && SearchNavArgument !in appNavBackStack) {
      SearchNavArgument.navigate()
    }
    if (appNavBackStack.none { it is PlaceDetailNavArgument }) {
      PlaceDetailNavArgument("").navigate()
    }
  }
  LaunchedEffect(Unit) {
    CommonMapComposeViewModel.navEvents.collect { event ->
      if (event is MapNavEvent.OpenPlaceDetail) {
        appNavBackStack.filterIsInstance<PlaceDetailNavArgument>().forEach { it.popBackStack() }
        PlaceDetailNavArgument(event.placeId).navigate()
      }
    }
  }
}

/**
 * 地图 BottomSheet 的导航栏占位。
 *
 * 竖屏铺满可用宽度；横屏与地点详情、搜索 Sheet 保持相同的左侧间距和三分之一屏宽。
 */
@Composable
private fun MapBottomSheetNavigationBarContent() {
  val windowSize = getWindowScreenSize()
  val modifier = if (windowSize.height / windowSize.width > 1.5F) {
    Modifier.fillMaxWidth()
  } else {
    Modifier
      .padding(start = 30.dp)
      .width(windowSize.width / 3)
  }
  Spacer(
    modifier = modifier
      .fillMaxHeight()
      .background(LocalAppColors.current.topBg)
  )
}
