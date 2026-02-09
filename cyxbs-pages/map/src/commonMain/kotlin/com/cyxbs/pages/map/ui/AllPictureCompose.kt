package com.cyxbs.pages.map.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.login.rememberLoginDialogState
import com.cyxbs.components.config.res.ConfigRes
import com.cyxbs.components.init.MainNavController
import com.cyxbs.components.utils.compose.clickableSingle
import com.cyxbs.components.utils.extensions.ImageFromUrlCompose
import com.cyxbs.pages.map.viewmodel.MapComposeViewModel
import cyxbsmobile.cyxbs_pages.map.generated.resources.Res
import cyxbsmobile.cyxbs_pages.map.generated.resources.map_ic_share_blue
import org.jetbrains.compose.resources.painterResource

/**
 * @Desc : 所有图片页面
 * @Author : zzx
 * @Date : 2025/12/2 13:35
 */

@Composable
fun AllPictureCompose(modifier: Modifier = Modifier) {
  val viewmodel = viewModel(MapComposeViewModel::class)
  val showState = remember { mutableStateOf(false) }
  val loginDialogState = rememberLoginDialogState()
  Column(
    modifier = modifier.background(LocalAppColors.current.topBg)
  ) {
    Image(
      modifier = Modifier
        .padding(start = 6.dp, top = 35.dp)
        .clickableSingle {
          viewmodel.mapPagerState.value = 0
        }
        .size(30.dp)
        .padding(start = 10.dp, end = 10.dp),
      painter = painterResource(ConfigRes.configIcBack()),
      contentDescription = null
    )
    Row(
      modifier = Modifier
        .padding(start = 15.dp, top = 35.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "所有图片",
        fontSize = 23.sp,
        color = LocalAppColors.current.tvLv2
      )
      Spacer(modifier = Modifier.weight(1f))
      Row(
        modifier = Modifier
          .padding(end = 15.dp)
          .clickableSingle {
            loginDialogState.doIfLogin(
              msg = "上传图片"
            ) {
              showState.value = true
            }
          }
          .padding(top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Image(
          modifier = Modifier.padding(end = 8.dp).size(16.dp),
          painter = painterResource(Res.drawable.map_ic_share_blue),
          contentDescription = null
        )
        Text(
          text = "与大家分享你拍摄的此地点",
          fontSize = 13.sp,
          color = Color(0xFF02CAF0)
        )
      }
    }
    viewmodel.placeDetails.value?.images?.let { images ->
      LazyVerticalGrid(
        modifier = modifier.padding(10.dp),
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        items(
          count = images.size,
          key = { id -> images[id] }
        ) { index ->
          ImageFromUrlCompose(
            url = images[index],
            modifier = Modifier
              .aspectRatio(4f / 3f)
              .clip(RoundedCornerShape(10.dp))
              .clickableSingle {
                MainNavController.navigate(MapShowPictureArgument(images, index))
              },
            contentScale = ContentScale.Crop
          )
        }
      }
    }
  }
  UploadPhotoDialog(showState)
  UploadPhotoResult(viewmodel.uploadPhotoResultState)
  UploadingPhotoProgressDialog()
}