package com.cyxbs.pages.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation3.scene.SceneStrategy
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_MAP_PLACE_DETAIL
import com.cyxbs.components.navigation.NAV_MAP_SEARCH
import com.cyxbs.components.navigation.appNavBackStack
import com.cyxbs.components.view.ui.BottomSheetSceneStrategy
import com.cyxbs.components.view.ui.BottomSheetSceneStrategy.Companion.Properties
import com.cyxbs.pages.map.widget.PlaceDetailBottomSheetContent
import com.cyxbs.pages.map.widget.SearchBottomSheetContent
import kotlinx.serialization.Serializable

/**
 * 地点详情 / 搜索两个 bottomSheet 的 NavEntry 改造。
 *
 * - 通过重写 [AppNavEntry.getSceneStrategy] 注入 [BottomSheetSceneStrategy]，把自身渲染成 overlay 的 bottomSheet。
 * - [AppNavEntry.buildMetadata] 里用 [Properties.stateProvider] 复用 [MapComposeViewModel] 现有的
 *   bottomSheetState / searchBottomSheetState，使 MapUiController 的联动逻辑零改动。
 * - [AppNavEntry.Content] 里通过 [MapVmHolder.WithMapScope] 切回 Map 页的 owner，
 *   使内层 `viewModel(MapComposeViewModel::class)` 解析到同一个 VM 实例。
 *
 * 进出栈时机由 [MapBottomSheetEntryHost] 统一随地图页处理（[Properties.popOnHide] = false）。
 *
 * @author zzx
 */

@Serializable
object PlaceDetailNavArgument : AppNavArgument

@AppNav(route = NAV_MAP_PLACE_DETAIL)
class PlaceDetailNavEntry : AppNavEntry<PlaceDetailNavArgument>() {

  override fun isNeedLogin(argument: PlaceDetailNavArgument): Boolean = false

  override fun getSceneStrategy(): SceneStrategy<Any> = BottomSheetSceneStrategy()

  override fun buildMetadata(argument: PlaceDetailNavArgument): Map<String, Any> {
    return BottomSheetSceneStrategy.bottomSheet(
      Properties(
        // 仅当有地点数据、且处于地图主页面（非"全部图片"页 / 非竖屏搜索整页）时才渲染，避免空 peek 或漏显到其他页之上。
        // 横屏下 mapPagerState/mapSearchPagerState 恒为 0，这两个条件不影响横屏正常显示。
        stateProvider = {
          MapVmHolder.current?.vm
            ?.takeIf {
              it.placeDetails.value != null &&
                  it.mapPagerState.value == 0 &&
                  it.mapSearchPagerState.value == 0
            }
            ?.bottomSheetState
        },
        peekHeight = 112.dp,
        dismissOnBackPress = false,
        dismissOnClickOutside = false,
        scrimColor = Color.Transparent,
        // 内层 ConstraintLayout 已自带 navigationBarsPadding，这里外壳不再重复
        modifier = Modifier,
        // 由 MapBottomSheetEntryHost 统一随地图页进出栈，不按单个 sheet 的 Hide 出栈（保证与 Search 的稳定 z-order）
        popOnHide = false,
      )
    )
  }

  @Composable
  override fun Content(argument: PlaceDetailNavArgument) {
    MapVmHolder.WithMapScope {
      PlaceDetailBottomSheetContent()
    }
  }
}

@Serializable
object SearchNavArgument : AppNavArgument

@AppNav(route = NAV_MAP_SEARCH)
class SearchNavEntry : AppNavEntry<SearchNavArgument>() {

  override fun isNeedLogin(argument: SearchNavArgument): Boolean = false

  // 单例 overlay，使用固定 contentKey
  override fun getContentKey(argument: SearchNavArgument): String = NAV_MAP_SEARCH

  override fun getSceneStrategy(): SceneStrategy<Any> = BottomSheetSceneStrategy()

  override fun buildMetadata(argument: SearchNavArgument): Map<String, Any> {
    return BottomSheetSceneStrategy.bottomSheet(
      Properties(
        stateProvider = {
          MapVmHolder.current?.vm
            ?.takeIf { it.mapPagerState.value == 0 }
            ?.searchBottomSheetState
        },
        peekHeight = 80.dp,
        dismissOnBackPress = false,
        dismissOnClickOutside = false,
        scrimColor = Color.Transparent,
        // 内层 Column 已自带 navigationBarsPadding，这里外壳不再重复
        modifier = Modifier,
        // 由 MapBottomSheetEntryHost 统一随地图页进出栈（搜索 sheet 本就 hideable=false，不会 Hide）
        popOnHide = false,
      )
    )
  }

  @Composable
  override fun Content(argument: SearchNavArgument) {
    MapVmHolder.WithMapScope {
      SearchBottomSheetContent()
    }
  }
}

@Composable
fun MapBottomSheetEntryHost(landscape: Boolean) {
  LaunchedEffect(landscape) {
    if (landscape && SearchNavArgument !in appNavBackStack) {
      SearchNavArgument.navigate()
    }
    if (PlaceDetailNavArgument !in appNavBackStack) {
      PlaceDetailNavArgument.navigate()
    }
  }
}