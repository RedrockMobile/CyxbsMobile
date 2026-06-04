package com.cyxbs.components.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.account.api.ILoginDialogContent
import com.cyxbs.components.account.api.ITokenService
import com.g985892345.provider.manager.KtProvider
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlin.reflect.KClass

/**
 * 主导航容器，应用最顶层的 Compose 导航节点
 *
 * 页面注册使用示例查看 [AppNavEntry]
 *
 * ## sceneStrategies
 * sceneStrategies 决定屏幕不同宽高下渲染的 NavEntry 个数和样式，目前已默认包含如下：
 * - ListDetailSceneStrategy：【官方】宽屏下的列表与详细页处理
 * - DialogSceneStrategy：【官方】Dialog 栈处理
 * - SinglePaneSceneStrategy：【官方】默认单栈处理
 *
 * 正常情况下 ListDetailSceneStrategy 已经能适配大部分场景，相关学习文章可以查看：
 * https://juejin.cn/post/7568873939033260082?searchId=2026051823320440955048A6F1243E556E
 *
 * @author 985892345
 * @date 2026/5/22
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Suppress("UNCHECKED_CAST")
@Composable
fun AppNavDisplay() {
  appNavBackStack = rememberAppNavBackStack()
  NavDisplay(
    backStack = appNavBackStack,
    onBack = { appNavBackStack.lastOrNull()?.popBackStack() },
    entryDecorators = listOf(
      // 添加 view model store 装饰器
      rememberViewModelStoreNavEntryDecorator(),
      // 添加状态保存的默认装饰器
      rememberSaveableStateHolderNavEntryDecorator(),
    ),
    sceneStrategies = appNavCollector.values.mapNotNull {
      key(it) {
        remember {
          it.navEntry.getSceneStrategy() as SceneStrategy<AppNavArgument>?
        }
      }
    } + listOf(
      rememberAppListDetailSceneStrategy(),     // 宽屏下的列表与详细页处理
      remember { DialogSceneStrategy() },       // 支持 Dialog 栈处理
      remember { SinglePaneSceneStrategy() },   // 默认单栈处理
    ),
    transitionSpec = appNavTransitionSpec(),
    popTransitionSpec = appNavPopTransitionSpec(),
    predictivePopTransitionSpec = appNavPredictivePopTransitionSpec(),
    entryProvider = remember(appNavCollector) {
      entryProvider(
        fallback = { argument ->
          // 没找到 argument 对应页面时的兜底处理
          NavEntry(argument) {
            FallbackContent(it)
          }
        }
      ) {
        appNavCollector.forEach { entry ->
          val navEntry = entry.value.navEntry as AppNavEntry<AppNavArgument>
          addEntryProvider(
            clazz = entry.value.argumentClazz,
            clazzContentKey = { navEntry.getContentKey(it) },
            metadata = { navEntry.buildMetadata(it) }
          ) { argument ->
            AppNavEntryContent(navEntry = navEntry, argument = argument)
          }
        }
      }
    }
  )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun rememberAppListDetailSceneStrategy(): ListDetailSceneStrategy<AppNavArgument> {
  val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    .copy(horizontalPartitionSpacerSize = 0.dp)   // ← 把 spacer 压到 0，drag handle 自己撑空间
  return rememberListDetailSceneStrategy(
    directive = directive,
    paneExpansionDragHandle = { state ->
      val interactionSource = remember { MutableInteractionSource() }
      Box(
        modifier = Modifier
          .pointerHoverIcon(icon = HorizontalResizePointerIcon)
          .paneExpansionDraggable(
            state = state,
            minTouchTargetSize = 3.dp,  // 这是设置间距的地方
            interactionSource = interactionSource,
          )
          .fillMaxHeight()
          .background(Color(0xFFCFD8DC))
      )
    }
  )
}

// 单个 AppNavEntry 的内容渲染入口，内部有登录胎的检查
@Composable
private fun AppNavEntryContent(navEntry: AppNavEntry<AppNavArgument>, argument: AppNavArgument) {
  if (navEntry.isNeedLogin(argument)) {
    val loginState = remember { KtProvider.impl(IAccountService::class) }.state.collectAsState()
    if (loginState.value !is AccountState.Login) {
      // 没有登录则弹窗引导去登录，不显示页面内容
      Box(
        modifier = Modifier.fillMaxSize().background(Color.Transparent.copy(alpha = 0.6F)),
        contentAlignment = Alignment.Center,
      ) {
        remember { KtProvider.impl(ILoginDialogContent::class) }.Content()
      }
      return
    }
  }
  navEntry.Content(argument)
}

// 未匹配到任何 AppNavEntry 时的兜底页面
@Composable
private fun FallbackContent(argument: AppNavArgument) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(text = """
      未注册 ${argument::class} 类型的 AppNavEntry，请检查是否正确实现？
      
      @AppNav(route = "test")  // 使用 @AppNav 注解
      object TestNavEntry : AppNavEntry<TestNavArgument>() {
          // 继承并实现 AppNavEntry
      }
      
      如果已正常实现，按一下步骤排查：
      1. KSP 是否对 @AppNav 正常处理？
      2. AppNavDisplay 获取所有 AppNavEntry 实现类时是否包含对应实现类？
    """.trimIndent())
  }
}

internal val appNavCollector = KtProvider.allImpl(AppNavCollector::class)
  .mapValues { it.value.get() }

@Suppress("UNCHECKED_CAST")
internal val appNavSavedStateConfiguration = SavedStateConfiguration {
  serializersModule = SerializersModule {
    polymorphic(AppNavArgument::class) {
      // 注册所有 AppNavEntry argument 的 KSerializer
      appNavCollector.values.forEach {
        val clazz = it.argumentClazz as KClass<AppNavArgument>
        val serializer = it.argumentSerializer as KSerializer<AppNavArgument>
        subclass(clazz, serializer)
      }
    }
  }
}

/**
 * 提供一个能跨进程死亡 / 配置变化自动保存与恢复的 [AppNavBackStack]
 */
@Composable
fun rememberAppNavBackStack(): AppNavBackStack {
  return rememberSerializable(
    serializer = AppNavBackStackSerializer(),
    configuration = appNavSavedStateConfiguration,
  ) {
    AppNavBackStack(getFirstAppNavArgument())
  }
}

private fun getFirstAppNavArgument(): List<AppNavArgument> {
  val accountService = KtProvider.impl(IAccountService::class)
  val tokenService = KtProvider.impl(ITokenService::class)
  val isFirstToLogin = !accountService.isTouristMode() &&
      (!accountService.isLogin() || tokenService.isRefreshTokenExpired())
  if (isFirstToLogin) {
    val login = AppNavArgument.decodeFromRoute(NAV_LOGIN)
    check(login != null) { "未注册 NAV_LOGIN 对应的登录页" }
    return listOf(login)
  }
  val home = AppNavArgument.decodeFromRoute(NAV_HOME)
  check(home != null) { "未注册 NAV_HOME 对应的主页" }
  return listOf(home)
}