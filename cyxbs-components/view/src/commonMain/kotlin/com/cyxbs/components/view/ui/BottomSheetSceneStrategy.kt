package com.cyxbs.components.view.ui

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.runtime.get
import androidx.navigation3.scene.OverlayScene


/**
 * description: 自定义BottomSheet的Strategy(决定如何显示导航栈)
 *
 * 把带有 [BottomSheetSceneStrategy.Companion.BottomSheetKey] metadata 的 NavEntry 以
 * [BottomSheetCompose] 的形式作为 overlay 渲染，使其支持压栈/出栈。
 *
 * ## 设计要点
 * - **复用外部 [BottomSheetState]**：通过 [Properties.stateProvider] 注入由业务持有的 state，
 *   而非在 Scene 内部新建。这样地图等场景里 controller 对同一个 state 的 collapse/expand/hide
 *   联动逻辑无需改动。
 * - **出栈语义**：只有当 state 进入 [BottomSheetValueState.Hide]（彻底隐藏 / 拖到底）时才调用
 *   [SceneStrategyScope.onBack] 出栈；collapse / 露出 peek 不出栈。
 * - **退场动画**：出栈时 [OverlayScene.onRemove] 会先 `hide()` 播放收起动画，再离开组合。
 *
 * author: zzx
 * email: 1487144524@qq.com
 * date: 2026/5/30 15:24
 */
class BottomSheetSceneStrategy : SceneStrategy<Any> {

  override fun SceneStrategyScope<Any>.calculateScene(entries: List<NavEntry<Any>>): Scene<Any>? {
    // 只处理backStack最顶上的entry
    val entry = entries.lastOrNull() ?: return null
    // 如果这个entry没有声明BottomSheetKey metadata，就交给后面的来处理
    val properties = entry.metadata[BottomSheetKey] ?: return null
    return BottomSheetScene(
      key = entry.contentKey,
      previousEntries = entries.dropLast(1),
      overlaidEntries = entries.dropLast(1),
      entry = entry,
      properties = properties,
      onBack = onBack,
    )
  }

  companion object {

    /**
     * BottomSheet配置项
     */
    data class Properties(
      /**
       * 注入外部持有的 [BottomSheetState]，Scene 会复用它而非新建。
       * 一般写成引用全局/持有者的无捕获 lambda 以保持稳定（如 `{ holder.vm?.bottomSheetState }`）。
       *
       * 返回 null 表示外部尚未准备好（如进程恢复时外部 VM 还没发布），此时 Scene 不渲染任何内容，
       * 待返回非空后自动重组显示。
       */
      val stateProvider: @Composable () -> BottomSheetState?,
      val peekHeight: Dp = 0.dp,
      val expandOnShow: Boolean = false, // 出现时是否展开到最大高度
      val dismissOnBackPress: Boolean = true, // 是否让 BottomSheetCompose 自己处理返回键
      val dismissOnClickOutside: Boolean = false, // 点击 sheet 外部区域是否 dismiss
      val scrimColor: Color = Color.Transparent, // 背景遮罩颜色
      val modifier: Modifier = Modifier.navigationBarsPadding(),
      /**
       * 是否在 state 进入 [BottomSheetValueState.Hide] 时自动出栈。
       * 默认 true
       * 若由业务自行管理 entry 的进出栈（如需要稳定 z-order 的多 sheet 叠加场景），可设为 false。
       */
      val popOnHide: Boolean = true,
    )

    object BottomSheetKey : NavMetadataKey<Properties>

    /**
     * 暴露给AppNavEntry.buildMetadata()的快捷方法
     * ``` kotlin
     * override fun buildMetadata(argument: Xxx): Map<String, Any> {
     *   return AppBottomSheetSceneStrategy.bottomSheet(
     *     AppBottomSheetSceneStrategy.Properties(peekHeight = 112.dp)
     *   )
     * }
     * ```
     */
    fun bottomSheet(
      properties: Properties
    ): Map<String, Any> = metadata {
      put(BottomSheetKey, properties)
    }

  }

}

/**
 * 负责渲染 bottomSheet的Scene
 * OverlayScene 表示：
 * - 当前 entry 是覆盖层；
 * - previousEntries / overlaidEntries 是底下仍然显示的页面；
 * - content 里决定覆盖层长什么样。
 */
private class BottomSheetScene(
  override val key: Any,
  override val previousEntries: List<NavEntry<Any>>,
  override val overlaidEntries: List<NavEntry<Any>>,
  private val entry: NavEntry<Any>,
  private val properties: BottomSheetSceneStrategy.Companion.Properties,
  private val onBack: () -> Unit,
) : OverlayScene<Any> {

  override val entries: List<NavEntry<Any>> = listOf(entry)

  private lateinit var bottomSheetState: BottomSheetState

  override val content: @Composable (() -> Unit) = {

    // 返回 null 表示外部 state 尚未就绪（如进程恢复时外部 VM 还没发布），此时不渲染，待就绪后自动重组
    val state = properties.stateProvider()
    if (state != null) {
      bottomSheetState = state

      BottomSheetCompose(
        bottomSheetState = state,
        modifier = properties.modifier,
        peekHeight = properties.peekHeight,
        dismissOnBackPress = properties.dismissOnBackPress,
        dismissOnClickOutside = properties.dismissOnClickOutside,
        scrimColor = properties.scrimColor
      ) {
        // entry.Content() 不携带 BottomSheetScope receiver，这里通过 CompositionLocal 下传，
        // 供内层内容调用 bottomSheetDraggable()
        CompositionLocalProvider(LocalBottomSheetScope provides this) {
          entry.Content()
        }
      }

      if (properties.expandOnShow) {
        LaunchedEffect(state) {
          state.expand()
        }
      }

      // 只有彻底 Hide（拖到底 / 业务调用 hide()）才出栈；collapse / peek 不出栈。
      // hasShown 防止 entry 刚挂载时 state 恰为 Hide 导致的立即出栈。
      if (properties.popOnHide) {
        LaunchedEffect(state) {
          var hasShown = false
          state.stateFlow.collect { value ->
            if (value != BottomSheetValueState.Hide) {
              hasShown = true
            } else if (hasShown) {
              onBack()
            }
          }
        }
      }
    }
  }

  override suspend fun onRemove() {
    if (::bottomSheetState.isInitialized) {
      bottomSheetState.hide()
    }
  }

  // 参考官方 DialogScene，实现 equals/hashCode 保证 Scene 身份稳定（不纳入 onBack）
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || this::class != other::class) return false

    other as BottomSheetScene

    return key == other.key &&
        previousEntries == other.previousEntries &&
        overlaidEntries == other.overlaidEntries &&
        entry == other.entry &&
        properties == other.properties
  }

  override fun hashCode(): Int {
    return key.hashCode() * 31 +
        previousEntries.hashCode() * 31 +
        overlaidEntries.hashCode() * 31 +
        entry.hashCode() * 31 +
        properties.hashCode() * 31
  }

  override fun toString(): String {
    return "BottomSheetScene(key=$key, entry=$entry, previousEntries=$previousEntries, " +
        "overlaidEntries=$overlaidEntries, properties=$properties)"
  }

}
