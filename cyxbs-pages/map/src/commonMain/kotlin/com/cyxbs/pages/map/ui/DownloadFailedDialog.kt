package com.cyxbs.pages.map.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.view.ui.ChooseDialogCompose
import com.cyxbs.pages.map.api.MapNavArgument
import com.cyxbs.pages.map.viewmodel.MapComposeViewModel

/**
 * @Desc : 下载失败的dialog
 * @Author : zzx
 * @Date : 2025/11/21 16:00
 */

@Composable
fun DownloadFailedDialog(argument: MapNavArgument) {
  val viewmodel = viewModel(MapComposeViewModel::class)
  ChooseDialogCompose(
    showState = viewmodel.downloadFailedDialogState,
    onClickPositiveBtn = {
      argument.popBackStack()
      viewmodel.downloadFailedDialogState.value = false
    }
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = "地图下载失败",
        fontSize = 18.sp,
        color = LocalAppColors.current.tvLv4,
        modifier = Modifier.padding(top = 28.dp)
      )
      Text(
        text = "点击确定后退出",
        fontSize = 16.sp,
        color = LocalAppColors.current.tvLv4,
        modifier = Modifier.padding(top = 21.dp, bottom = 36.dp)
      )
    }
  }
}