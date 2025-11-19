package com.cyxbs.pages.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.navigation.DestinationParcel
import com.cyxbs.components.config.navigation.MainNavDestination
import com.cyxbs.components.config.navigation.NAV_MAP
import com.cyxbs.components.init.MainNavController
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.map.api.MapNavArgument
import com.cyxbs.pages.map.util.getImage
import com.cyxbs.pages.map.util.isMapLocalExist
import com.cyxbs.pages.map.util.loadImage
import com.cyxbs.pages.map.viewmodel.MapComposeViewModel
import com.cyxbs.pages.map.widget.MapWidgetCompose
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take

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
    viewModel { MapComposeViewModel() }
    MapCompose()
  }
}

@Composable
fun MapCompose(modifier: Modifier = Modifier) {
  val viewmodel = viewModel(MapComposeViewModel::class)
  val imageResult by produceState<ByteArray?>(null, viewmodel.mapInfo.value) {
    viewmodel.mapInfo.value?.let { mapInfo ->
      if (!isMapLocalExist()) {
        value = loadImage(mapInfo.mapUrl) { bytesRead, contentLength ->
          // TODO 引入下载进度dialog

        }
        value?.let {
          toast("地图加载失败！请返回重试")
          MainNavController.popBackStack()
        }
      }
    }
  }
  viewmodel.mapInfo.value?.let { mapInfo ->
    val bytes = imageResult ?: if (isMapLocalExist()) getImage() else null
    bytes?.let { bytes ->
      MapWidgetCompose(
        modifier = modifier,
        inputStream = bytes,
        mapInfo = mapInfo,
        mapWidgetState = viewmodel.mapWidgetState,
        anchorItemState = viewmodel.anchorItemState,
        anchorItemStateList = viewmodel.anchorItemStateList
      )
    }
  }
  // 初次加载的focus地点
  LaunchedEffect(Unit) {
    snapshotFlow { viewmodel.mapContainer.value }
      .filter { it != IntSize.Zero }
      .take(1)
      .collect {
        // 这里如果立即执行会导致图片加载很奇怪，推测是立即执行时布局还在调整，故这里延迟200ms
        delay(200)
        viewmodel.initFocus(this)
      }
  }
}
