package com.cyxbs.pages.map.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
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
 * @Desc : 类的描述
 * @Author : zzx
 * @Date : 2025/12/8 16:46
 */

@Composable
fun ClearAllHistoryDialog(
  showState: MutableState<Boolean>
) {
  val viewmodel = viewModel(MapComposeViewModel::class)
  ChooseDialogCompose(
    showState = showState,
    positiveBtnText = "确定",
    negativeBtnText = "取消",
    onClickPositiveBtn = {
      viewmodel.clearSearchHistory()
      showState.value = false
    },
    onClickNegativeBtn = {
      showState.value = false
    }
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = "提示",
        fontSize = 18.sp,
        color = LocalAppColors.current.tvLv4,
        modifier = Modifier.padding(top = 28.dp)
      )
      Text(
        text = "真的要清空搜索记录吗？",
        fontSize = 16.sp,
        color = LocalAppColors.current.tvLv4,
        modifier = Modifier.padding(top = 21.dp, bottom = 36.dp, start = 24.dp, end = 24.dp),
        textAlign = TextAlign.Center
      )
    }
  }
}