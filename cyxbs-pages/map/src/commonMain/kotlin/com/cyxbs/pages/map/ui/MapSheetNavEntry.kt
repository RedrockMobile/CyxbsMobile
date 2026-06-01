package com.cyxbs.pages.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
        stateProvider = { MapVmHolder.current?.vm?.searchBottomSheetState },
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
    // SearchCompose 内部用 argument.popBackStack() 退出地图页，需复用原始 MapNavArgument 实例
    MapVmHolder.WithMapScope { shared ->
      SearchBottomSheetContent(shared.argument)
    }
  }
}

/**
 * 把 bottomSheet 以 NavEntry overlay 形式压栈/出栈的宿主，随地图页一起进出栈。
 *
 * ## 横屏（[landscape] = true）：两个 sheet 一起压入
 * 进入即**一次性按 [Search, PlaceDetail] 顺序一起压入**，离开（切到图片页 / 退出地图）时一起弹出。
 *
 * 之所以一起压入而非按需：NavDisplay 的 overlay 渲染按「首次出现顺序」累积、再 `fastForEachReversed` 绘制，
 * 即**最先压入的 overlay 画在最上层**。一起压入时 overlayScenes 为 `[PlaceDetail(栈顶), Search]`，
 * 累积顺序即 `[PlaceDetail, Search]`，反向绘制 → PlaceDetail 在最上层，符合视觉预期。
 * 若分两次压入，先压的 Search 会被累积在前 → 画在上层，导致 PlaceDetail 被压在下面。
 *
 * ## 竖屏（[landscape] = false）：只压 PlaceDetail
 * 竖屏的搜索是整页（[MapComposeViewModel.mapSearchPagerState]），不是 sheet，故只压 PlaceDetail。
 *
 * - **PlaceDetail**：无 `placeDetails` 数据或不在地图主页面时其 stateProvider 返回 null → Scene 不渲染；
 *   显示时由 MapUiController 通过同一个 bottomSheetState 驱动 collapse/expand/hide，`popOnHide=false`。
 * - **Search**：常驻 peek（hideable=false），仅横屏。
 *
 * 退出地图通过 [MapNavArgument.beforePop] 钩子先弹掉叠在 map 之上的 sheet entry，覆盖所有退出路径。
 */
@Composable
fun MapBottomSheetEntryHost(landscape: Boolean) {
  DisposableEffect(landscape) {
    // 顺序重要：先 Search 后 PlaceDetail，使 PlaceDetail 画在上层（见上方说明）
    if (landscape && SearchNavArgument !in appNavBackStack) {
      SearchNavArgument.navigate()
    }
    if (PlaceDetailNavArgument !in appNavBackStack) {
      PlaceDetailNavArgument.navigate()
    }
    // 登记退出清理：弹 map 前先弹掉叠在其上的 sheet entry（覆盖 SearchCompose / BackIcon / backHandler）
    MapVmHolder.current?.argument?.beforePop = {
      PlaceDetailNavArgument.popBackStack()
      if (landscape) SearchNavArgument.popBackStack()
    }
    onDispose {
      MapVmHolder.current?.argument?.beforePop = null
      PlaceDetailNavArgument.popBackStack()
      if (landscape) SearchNavArgument.popBackStack()
    }
  }
}