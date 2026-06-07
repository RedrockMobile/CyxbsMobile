package com.cyxbs.pages.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.cyxbs.pages.map.api.MapNavArgument
import com.cyxbs.pages.map.viewmodel.MapComposeViewModel

/**
 * 把当前 Map 页面的 VM / ViewModelStoreOwner / 跳转参数共享给独立的 sheet NavEntry。
 *
 * ## 为什么需要它
 * PlaceDetail / Search 两个 bottomSheet 被改造为独立 NavEntry（[PlaceDetailNavEntry] / [SearchNavEntry]），
 * 它们由 NavDisplay 渲染在与 [MapNavEntry] **平级的另一个组合子树**里，既不在同一个 ViewModelStore 作用域、
 * Map 子树里 provide 的 CompositionLocal 也到不了它们。因此需要一个顶层 holder 做跨子树桥接：
 * [MapNavEntry] 显示时 [publish]，离开时 [clear]，sheet entry 据此复用同一 [MapComposeViewModel] 实例
 * （并复用其 BottomSheetState），保持 MapUiController 的联动逻辑零改动。
 *
 * ## 关于空判断
 * [current] 用 mutableState 持有：进程死亡恢复时若 backstack 已含 sheet entry，它们与 Map 在同一帧组合，
 * 而 Map 的发布在组合之后，故 sheet 侧会先读到 null，待发布后自动重组。读取处统一通过 [WithMapScope]
 * 或 `current?.` 处理，无需各处散落判空。
 *
 * @author zzx
 */
internal object MapVmHolder {

  /** 当前 Map 页发布的共享快照，未发布时为 null。 */
  var current: MapShared? by mutableStateOf(null)
    private set

  fun publish(vm: MapComposeViewModel, owner: ViewModelStoreOwner, argument: MapNavArgument) {
    current = MapShared(vm, owner, argument)
  }

  /** 仅当当前持有的就是该 vm 时才清理，避免 A→B 切换时误清 B 的登记。 */
  fun clear(vm: MapComposeViewModel) {
    if (current?.vm === vm) {
      current = null
    }
  }

  /**
   * 在 Map 页的 [ViewModelStoreOwner] 作用域内渲染 [content]，使内层 `viewModel(MapComposeViewModel::class)`
   * 解析到同一个 VM 实例。尚未发布时不渲染任何内容（待发布后自动重组）。
   */
  @Composable
  fun WithMapScope(content: @Composable (MapShared) -> Unit) {
    val shared = current ?: return
    CompositionLocalProvider(LocalViewModelStoreOwner provides shared.owner) {
      content(shared)
    }
  }
}

/** Map 页共享给 sheet entry 的不可变快照。 */
internal data class MapShared(
  val vm: MapComposeViewModel,
  val owner: ViewModelStoreOwner,
  val argument: MapNavArgument,
)