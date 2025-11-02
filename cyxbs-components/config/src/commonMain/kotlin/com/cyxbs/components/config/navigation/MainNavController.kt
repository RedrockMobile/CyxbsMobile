package com.cyxbs.components.config.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.account.api.ITokenService
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.service.allImpl
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.init.MainNavController
import com.cyxbs.pages.login.api.LoginNavArgument
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * .
 *
 * @author 985892345
 * @date 2025/4/1
 */

/**
 * 主页的路由参数，对应的界面在 [HomeNavDestination]
 */
@Serializable
class HomeNavArgument(
  @SerialName("page")
  val page: String = "discover",
)

/**
 * 应用最顶层的 Compose NavHost
 *
 * 如果需要注册页面，详细请查看 [MainNavDestination]
 */
@Composable
fun MainNavHost() {
  val navController = rememberNavController()
  MainNavController = navController
  val destinations = remember {
    MainNavDestination::class.allImpl().mapValues { it.value.get() as MainNavDestination<Any> }
  }
  val dialogs = remember {
    MainNavDialog::class.allImpl().mapValues { it.value.get() as MainNavDialog<Any> }
  }
  NavHost(
    navController = navController,
    startDestination = if (checkIsLogin() == null) {
      // 未登录则起点是登录页面
      // 这里 targetRoute 因为 navController 还未初始化 graph，所以手动指定为 HomeArgument 的 route
      LoginNavArgument(HomeNavArgument.serializer().descriptor.serialName)
    } else HomeNavArgument(),
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
          val accountStateFlow = remember { IAccountService::class.impl().state }.collectAsState()
          if (!destination.needLogin || accountStateFlow.value is AccountState.Login) {
            // 登录检查
            val parcel = DestinationParcel<Any>(it.toRoute(destination.argumentClass), it, this)
            destination.DestinationContent(parcel)
          } else {
            LoginDialog()
          }
        },
      )
    }
    dialogs.forEach { (scheme, navDialog) ->
      dialog(
        route = navDialog.argumentClass,
        typeMap = navDialog.typeMap,
        deepLinks = navDialog.extraDeepLinks + navDeepLink(
          // 注册 path 为 @ImplProvider name 的默认 deepLink
          route = navDialog.argumentClass,
          basePath = "cyxbs://$scheme",
          typeMap = navDialog.typeMap
        ) {},
        dialogProperties = navDialog.dialogProperties,
      ) {
        val accountStateFlow = remember { IAccountService::class.impl().state }.collectAsState()
        if (!navDialog.needLogin || accountStateFlow.value is AccountState.Login) {
          // 登录检查
          val parcel = DialogDestinationParcel<Any>(it.toRoute(navDialog.argumentClass), it)
          navDialog.DialogContent(parcel)
        } else {
          LoginDialog()
        }
      }
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

@Composable
private fun LoginDialog() {
  Dialog(
    properties = DialogProperties(
      dismissOnBackPress = true,
      dismissOnClickOutside = false,
    ),
    onDismissRequest = {
      MainNavController.popBackStack()
    },
  ) {
    Box(
      modifier = Modifier.width(300.dp).height(150.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(LocalAppColors.current.topBg),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
          modifier = Modifier.fillMaxWidth().weight(1F),
          contentAlignment = Alignment.Center
        ) {
          Text(text = "请先登录才能使用此功能哦~", fontSize = 14.sp, color = LocalAppColors.current.tvLv4)
        }
        Box(
          modifier = Modifier.padding(bottom = 30.dp)
            .width(80.dp)
            .height(34.dp)
            .clip(MaterialTheme.shapes.large)
            .background(LocalAppColors.current.positive)
            .clickable {
              LoginNavArgument.navigate(target = null, clearStack = false) // 打开登录页
            },
          contentAlignment = Alignment.Center
        ) {
          Text(text = "去登录", color = Color.White)
        }
      }
    }
  }
}