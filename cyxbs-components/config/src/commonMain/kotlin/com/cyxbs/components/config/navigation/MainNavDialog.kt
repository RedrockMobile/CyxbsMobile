package com.cyxbs.components.config.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavType
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * 如果你的 dialog 多个页面都会复用，那么建议使用 [MainNavDialog] 以页面维度创建 dialog
 *
 * 使用方式与 [MainNavDestination] 类似
 *
 * @author 985892345
 * @date 2025/11/2
 */
abstract class MainNavDialog<T : Any>(
  val argumentClass: KClass<T>,
  val dialogProperties: DialogProperties,
  val extraDeepLinks: List<NavDeepLink> = emptyList(),
  val typeMap: Map<KType, NavType<*>> = emptyMap(),
) {

  // 页面是否需要登录，如果是的话，则在未登录时将先跳转登录页
  abstract val needLogin: Boolean

  @Composable
  abstract fun DialogContent(parcel: DialogDestinationParcel<T>)

}

@Stable
data class DialogDestinationParcel<T>(
  val argument: T,
  val entry: NavBackStackEntry,
)