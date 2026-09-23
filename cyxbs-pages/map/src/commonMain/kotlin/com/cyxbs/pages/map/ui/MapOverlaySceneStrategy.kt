package com.cyxbs.pages.map.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.cyxbs.components.navigation.AppNavArgument

/** 弹层外壳由 entry 内部创建，确保 VM 与 sheet 都处于 entry 的生命周期内。 */
internal class MapOverlaySceneStrategy : SceneStrategy<AppNavArgument> {
  override fun SceneStrategyScope<AppNavArgument>.calculateScene(
    entries: List<NavEntry<AppNavArgument>>,
  ): Scene<AppNavArgument>? {
    val entry = entries.lastOrNull() ?: return null
    if (entry.metadata[KEY] != true) return null
    return MapOverlayScene(entry, entries.dropLast(1))
  }

  companion object {
    private const val KEY = "map.overlay"
    fun metadata(): Map<String, Any> = mapOf(KEY to true)
  }
}

private data class MapOverlayScene(
  val entry: NavEntry<AppNavArgument>,
  override val previousEntries: List<NavEntry<AppNavArgument>>,
) : OverlayScene<AppNavArgument> {
  override val key: Any = entry.contentKey
  override val entries: List<NavEntry<AppNavArgument>> = listOf(entry)
  override val overlaidEntries: List<NavEntry<AppNavArgument>> = previousEntries
  override val content: @Composable () -> Unit = {
    // NavDisplay 会保留已有 overlay 的组合顺序，显式按栈位置绘制，避免先创建的搜索层盖住详情。
    Box(Modifier.fillMaxSize().zIndex(previousEntries.size.toFloat())) {
      entry.Content()
    }
  }
  override suspend fun onRemove() = Unit
}
