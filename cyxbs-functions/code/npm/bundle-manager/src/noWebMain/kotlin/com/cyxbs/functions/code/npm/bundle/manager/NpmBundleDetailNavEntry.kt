package com.cyxbs.functions.code.npm.bundle.manager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.appNavBackStack
import com.cyxbs.functions.code.npm.NpmBundleManager
import com.cyxbs.functions.code.npm.model.NpmBundleSnapshot
import com.cyxbs.functions.code.npm.model.NpmPackageId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * npm Bundle 详情路由参数。
 *
 * [packageName] 和 [version] 共同标识需要展示的本地 Bundle。依赖关系跳转会创建新的参数实例，
 * 因而手机单栏和宽屏双栏都使用同一条 AppNav 返回栈。
 */
@Serializable
data class NpmBundleDetailNavArgument(
  val packageName: String,
  val version: String,
) : AppNavArgument {

  /** 转换为 npm 包池使用的稳定包坐标。 */
  val id: NpmPackageId
    get() = NpmPackageId(name = packageName, version = version)

  companion object {

    /** 根据包池坐标创建详情路由参数。 */
    fun from(id: NpmPackageId): NpmBundleDetailNavArgument {
      return NpmBundleDetailNavArgument(packageName = id.name, version = id.version)
    }
  }
}

/**
 * npm Bundle 详情页面。
 *
 * 该目的地固定承担 ListDetailSceneStrategy 的 detailPane；列表页面由
 * [NpmBundleManagerNavEntry] 独立负责，两者只通过相同的 sceneKey 组成宽屏双栏。
 */
@AppNav(route = "code/npm-bundles/detail")
class NpmBundleDetailNavEntry : AppNavEntry<NpmBundleDetailNavArgument>() {

  override fun isNeedLogin(argument: NpmBundleDetailNavArgument): Boolean = false

  /** 声明为详情 pane，与 Bundle 列表使用相同的场景标识。 */
  @OptIn(ExperimentalMaterial3AdaptiveApi::class)
  override fun buildMetadata(argument: NpmBundleDetailNavArgument): Map<String, Any> {
    return ListDetailSceneStrategy.detailPane(sceneKey = BUNDLE_LIST_DETAIL_SCENE_KEY)
  }

  @OptIn(ExperimentalMaterial3AdaptiveApi::class)
  @Composable
  override fun Content(argument: NpmBundleDetailNavArgument) {
    val manager = remember { NpmBundleManager.Default }
    val coroutineScope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<NpmBundleSnapshot?>(null) }
    var selectedVersion by rememberSaveable(argument.packageName, argument.version) {
      mutableStateOf(argument.version)
    }
    var confirmation by remember { mutableStateOf<BundleConfirmation.Delete?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<BundlePageMessage?>(null) }

    /** 串行执行详情维护操作，完成后统一刷新快照。 */
    fun launchAction(block: suspend () -> String) {
      coroutineScope.launch {
        isWorking = true
        try {
          message = BundlePageMessage(block(), isError = false)
          snapshot = manager.getSnapshot()
        } catch (throwable: Throwable) {
          if (throwable is CancellationException) throw throwable
          message = BundlePageMessage(throwable.toDisplayMessage(), isError = true)
        } finally {
          isWorking = false
        }
      }
    }

    LaunchedEffect(Unit) {
      isWorking = true
      try {
        snapshot = manager.getSnapshot()
      } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        message = BundlePageMessage(throwable.toDisplayMessage(), isError = true)
      } finally {
        isWorking = false
      }
    }

    val packageGroups = remember(snapshot) { snapshot?.toPackageGroups().orEmpty() }
    val selectedId = NpmPackageId(name = argument.packageName, version = selectedVersion)
    val selectedGroup = packageGroups.firstOrNull { it.name == selectedId.name }
    val selectedBundle = selectedGroup?.versions?.firstOrNull { it.id == selectedId }
      ?: selectedGroup?.latest

    val paneDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    val canShowMultiplePanes = paneDirective.maxHorizontalPartitions > 1 ||
      paneDirective.maxVerticalPartitions > 1
    // SceneStrategy 未向 Entry 暴露实际 pane 数量。只有当前详情前方仍存在本管理流程的列表
    // Entry 时，宽屏才会同时显示两栏；直接 deeplink 到详情仍保留返回按钮。
    val currentArgumentIndex = appNavBackStack.indexOfLast { it === argument }
    val hasListPaneBeforeCurrent = currentArgumentIndex > 0 && appNavBackStack
      .subList(0, currentArgumentIndex)
      .asReversed()
      .takeWhile {
        it is NpmBundleManagerNavArgument || it is NpmBundleDetailNavArgument
      }
      .any { it is NpmBundleManagerNavArgument }
    val isDetailBesideList = canShowMultiplePanes && hasListPaneBeforeCurrent

    // 双栏详情只消费屏幕右侧安全区，左侧安全区由列表 pane 负责；单栏则消费完整安全区。
    val paneSafeDrawingInsets = if (canShowMultiplePanes) {
      WindowInsets.safeDrawing.only(
        WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom
      )
    } else {
      WindowInsets.safeDrawing
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colors.background)
        .windowInsetsPadding(paneSafeDrawingInsets),
    ) {
      BundleToolbar(
        title = "Bundle 详情",
        showBackButton = !isDetailBesideList,
        isWorking = isWorking,
        onBack = argument::popBackStack,
      )

      message?.let { BundleMessage(it) }

      Box(modifier = Modifier.fillMaxWidth().weight(1F)) {
        when {
          snapshot == null && isWorking -> CircularProgressIndicator(Modifier.align(Alignment.Center))
          selectedGroup != null && selectedBundle != null -> BundleDetail(
            group = selectedGroup,
            selected = selectedBundle,
            enabled = !isWorking,
            // 历史版本属于当前详情，不额外写入 AppNav 返回栈。
            onSelectVersion = { selectedVersion = it.version },
            // 依赖钻取创建新的详情目的地，让返回操作逐级回到来源 Bundle。
            onSelectBundle = { NpmBundleDetailNavArgument.from(it).navigate() },
            onRedownload = {
              launchAction {
                manager.redownloadBundle(selectedBundle.id)
                "已重新下载 ${selectedBundle.id.toDisplayText()}，下次创建 Runtime 时生效。"
              }
            },
            onDelete = { confirmation = BundleConfirmation.Delete(selectedBundle) },
          )
          else -> MissingBundleDetail(argument.id)
        }
      }
    }

    confirmation?.let { pending ->
      BundleConfirmationDialog(
        pending = pending,
        enabled = !isWorking,
        onDismiss = { confirmation = null },
        onConfirm = {
          confirmation = null
          launchAction {
            val result = manager.deleteBundle(pending.bundle.id)
            argument.popBackStack()
            "已删除 ${result.deletedBundles.size} 个不可达 Bundle，" +
              "失效 ${result.invalidatedEntryNames.size} 个入口。"
          }
        },
      )
    }
  }
}
