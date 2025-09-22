package com.cyxbs.components.config.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavType
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * 导航目的地抽象类，其子类为具体的目的地页面
 *
 * # 使用方式
 * 1. 继承此类，提供 [argumentClass]，相关数据类建议放至 api 模块中
 * 2. 使用 @ImplProvider(clazz = MainDestination::class, name = "xxx") 注解，其中 xxx 将会被注册为 cyxbs://xxx 进行链接跳转
 * ```
 * @ImplProvider(clazz = MainDestination::class, name = "xxx")
 * class XXXDestination : MainDestination<XXXArgument>(XXXArgument::Class) {}
 *
 * // 后续使用以下方式进行跳转
 * MainNavController.current.navigate(argument, ...)
 *
 * // 对于 name = "xxx" 也会同时注册 cyxbs://xxx/... 的 deeplink，其末尾的 ... 由跳转类的 argumentClass 的字段解析决定
 * MainNavController.current.navigate("cyxbs://xxx/...", ...)
 * ```
 *
 *
 * # 对于 [extraDeepLinks] 参数
 * 默认的 deeplink: path 为 @ImplProvider name，其余参数由 parcel 的序列化信息解析
 * 其中解析规则详细可看：[NavDeepLink.Builder.setUriPattern]
 * - 带有默认值时视为 query 参数
 * - 使用 CollectionNavType 解析的值视为 query 参数
 * - 其余视为 path 参数，按顺序添加
 *
 *
 * # 对于 [typeMap] 参数
 * 用于注册自定义类型的解析
 * argument 只支持 基本类型、String、List<String>，其他类型注册该需要 NavType 进行解析
 *
 *
 * @param extraDeepLinks 额外的 deeplink
 * @param typeMap 额外的 NavType
 *
 * @author 985892345
 * @date 2025/4/1
 */
abstract class MainDestination<T : Any>(
  val argumentClass: KClass<T>,
  val extraDeepLinks: List<NavDeepLink> = emptyList(),
  val typeMap: Map<KType, NavType<*>> = emptyMap()
) {

  abstract val content: @Composable (DestinationParcel<T>) -> Unit

  open fun enterTransition(
    scope: AnimatedContentTransitionScope<NavBackStackEntry>
  ): EnterTransition? = null
  open fun exitTransition(
    scope: AnimatedContentTransitionScope<NavBackStackEntry>
  ): ExitTransition? = null

  open fun popEnterTransition(
    scope: AnimatedContentTransitionScope<NavBackStackEntry>
  ): EnterTransition? = enterTransition(scope)
  open fun popExitTransition(
    scope: AnimatedContentTransitionScope<NavBackStackEntry>
  ): ExitTransition? = exitTransition(scope)

  open fun sizeTransform(
    scope: AnimatedContentTransitionScope<NavBackStackEntry>
  ): SizeTransform? = null
}

@Stable
data class DestinationParcel<T>(
  val argument: T,
  val entry: NavBackStackEntry,
  val scope: AnimatedContentScope,
)