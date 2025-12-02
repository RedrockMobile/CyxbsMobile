package com.cyxbs.pages.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.cyxbs.components.config.navigation.DestinationParcel
import com.cyxbs.components.config.navigation.MainNavDestination
import com.cyxbs.components.config.navigation.NAV_MAP_SHOW_PICTURE
import com.cyxbs.components.init.MainNavController
import com.cyxbs.components.utils.compose.clickableSingle
import com.cyxbs.components.utils.extensions.ImageFromUrlCompose
import com.cyxbs.components.utils.extensions.logg
import com.cyxbs.pages.map.widget.BannerCompose
import com.cyxbs.pages.map.widget.bannerTransition
import com.cyxbs.pages.map.widget.rememberBannerPagerState
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @Desc : 展示map图片
 * @Author : zzx
 * @Date : 2025/12/1 21:52
 */

@Serializable
class MapShowPictureArgument(
  @SerialName("imageList")
  val imageList: List<String>,
  @SerialName("currentIndex")
  val currentIndex: Int
)

@ImplProvider(clazz = MainNavDestination::class, name = NAV_MAP_SHOW_PICTURE)
class MapShowPictureNavDestination : MainNavDestination<MapShowPictureArgument>(
  MapShowPictureArgument::class
) {

  override val needLogin: Boolean
    get() = false

  @Composable
  override fun DestinationContent(parcel: DestinationParcel<MapShowPictureArgument>) {
    val images = mutableStateListOf<String>().apply {
      addAll(parcel.argument.imageList)
    }
    val currentIndex = remember { mutableStateOf(parcel.argument.currentIndex) }
    val pagerState = rememberBannerPagerState(
      pageCount = images.size,
      isScrollInfinite = true,
      initialPage = currentIndex.value
    )
    Box(
      modifier = Modifier
        .clickableSingle {
          MainNavController.popBackStack()
        }
        .fillMaxSize()
        .background(Color.Black)
    ) {
      Text(
        modifier = Modifier
          .padding(top = 15.dp)
          .align(Alignment.TopCenter),
        text = "${pagerState.currentPage % images.size + 1}/${images.size}",
        color = Color.White
      )
      BannerCompose(
        pageCount = images.size,
        pagerState = pagerState,
        modifier = Modifier.fillMaxSize()
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