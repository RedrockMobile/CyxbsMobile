package com.cyxbs.pages.map.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.navigation.DestinationParcel
import com.cyxbs.components.config.navigation.MainNavDestination
import com.cyxbs.components.config.navigation.NAV_MAP
import com.cyxbs.pages.map.api.MapNavArgument
import com.cyxbs.pages.map.model.MapDataRepository
import com.cyxbs.pages.map.util.getImage
import com.cyxbs.pages.map.util.isMapLocalExist
import com.cyxbs.pages.map.util.loadImage
import com.cyxbs.pages.map.viewmodel.MapComposeViewModel
import com.cyxbs.pages.map.widget.MapWidgetCompose
import com.cyxbs.pages.map.widget.PlaceDetailBottomSheet
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

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
    viewModel(MapComposeViewModel::class)
    MapCompose()
    MapDestination()
    MapProgressDialog()
    DownloadFailedDialog()
    MapUpdateDialog()
  }
}

@Composable
fun MapDestination() {
  val viewmodel = viewModel(MapComposeViewModel::class)
  AnimatedContent(
    targetState = viewmodel.mapPagerState.value,
    transitionSpec = {
      if (targetState > initialState) {
        slideInHorizontally { width -> width } togetherWith
            slideOutHorizontally { width -> -width }
      } else {
        slideInHorizontally { width -> -width } togetherWith
            slideOutHorizontally { width -> width }
      }
    }
  ) { targetPage ->
    if (targetPage == 1) {
      AllPictureCompose(Modifier.fillMaxSize())
    } else {
      MapContent(Modifier.fillMaxSize())
    }
  }
}

@Composable
fun MapContent(modifier: Modifier = Modifier) {
  // TODO 顶部组件
  PlaceDetailBottomSheet()
}

@Composable
fun MapCompose(modifier: Modifier = Modifier) {
  val viewmodel = viewModel(MapComposeViewModel::class)
  val localImage = remember { mutableStateOf<ByteArray?>(null) }
  val isImageLocalExist = remember { mutableStateOf(false) }
  val imageResult by produceState<ByteArray?>(
    null,
    viewmodel.mapInfo.value,
    viewmodel.isUpdateStart.value
  ) {
    viewmodel.mapInfo.value?.let { mapInfo ->
      // 如果不是更新地图，就走正常流程，否则就直接下载地图
      if (!viewmodel.isUpdateStart.value) {
        // 本地没有就直接下载,如果本地没有版本号信息也直接走下载
        if (!isMapLocalExist() || MapDataRepository.getMapVersion() == null) {
          viewmodel.progressDialogState.value = true
          value = loadImage(mapInfo.mapUrl) { bytesRead, contentLength ->
            viewmodel.downloadProgress.value = bytesRead.toFloat() / contentLength.toFloat()
          }
          if (value == null) {
            viewmodel.downloadFailedDialogState.value = true
            viewmodel.progressDialogState.value = false
          } else {
            // 下载有数据就把当前版本号存进本地
            MapDataRepository.saveMapVersion(mapInfo.pictureVersion)
            viewmodel.progressDialogState.value = false
          }
        } else {
          // 如果有，则先核对版本，版本对就直接拿缓存，不对就走更新
          MapDataRepository.getMapInfo()?.let {
            if (it.pictureVersion == MapDataRepository.getMapVersion()) {
              value = getImage()
            } else {
              viewmodel.updateMapDialogState.value = true
            }
          }
        }
      } else {
        viewmodel.progressDialogState.value = true
        value = loadImage(mapInfo.mapUrl) { bytesRead, contentLength ->
          viewmodel.downloadProgress.value = bytesRead.toFloat() / contentLength.toFloat()
        }
        if (value == null) {
          viewmodel.downloadFailedDialogState.value = true
          viewmodel.progressDialogState.value = false
        } else {
          MapDataRepository.saveMapVersion(mapInfo.pictureVersion)
          viewmodel.progressDialogState.value = false
        }
      }
    }
  }
  Box(
    modifier = modifier.background(Color(0xFFA8BCF1))
  ) {
    // 这里如果统一对imageResult作处理会导致更新地图的下载会模糊，只能暂时这样写了
    viewmodel.mapInfo.value?.let { mapInfo ->
      imageResult?.let { imageResult ->
        MapWidgetCompose(
          modifier = modifier,
          inputStream = imageResult,
          mapInfo = mapInfo,
          mapWidgetState = viewmodel.mapWidgetState,
          anchorItemState = viewmodel.anchorItemState,
          anchorItemStateList = viewmodel.anchorItemStateList
        )
      } ?: run {
        if (isImageLocalExist.value && !viewmodel.updateMapDialogState.value) {
          MapWidgetCompose(
            modifier = modifier,
            inputStream = localImage.value,
            mapInfo = mapInfo,
            mapWidgetState = viewmodel.mapWidgetState,
            anchorItemState = viewmodel.anchorItemState,
            anchorItemStateList = viewmodel.anchorItemStateList
          )
        }
      }
    }
  }
  // 初次加载的focus地点
  LaunchedEffect(Unit) {
    launch {
      isImageLocalExist.value = isMapLocalExist()
      localImage.value = getImage()
    }
    launch {
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
}
