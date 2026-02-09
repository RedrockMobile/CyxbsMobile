package com.cyxbs.pages.map.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.view.ui.ChooseDialogCompose
import com.cyxbs.pages.map.viewmodel.MapComposeViewModel

/**
 * @Desc : 地图更新的Dialog
 * @Author : zzx
 * @Date : 2025/11/25 20:08
 */

@Composable
fun MapUpdateDialog() {
  val viewmodel = viewModel(MapComposeViewModel::class)
  ChooseDialogCompose(
    showState = viewmodel.updateMapDialogState,
    positiveBtnText = "立即更新",
    negativeBtnText = "下次再说",
    onClickPositiveBtn = {
      viewmodel.isUpdateStart.value = true
      viewmodel.updateMapDialogState.value = false
    },
    onClickNegativeBtn = {
      viewmodel.updateMapDialogState.value = false
    }
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = "地图更新",
        fontSize = 18.sp,
        color = LocalAppColors.current.tvLv4,
        modifier = Modifier.padding(top = 28.dp)
      )
      Text(
        text = "有最新的地图信息可用，推荐更新获取校内最新的地点信息",
        fontSize = 16.sp,
        color = LocalAppColors.current.tvLv4,
        modifier = Modifier.padding(top = 21.dp, bottom = 36.dp, start = 24.dp, end = 24.dp),
        textAlign = TextAlign.Center
      )
    }
  }
}