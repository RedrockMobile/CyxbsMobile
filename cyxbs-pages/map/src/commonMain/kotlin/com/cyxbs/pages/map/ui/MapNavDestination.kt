package com.cyxbs.pages.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cyxbs.components.config.navigation.DestinationParcel
import com.cyxbs.components.config.navigation.MainNavDestination
import com.cyxbs.components.config.navigation.NAV_MAP
import com.cyxbs.components.init.MainNavController
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.map.api.MapNavArgument
import com.cyxbs.pages.map.util.getImage
import com.cyxbs.pages.map.util.isMapLocalExist
import com.cyxbs.pages.map.util.loadImage
import com.cyxbs.pages.map.widget.MapWidget
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * @Desc : Map界面导航
 * @Author : zzx
 * @Date : 2025/11/10 11:42
 */

@ImplProvider(clazz = MainNavDestination::class, name = NAV_MAP)
class MapNavDestination : MainNavDestination<MapNavArgument>(MapNavArgument::class) {

  override val needLogin: Boolean
    get() = false

  @Composable
  override fun DestinationContent(parcel: DestinationParcel<MapNavArgument>) {
    val url = "http://cdn.redrock.team/magipoke_intergral_item.ecdd194116e943e3a7f854fd906c7c49"
    var isLoad by remember { mutableStateOf(false) }
    val imageResult = remember { mutableStateOf<ByteArray?>(null) }
    //"http://cdn.redrock.team/magipoke_intergral_item.ecdd194116e943e3a7f854fd906c7c49"
    if (isMapLocalExist()) {
      MapWidget(getImage())
    } else {
      if (isLoad) {
        MapWidget(imageResult.value)
      }
      LaunchedEffect(Unit) {
        val bytes = loadImage(url) { bytesRead, contentLength ->

        }
        if (bytes == null) {
          toast("地图加载失败!请返回重试")
          MainNavController.popBackStack()
        } else {
          imageResult.value = bytes
          isLoad = true
        }
      }
    }

  }
}
