package com.cyxbs.components.config.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.account.api.ITokenService
import com.cyxbs.components.config.service.allImpl
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.init.MainNavController
import com.cyxbs.pages.login.api.LoginArgument
import kotlinx.serialization.Serializable

/**
 * .
 *
 * @author 985892345
 * @date 2025/4/1
 */

/**
 * 主页的路由参数，对应的界面在 [HomeDestination]
 */
@Serializable
object HomeArgument

/**
 * 应用最顶层的 Compose NavHost
 *
 * 如果需要注册页面，详细请查看 [MainDestination]
 */
@Composable
fun MainNavHost() {
  val navController = rememberNavController()
  MainNavController = navController
  val destinations = remember {
    MainDestination::class.allImpl().mapValues { it.value.get() as MainDestination<Any> }
  }
  NavHost(
    navController = navController,
    startDestination = remember {
      if (checkIsLogin() == null) {
        // 未登录则起点是登录页面
        // 这里 targetRoute 因为 navController 还未初始化 graph，所以手动指定为 HomeArgument 的 route
        LoginArgument(HomeArgument.serializer().descriptor.serialName)
      } else HomeArgument
    },
  ) {
    destinations.forEach { (scheme, destination) ->
      composable(
        route = destination.argumentClass,
        typeMap = destination.typeMap,
        deepLinks = destination.extraDeepLinks + navDeepLink(
          // 注册 path 为 @ImplProvider name 的默认 deepLink
          route = destination.argumentClass,
          basePath = "cyxbs://$scheme",
          typeMap = destination.typeMap
        ) {},
        enterTransition = destination::enterTransition,
        exitTransition = destination::exitTransition,
        popEnterTransition = destination::popEnterTransition,
        popExitTransition = destination::popExitTransition,
        sizeTransform = destination::sizeTransform,
        content = {
          val parcel = DestinationParcel<Any>(it.toRoute(destination.argumentClass), it, this)
          destination.content(parcel)
        },
      )
    }
  }
}

private fun checkIsLogin(): Boolean? {
  val accountService = IAccountService::class.impl()
  if (!accountService.isTouristMode()) {
    // 不是游客模式
    if (!accountService.isLogin() || ITokenService::class.impl().isRefreshTokenExpired()) {
      // 未登录 和 refreshToken 过期时 需要跳转到登录界面
      return null
    }
    return true
  }
  return false
}