package com.cyxbs.pages.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.view.ui.ChooseDialogCompose
import com.cyxbs.pages.map.viewmodel.MapComposeViewModel
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher

/**
 * @Desc : 上传图片的dialog
 * @Author : zzx
 * @Date : 2025/12/10 11:03
 */

@Composable
fun UploadPhotoDialog(
  showState: MutableState<Boolean>
) {
  val viewmodel = viewModel(MapComposeViewModel::class)
  val mode = FileKitMode.Multiple(maxItems = 9)
  val launcher = rememberFilePickerLauncher(
    type = FileKitType.Image,
    mode = mode
  ) { imageList ->
    viewmodel.uploadPhoto(imageList)
  }
  ChooseDialogCompose(
    showState = showState,
    positiveBtnText = "确定",
    negativeBtnText = "取消",
    onClickPositiveBtn = {
      launcher.launch()
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
        text = "上传图片",
        fontSize = 18.sp,
        color = LocalAppColors.current.tvLv4,
        modifier = Modifier.padding(top = 28.dp)
      )
      Text(
        text = "在这里，和邮子们共同分享你们所拍摄的校园风景。上传你的照片，优质照片有机会在此展示",
        fontSize = 16.sp,
        color = LocalAppColors.current.tvLv4,
        modifier = Modifier.padding(top = 21.dp, bottom = 36.dp, start = 24.dp, end = 24.dp),
        textAlign = TextAlign.Center
      )
    }
  }
}

@Composable
fun UploadPhotoResult(
  showState: MutableState<Boolean>
) {
  val viewmodel = viewModel(MapComposeViewModel::class)
  ChooseDialogCompose(
    showState = showState,
    positiveBtnText = "确定",
    onClickPositiveBtn = {
      showState.value = false
    }
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = "上传完成",
        fontSize = 18.sp,
        color = LocalAppColors.current.tvLv4,
        modifier = Modifier.padding(top = 28.dp)
      )
      Text(
        text = "上传的优质照片如果审核通过了，就可以在这里看到啦~\n上传成功数量：${viewmodel.successImageCount}\n上传失败数量：${viewmodel.failedImageCount}",
        fontSize = 16.sp,
        color = LocalAppColors.current.tvLv4,
        modifier = Modifier.padding(top = 21.dp, bottom = 36.dp, start = 24.dp, end = 24.dp),
        textAlign = TextAlign.Center
      )
    }
  }
}

@Composable
fun UploadingPhotoProgressDialog() {
  val viewmodel = viewModel(MapComposeViewModel::class)
  if (viewmodel.uploadingPhotoState.value) {
    Dialog(
      onDismissRequest = { viewmodel.uploadingPhotoState.value = false }
    ) {
      Box(
        modifier = Modifier
          .width(314.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(LocalAppColors.current.topBg)
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = "正在上传图片",
            fontSize = 18.sp,
            color = LocalAppColors.current.tvLv4,
            modifier = Modifier.padding(top = 28.dp)
          )
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(top = 24.dp, bottom = 24.dp, start = 37.dp, end = 37.dp)
          ) {
            CircularProgressIndicator(
              color = Color(0xFF4B44E5),
              backgroundColor = Color(0xFFE7EFFD),
              strokeCap = StrokeCap.Round
            )
            Text(
              text = "请稍后",
              fontSize = 16.sp,
              color = LocalAppColors.current.tvLv4,
              modifier = Modifier.padding(start = 24.dp, end = 24.dp),
              textAlign = TextAlign.Center
            )
          }
        }
      }
    }
  }
}