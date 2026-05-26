package com.cyxbs.pages.login.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.account.api.ILoginDialogContent
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.pages.login.api.LoginNavArgument
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * .
 *
 * @author 985892345
 * @date 2026/5/25
 */
@ImplProvider
object LoginDialogContentImpl : ILoginDialogContent {
  @Composable
  override fun Content(function: String?, clickLoginObserver: (() -> Unit)?) {
    LoginDialogContent(function, clickLoginObserver)
  }
}

@Composable
private fun LoginDialogContent(function: String?, clickLoginObserver: (() -> Unit)?) {
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
        Text(text = "请先登录才能使用${function ?: "此功能"}哦~", fontSize = 14.sp, color = LocalAppColors.current.tvLv4)
      }
      Box(
        modifier = Modifier.padding(bottom = 30.dp)
          .width(80.dp)
          .height(34.dp)
          .clip(MaterialTheme.shapes.large)
          .background(LocalAppColors.current.positive)
          .clickable {
            clickLoginObserver?.invoke()
            LoginNavArgument.navigate(target = null, clearStack = false) // 打开登录页
          },
        contentAlignment = Alignment.Center
      ) {
        Text(text = "去登录", color = Color.White)
      }
    }
  }
}