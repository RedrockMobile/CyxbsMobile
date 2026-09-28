package com.cyxbs.pages.map.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.map.model.MapDataRepository
import com.cyxbs.pages.map.model.MapRepository
import com.cyxbs.pages.map.model.bean.ButtonInfoItem
import com.cyxbs.pages.map.model.bean.MapInfo
import com.cyxbs.pages.map.model.bean.PlaceItem
import com.cyxbs.pages.map.util.calculateClickBuildingInMap
import com.cyxbs.pages.map.util.calculateClickTagInMap
import com.cyxbs.pages.map.util.calculateOriginPosition
import com.cyxbs.pages.map.util.calculatePlaceInMap
import com.cyxbs.pages.map.widget.AnchorItemState
import com.cyxbs.pages.map.widget.MapUiEvent
import com.cyxbs.pages.map.widget.MapWidgetState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeoutOrNull
import com.cyxbs.pages.map.util.getMilliseconds
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CoroutineStart
import kotlin.time.Duration.Companion.milliseconds

/**
 * @Desc : Map的ViewModel
 * @Author : zzx
 * @Date : 2025/11/18 10:48
 */

class MapComposeViewModel : BaseViewModel() {

  companion object {
    const val NETWORK_ERROR_INFO = "服务君似乎打盹了呢"
    const val MIN_SCALE = 1f

    private val _navEvents = MutableSharedFlow<MapNavEvent>(replay = 0)
    val navEvents: SharedFlow<MapNavEvent> = _navEvents.asSharedFlow()

    suspend fun emitNavEvent(event: MapNavEvent) {
      _navEvents.emit(event)
    }
  }

  var maxScale = 6f
  val mapPagerState = mutableStateOf(0)
  val picturePlaceId = mutableStateOf("")
  val pictureImages = mutableStateOf<List<String>>(emptyList())
  val uploadPhotoDialogState = mutableStateOf(false)
  val uploadPlaceId = mutableStateOf("")

  fun requestPhotoUpload(placeId: String) {
    uploadPlaceId.value = placeId
    uploadPhotoDialogState.value = true
  }

  val uploadPhotoResultState = mutableStateOf(false)
  val uploadingPhotoState = mutableStateOf(false)
  var successImageCount = 0
  var failedImageCount = 0

  fun uploadPhoto(uploadPlaceId: String, imageList: List<PlatformFile>?) {
    if (imageList.isNullOrEmpty() || uploadingPhotoState.value) return
    launchByViewModelScope {
      try {
        successImageCount = 0
        failedImageCount = 0
        uploadingPhotoState.value = true
        uploadPhotoResultState.value = false
        for (i in imageList.indices) {
          val file = imageList[i]
          val fileName = getMilliseconds().toString() + "${i}.jpg"
          val fileBytes = file.readBytes()
          val multipartBody = MultiPartFormDataContent(
            formData {
              append("place_id", uploadPlaceId)
              append("file", fileBytes, Headers.build {
                append(HttpHeaders.ContentType, "image/jpeg")
                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
              })
            }
          )
          MapRepository.uploadPhoto(multipartBody).getOrElse { throwable: Throwable ->
            toast("上传第${i}张图片失败！")
            failedImageCount++
            null
          }?.let {
            if (it.isSuccess()) {
              successImageCount++
            } else {
              failedImageCount++
            }
          }
        }
        uploadPhotoResultState.value = true
      } finally {
        uploadingPhotoState.value = false
      }
    }
  }

  // 地图组件状态
  val mapWidgetState = MapWidgetState()

  // 单独记录地图组件宽高
  val mapContainer = mutableStateOf(IntSize.Zero)
  val mapCenter get() = Offset(mapContainer.value.width / 2f, mapContainer.value.height / 2f)

  // 点击锚点状态
  val anchorItemState = AnchorItemState(placeId = "0")

  // 展示锚点集合状态
  val anchorItemStateList = mutableStateListOf<AnchorItemState>()
  val mapInfo = mutableStateOf<MapInfo?>(null)
  val buttonInfoItemList = mutableStateListOf<ButtonInfoItem>()
  private var hasInitializedFocus = false

  // 下载地图进度dialog相关信息
  val progressDialogState = mutableStateOf(false)
  val downloadProgress = mutableStateOf(0f)

  // 下载失败的dialog
  val downloadFailedDialogState = mutableStateOf(false)

  // 地图更新的dialog
  val updateMapDialogState = mutableStateOf(false)
  val isUpdateStart = mutableStateOf(false)

  // 当前button选中项
  val currentSelectedItem = mutableStateOf(999)

  // 收藏列表
  val collectListState = mutableStateListOf<String>()

  private val _mapUiEvent = MutableSharedFlow<MapUiEvent>()
  val mapUiEvent: SharedFlow<MapUiEvent> = _mapUiEvent.asSharedFlow()

  init {
    navEvents.collectLaunch(start = CoroutineStart.UNDISPATCHED) { event ->
      when (event) {
        is MapNavEvent.SelectPlace -> searchToPlace(event.place)
        is MapNavEvent.RequestPhotoUpload -> requestPhotoUpload(event.placeId)
        is MapNavEvent.OpenAllPictures -> {
          picturePlaceId.value = event.placeId
          pictureImages.value = event.images
          mapPagerState.value = 1
        }
        MapNavEvent.CollectionsChanged -> {
          collectListState.clear()
          collectListState.addAll(MapDataRepository.getCollectList().orEmpty())
        }
        else -> Unit
      }
    }
    initMapInfo()
    getButtonInfo()
    if (IAccountService::class.impl().isLogin()) {
      getCollect()
    }
  }

  /**
   * 发送事件
   */
  private fun sendMapUiEvent(event: MapUiEvent) {
    launchByViewModelScope {
      _mapUiEvent.emit(event)
    }
  }

  fun searchToPlace(placeItem: PlaceItem) {
    sendMapUiEvent(
      MapUiEvent.SearchToPlace(
        placeId = placeItem.placeId,
        placeCenterX = placeItem.placeCenterX.toFloat(),
        placeCenterY = placeItem.placeCenterY.toFloat()
      )
    )
  }

  // 从外部跳转而来的placeSearch
  fun placeSearch(placeSearch: String) {
    launchByViewModelScope {
      val placeId = withTimeoutOrNull(1500.milliseconds) {
        MapRepository.placeSearch(placeSearch).getOrElse { throwable ->
          findLocalPlace(placeSearch)?.placeId ?: mapInfo.value?.openSiteId
        }
      } ?: (findLocalPlace(placeSearch)?.placeId ?: mapInfo.value?.openSiteId)
      placeId?.let {
        focusPlaceFromSearch(it)
      }
    }
  }

  // 初始化地图信息
  fun initMapInfo() {
    launchByViewModelScope {
      val localMapInfo = MapDataRepository.getMapInfo()
      localMapInfo?.let {
        mapInfo.value = it
      }
      MapRepository.getMapInfo().getOrElse { throwable ->
        toast(NETWORK_ERROR_INFO)
        localMapInfo
      }?.let {
        mapInfo.value = it
        if (it != localMapInfo) {
          MapDataRepository.saveMapInfo(it)
        }
      } ?: run {
        downloadFailedDialogState.value = true
      }
    }
  }

  private fun findLocalPlace(placeSearch: String): PlaceItem? {
    val keyword = placeSearch.trim()
    if (keyword.isEmpty()) return null
    return mapInfo.value?.placeList?.firstOrNull { placeItem ->
      placeItem.placeId == keyword || placeItem.placeName.contains(keyword, true)
    }
  }

  // 初始化聚焦信息
  fun initFocus(placeId: String) {
    if (hasInitializedFocus) return
    hasInitializedFocus = true
    mapInfo.value?.let { mapInfo ->
      mapInfo.placeList.find {
        it.placeId == placeId
      }?.let { placeItem ->
        focusOnPlace(placeItem)
      }
    }
  }

  // 聚焦于某个地点
  fun focusOnPlace(placeItem: PlaceItem) {
    sendMapUiEvent(
      MapUiEvent.FocusOnPlace(
        placeId = placeItem.placeId,
        placeCenterX = placeItem.placeCenterX.toFloat(),
        placeCenterY = placeItem.placeCenterY.toFloat()
      )
    )
  }

  private fun focusPlaceFromSearch(placeId: String) {
    mapInfo.value?.placeList?.find { it.placeId == placeId }?.let { placeItem ->
      focusOnPlace(placeItem)
    }
  }

  fun calculatePlaceOffset(placeCenterX: Float, placeCenterY: Float): Offset? {
    val mapInfo = mapInfo.value ?: return null
    if (mapContainer.value == IntSize.Zero) return null
    return calculatePlaceInMap(
      Offset(placeCenterX, placeCenterY),
      mapContainer.value,
      IntSize(mapInfo.mapWidth, mapInfo.mapHeight)
    )
  }

  // 获取按钮信息
  fun getButtonInfo() {
    launchByViewModelScope {
      MapDataRepository.getButtonInfo()?.let { buttonInfo ->
        buttonInfoItemList.clear()
        buttonInfoItemList.addAll(buttonInfo.buttonInfo)
      }
      MapRepository.getButtonInfo().getOrElse { throwable ->
        toast(NETWORK_ERROR_INFO)
        MapDataRepository.getButtonInfo()
      }?.let { buttonInfo ->
        if (buttonInfo.buttonInfo != buttonInfoItemList.toList()) {
          buttonInfoItemList.clear()
          buttonInfoItemList.addAll(buttonInfo.buttonInfo)
        }
        if (MapDataRepository.getButtonInfo() != buttonInfo) {
          MapDataRepository.saveButtonInfo(buttonInfo)
        }
      }
    }
  }

  fun getCollect() {
    launchByViewModelScope {
      MapRepository.getCollect().getOrElse { throwable ->
        toast(NETWORK_ERROR_INFO)
        MapDataRepository.getCollectList()
      }?.let {
        collectListState.clear()
        collectListState.addAll(it)
        MapDataRepository.saveCollectList(it)
      }
    }
  }

  fun closeAnchorList() {
    sendMapUiEvent(MapUiEvent.CloseAnchorList)
  }

  fun showCollectList() {
    sendMapUiEvent(MapUiEvent.ShowCollectAnchors)
  }

  // 点击按钮展示anchorList
  fun showAnchorList(index: Int) {
    val buttonInfoItem = buttonInfoItemList[index]
    sendMapUiEvent(
      MapUiEvent.ShowAnchorList(buttonInfoItem.placeIdList)
    )
  }

  // 点击anchorItem
  fun clickAnchorItem(placeId: String, position: Offset) {
    sendMapUiEvent(
      MapUiEvent.OpenPlaceDetail(
        placeId = placeId,
        anchorPositionX = position.x,
        anchorPositionY = position.y
      )
    )
  }

  // 点击地图后的判断
  fun clickPlace(offset: Offset, mapInfo: MapInfo) {
    val mapRatio = mapInfo.mapWidth.toFloat() / mapInfo.mapHeight.toFloat()
    // 这里要减去是因为大图组件高为aspectRatio(ratio)，故只会占在中间一部分，故需要减去顶部的一部分距离
    val originOffset = calculateOriginPosition(
      mapWidgetState.center,
      mapWidgetState.offset,
      offset,
      mapWidgetState.scale
    ) - Offset(
      0f,
      (mapWidgetState.container.height.toFloat() - mapWidgetState.container.width.toFloat() / mapRatio) / 2f
    )
    var isFind = false
    var realOffset = Offset.Zero
    var placeId = anchorItemState.placeId
    mapInfo.placeList.forEach { placeItem ->
      if (!isFind) {
        // 先寻找标签
        val getOffset = calculateClickTagInMap(
          currentOffset = originOffset,
          placeItem = placeItem,
          containerSize = mapContainer.value,
          mapSize = IntSize(mapInfo.mapWidth, mapInfo.mapHeight)
        )
        if (getOffset != Offset.Zero) {
          realOffset = getOffset
          placeId = placeItem.placeId
          isFind = true
        } else {
          // 再寻找建筑
          placeItem.buildingList.forEach { placeBuildingItem ->
            val getOffsetFromBuilding = calculateClickBuildingInMap(
              currentOffset = originOffset,
              placeItem = placeItem,
              placeBuildingItem = placeBuildingItem,
              containerSize = mapContainer.value,
              mapSize = IntSize(mapInfo.mapWidth, mapInfo.mapHeight)
            )
            if (getOffsetFromBuilding != Offset.Zero) {
              realOffset = getOffsetFromBuilding
              placeId = placeItem.placeId
              isFind = true
            }
          }
        }
      }
    }
    currentSelectedItem.value = 999
    anchorItemState.placeId = placeId // 更新一下对应的placeId
    sendMapUiEvent(
      MapUiEvent.ClickMapPlace(
        placeId = placeId.takeIf { isFind },
        focusOffsetX = realOffset.x.takeIf { isFind },
        focusOffsetY = realOffset.y.takeIf { isFind }
      )
    )
  }

  // 改变锁定的状态
  fun changeLockStatus() {
    if (mapWidgetState.isLock) {
      toast("已解除锁定")
    } else {
      toast("已锁定")
    }
    mapWidgetState.isLock = !mapWidgetState.isLock
  }

  // 还原地图为初始状态
  fun resetMap() {
    sendMapUiEvent(MapUiEvent.ResetMap)
  }

  // 将地图中心移至某点
  fun animateMapToPosition(offset: Offset) {
    sendMapUiEvent(
      MapUiEvent.AnimateMapToPosition(
        offsetX = offset.x,
        offsetY = offset.y
      )
    )
  }

}
