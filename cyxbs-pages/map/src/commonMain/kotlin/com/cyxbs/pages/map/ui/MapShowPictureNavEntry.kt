package com.cyxbs.pages.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_MAP_SHOW_PICTURE
import com.cyxbs.components.utils.compose.clickableSingle
import com.cyxbs.components.utils.extensions.ImageFromUrlCompose
import com.cyxbs.pages.map.widget.BannerCompose
import com.cyxbs.pages.map.widget.rememberBannerPagerState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @Desc : 展示map图片
 * @Author : zzx
 * @Date : 2025/12/1 21:52
 */

@Serializable
class MapShowPictureNavArgument(
  @SerialName("imageList")
  val imageList: List<String>,
  @SerialName("currentIndex")
  val currentIndex: Int
) : AppNavArgument

@AppNav(route = NAV_MAP_SHOW_PICTURE)
class MapShowPictureNavEntry : AppNavEntry<MapShowPictureNavArgument>() {

  override fun isNeedLogin(argument: MapShowPictureNavArgument): Boolean {
    return false
  }

  @Composable
  override fun Content(argument: MapShowPictureNavArgument) {
    val images = mutableStateListOf<String>().apply {
      addAll(argument.imageList)
    }
    val currentIndex = remember { mutableStateOf(argument.currentIndex) }
    val pagerState = rememberBannerPagerState(
      pageCount = images.size,
      isScrollInfinite = true,
      initialPage = currentIndex.value
    )
    Column(
      modifier = Modifier
        .clickableSingle {
          argument.popBackStack()
        }
        .fillMaxSize()
        .background(Color.Black)
        .systemBarsPadding(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        modifier = Modifier
          .padding(top = 15.dp),
        text = "${pagerState.currentPage % images.size + 1}/${images.size}",
        color = Color.White
      )
      Box(
        modifier = Modifier.fillMaxSize()
      ) {
        BannerCompose(
          pageCount = images.size,
          pagerState = pagerState,
          modifier = Modifier.fillMaxSize().align(Alignment.Center)
        ) { index, virtualIndex ->
          ImageFromUrlCompose(
            url = images[index],
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth
          )
        }
      }
    }
  }

}