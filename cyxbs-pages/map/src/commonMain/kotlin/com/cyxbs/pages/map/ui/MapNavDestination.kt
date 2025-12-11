package com.cyxbs.pages.map.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.DropdownMenu
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.login.rememberLoginDialogState
import com.cyxbs.components.config.navigation.DestinationParcel
import com.cyxbs.components.config.navigation.MainNavDestination
import com.cyxbs.components.config.navigation.NAV_MAP
import com.cyxbs.components.config.res.ConfigRes
import com.cyxbs.components.init.MainNavController
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.clickableSingle
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.map.api.MapNavArgument
import com.cyxbs.pages.map.model.MapDataRepository
import com.cyxbs.pages.map.util.BackHandler
import com.cyxbs.pages.map.util.MapImageHelper
import com.cyxbs.pages.map.util.clickAnimation
import com.cyxbs.pages.map.util.clickCompass
import com.cyxbs.pages.map.util.getImageFile
import com.cyxbs.pages.map.util.isFileExist
import com.cyxbs.pages.map.viewmodel.MapComposeViewModel
import com.cyxbs.pages.map.widget.MapWidgetCompose
import com.cyxbs.pages.map.widget.PlaceDetailBottomSheet
import com.g985892345.provider.api.annotation.ImplProvider
import cyxbsmobile.cyxbs_pages.map.generated.resources.Res
import cyxbsmobile.cyxbs_pages.map.generated.resources.map_ic_compass
import cyxbsmobile.cyxbs_pages.map.generated.resources.map_ic_hot
import cyxbsmobile.cyxbs_pages.map.generated.resources.map_ic_lock
import cyxbsmobile.cyxbs_pages.map.generated.resources.map_ic_my_favorite
import cyxbsmobile.cyxbs_pages.map.generated.resources.map_ic_search_clear
import cyxbsmobile.cyxbs_pages.map.generated.resources.map_ic_search_edit_text_icon
import cyxbsmobile.cyxbs_pages.map.generated.resources.map_ic_unlock
import cyxbsmobile.cyxbs_pages.map.generated.resources.map_ic_vr
import cyxbsmobile.cyxbs_pages.map.generated.resources.map_ic_vr_description
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.vectorResource

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
    viewModel { MapComposeViewModel() } // wasm 无法反射 new 对象，这里需要提供 factory
    MapCompose(parcel.argument)
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
      MapContent()
    }
  }
  BackHandler {
    if (viewmodel.mapPagerState.value == 1) {
      viewmodel.mapPagerState.value = 0
    } else {
      MainNavController.popBackStack()
    }
  }
}

@Composable
fun MapContent(modifier: Modifier = Modifier) {
  val viewmodel = viewModel(MapComposeViewModel::class)
  Column(
    modifier = Modifier
      .fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(LocalAppColors.current.whiteBlack)
        .statusBarsPadding(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Image(
        modifier = Modifier
          .padding(start = 6.dp, top = 6.dp)
          .width(30.dp)
          .height(30.dp)
          .clickableNoIndicator {
            if (viewmodel.mapSearchPagerState.value == 1) {
              viewmodel.mapSearchPagerState.value = 0
            } else {
              MainNavController.popBackStack()
            }
          }
          .padding(start = 10.dp, end = 10.dp),
        painter = painterResource(ConfigRes.configIcBack()),
        contentDescription = null
      )
      SearchBar(
        modifier = Modifier
          .padding(start = 6.dp, top = 6.dp, end = 16.dp)
          .fillMaxWidth()
          .height(36.dp)
      )
    }
    AnimatedContent(
      targetState = viewmodel.mapSearchPagerState.value,
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
        SearchCompose(
          Modifier
            .padding(start = 6.dp, top = 6.dp, end = 16.dp)
            .fillMaxSize()
        )
      } else {
        Column(
          modifier = Modifier.fillMaxWidth()
        ) {
          SymbolListCompose()
          MapFunctionImageCompose(
            modifier = Modifier
              .padding(top = 32.dp)
              .background(Color.Transparent)
          )
          PlaceDetailBottomSheet()
        }
      }
    }
  }
}

@Composable
fun SearchBar(modifier: Modifier = Modifier) {
  val viewmodel = viewModel(MapComposeViewModel::class)
  BasicTextField(
    modifier = modifier
      .background(
        color = 0xFFF0F4FD.dark(0xFF202020),
        shape = RoundedCornerShape(20.dp)
      )
      .onFocusChanged { focusState ->
        if (focusState.isFocused) {
          viewmodel.mapSearchPagerState.value = 1
        }
      }
      .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
    value = viewmodel.searchText.value,
    onValueChange = {
      viewmodel.searchText.value = it
    },
    textStyle = TextStyle(
      fontSize = 14.sp,
      color = 0xFF16305C.dark(0xFFF0F0F2)
    ),
    maxLines = 1,
    cursorBrush = SolidColor(0xFF788EFA.dark(0xFFFFFFFF)),
    decorationBox = { innerTextField ->
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
      ) {
        Image(
          painter = painterResource(Res.drawable.map_ic_search_edit_text_icon),
          contentDescription = null
        )
        Box(
          modifier = Modifier.padding(start = 4.dp, end = 4.dp).weight(1f)
        ) {
          if (viewmodel.searchText.value.isEmpty()) {
            Text(
              color = 0xFF94969E.dark(0xFF8C8C8C),
              text = getHotWord(viewmodel.mapInfo.value?.hotWord),
              fontSize = 14.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          innerTextField()
        }
        if (viewmodel.searchText.value.isNotEmpty()) {
          Icon(
            modifier = Modifier
              .padding(start = 4.dp, end = 4.dp)
              .clickableNoIndicator {
                viewmodel.searchText.value = ""
              },
            imageVector = vectorResource(Res.drawable.map_ic_search_clear),
            tint = 0xFF16305C.dark(0xFFF0F0F2),
            contentDescription = null
          )
        }
      }
    }
  )
  LaunchedEffect(viewmodel.searchText.value) {
    delay(500)
    viewmodel.search()
  }
}

fun getHotWord(text: String? = null): String {
  return "大家都在搜：" + (text ?: "风雨操场")
}

@Composable
fun MapFunctionImageCompose(modifier: Modifier = Modifier) {
  val viewmodel = viewModel(MapComposeViewModel::class)
  Row(
    modifier = modifier
  ) {
    Column(
      modifier = Modifier.padding(start = 16.dp, top = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Image(
        modifier = Modifier
          .size(36.dp)
          .clickAnimation {
            viewmodel.jumpToVR()
          },
        painter = painterResource(Res.drawable.map_ic_vr),
        contentDescription = null
      )
      Image(
        modifier = Modifier.width(46.dp).height(36.dp),
        painter = painterResource(Res.drawable.map_ic_vr_description),
        contentDescription = null
      )
    }
    Spacer(modifier = Modifier.weight(1f))
    Column(
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Image(
        modifier = Modifier.padding(end = 16.dp).clickCompass(),
        painter = painterResource(Res.drawable.map_ic_compass),
        contentDescription = null
      )
      Image(
        modifier = Modifier
          .padding(top = 20.dp, end = 16.dp)
          .clickAnimation {
            viewmodel.changeLockStatus()
          }
          .size(36.dp),
        painter = painterResource(if (viewmodel.mapWidgetState.isLock) Res.drawable.map_ic_lock else Res.drawable.map_ic_unlock),
        contentDescription = null
      )
    }
  }
}

@Composable
fun SymbolListCompose(modifier: Modifier = Modifier) {
  val scope = rememberCoroutineScope()
  val viewmodel = viewModel(MapComposeViewModel::class)
  val selectedColor = 0xFF2A4E84.dark(0xFFF0F0F2)
  val backgroundColor = 0xFFE8F0FC.dark(0xFF404040)
  val loginDialogState = rememberLoginDialogState()
  val expandedCollect = remember { mutableStateOf(false) }
  Row(
    modifier = modifier
      .background(LocalAppColors.current.whiteBlack)
      .padding(top = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    LazyRow(
      modifier = Modifier
        .padding(start = 8.dp)
        .weight(1f)
        .height(54.dp)
        .padding(start = 8.dp, end = 1.dp)
    ) {
      items(
        count = viewmodel.buttonInfoItemList.size,
        key = { id -> viewmodel.buttonInfoItemList[id].title }
      ) { index ->
        val item = viewmodel.buttonInfoItemList[index]
        Box(
          modifier = Modifier
            .height(54.dp)
            .clickAnimation {
              if (viewmodel.mapWidgetState.isLock) viewmodel.changeLockStatus()
              if (viewmodel.currentSelectedItem.value != index) {
                viewmodel.showAnchorList(scope, index)
                viewmodel.currentSelectedItem.value = index
              } else {
                viewmodel.closeAnchorList(scope)
                viewmodel.currentSelectedItem.value = 999
              }
            }
            .padding(start = 14.dp)
        ) {
          Text(
            modifier = Modifier
              .align(Alignment.Center)
              .background(
                color = if (viewmodel.currentSelectedItem.value == index) backgroundColor else Color.Transparent,
                shape = RoundedCornerShape(200.dp)
              )
              .padding(start = 7.dp, end = 7.dp),
            text = item.title,
            fontSize = 14.sp,
            color = if (viewmodel.currentSelectedItem.value == index) selectedColor else LocalAppColors.current.tvLv2,
            textAlign = TextAlign.Center
          )
          if (item.isHot) {
            Image(
              modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 8.dp, y = 8.dp)
                .width(22.dp)
                .height(12.dp),
              painter = painterResource(Res.drawable.map_ic_hot),
              contentDescription = null
            )
          }
        }
      }
    }
    Box(
      modifier = Modifier
        .padding(end = 8.dp)
        .height(54.dp)
        .width(1.dp)
        .background(0xFFEFF3FD.dark(0xFF202020))
    )
    Box {
      Column(
        modifier = Modifier
          .padding(end = 8.dp)
          .height(54.dp)
          .clickAnimation {
            loginDialogState.doIfLogin(
              msg = "收藏"
            ) {
              expandedCollect.value = true
              if (viewmodel.mapWidgetState.isLock) {
                toast("已解除锁定")
                viewmodel.mapWidgetState.isLock = false
              }
              viewmodel.showCollectList(scope)
            }
          }
          .height(54.dp)
          .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Image(
          modifier = Modifier
            .width(23.dp)
            .height(17.dp),
          painter = painterResource(Res.drawable.map_ic_my_favorite),
          contentDescription = null
        )
        Text(
          modifier = Modifier.padding(top = 6.dp),
          fontSize = 11.sp,
          text = "我的收藏",
          color = LocalAppColors.current.tvLv2
        )
      }
      viewmodel.mapInfo.value?.let { mapInfo ->
        DropdownMenu(
          expanded = expandedCollect.value,
          onDismissRequest = { expandedCollect.value = false },
          offset = DpOffset(x = (-8).dp, y = 8.dp),
          modifier = Modifier
            .heightIn(max = 240.dp)
            .background(
              color = LocalAppColors.current.whiteBlack,
              shape = RoundedCornerShape(8.dp)
            )
        ) {
          viewmodel.collectListState.forEach {
            mapInfo.placeList.find { placeItem ->
              placeItem.placeId == it
            }?.let { placeItem ->
              Column(
                modifier = Modifier
                  .width(135.dp)
                  .clickableSingle {
                    expandedCollect.value = false
                    viewmodel.focusOnPlace(placeItem, scope)
                  },
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Text(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 16.dp),
                  text = placeItem.placeName,
                  fontSize = 15.sp,
                  color = LocalAppColors.current.tvLv2,
                  textAlign = TextAlign.Center,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                if (viewmodel.collectListState.last() != it) {
                  Box(
                    modifier = Modifier
                      .padding(start = 16.dp, end = 16.dp)
                      .height(1.dp)
                      .fillMaxWidth()
                      .background(0xFFEFF3FD.dark(0xFF202020))
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun MapCompose(argument: MapNavArgument, modifier: Modifier = Modifier) {
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
        if (!isFileExist() || MapDataRepository.getMapVersion() == null) {
          viewmodel.progressDialogState.value = true
          MapImageHelper.downloadImage(mapInfo.mapUrl) { bytesRead, contentLength ->
            viewmodel.downloadProgress.value = bytesRead.toFloat() / contentLength.toFloat()
          }
          value = getImageFile()
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
              value = getImageFile()
            } else {
              viewmodel.updateMapDialogState.value = true
            }
          }
        }
      } else {
        viewmodel.progressDialogState.value = true
        MapImageHelper.downloadImage(mapInfo.mapUrl) { bytesRead, contentLength ->
          viewmodel.downloadProgress.value = bytesRead.toFloat() / contentLength.toFloat()
        }
        value = getImageFile()
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
    modifier = modifier
      .background(Color(0xFFA8BCF1))
  ) {
    // 这里如果统一对imageResult作处理会导致更新地图的下载会模糊，只能暂时这样写了
    viewmodel.mapInfo.value?.let { mapInfo ->
      imageResult?.let { imageResult ->
        MapWidgetCompose(
          inputStream = imageResult,
          mapInfo = mapInfo,
          mapWidgetState = viewmodel.mapWidgetState,
          anchorItemState = viewmodel.anchorItemState,
          anchorItemStateList = viewmodel.anchorItemStateList
        )
      } ?: run {
        if (isImageLocalExist.value && !viewmodel.updateMapDialogState.value) {
          MapWidgetCompose(
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
      isImageLocalExist.value = isFileExist()
      localImage.value = getImageFile()
    }
    launch {
      snapshotFlow { viewmodel.mapContainer.value }
        .filter { it != IntSize.Zero }
        .take(1)
        .collect {
          // 这里如果立即执行会导致图片加载很奇怪，推测是立即执行时布局还在调整，故这里延迟200ms
          delay(200)
          viewmodel.mapInfo.value?.let {
            argument.placeSearch?.let { placeSearch ->
              viewmodel.placeSearch(this, placeSearch)
            } ?: run {
              viewmodel.initFocus(this, it.openSiteId)
            }
          }
        }
    }
  }
}
