package com.cyxbs.pages.map.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.view.ui.BottomSheetState
import com.cyxbs.components.view.ui.BottomSheetValueState
import com.cyxbs.pages.map.model.MapDataRepository
import com.cyxbs.pages.map.model.MapRepository
import com.cyxbs.pages.map.model.bean.ButtonInfoItem
import com.cyxbs.pages.map.model.bean.MapInfo
import com.cyxbs.pages.map.model.bean.PlaceDetails
import com.cyxbs.pages.map.model.bean.PlaceItem
import com.cyxbs.pages.map.util.calculateClickBuildingInMap
import com.cyxbs.pages.map.util.calculateClickTagInMap
import com.cyxbs.pages.map.util.calculateOriginPosition
import com.cyxbs.pages.map.util.calculatePlaceInMap
import com.cyxbs.pages.map.widget.AnchorItemState
import com.cyxbs.pages.map.widget.MapWidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * @Desc : Map的ViewModel
 * @Author : zzx
 * @Date : 2025/11/18 10:48
 */

expect class MapComposeViewModel : CommonMapComposeViewModel

abstract class CommonMapComposeViewModel : BaseViewModel() {

  companion object {
    const val NETWORK_ERROR_INFO = "服务君似乎打盹了呢"
    const val MAX_SCALE = 6f
    const val MIN_SCALE = 1f
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

  // 下载地图进度dialog相关信息
  val progressDialogState = mutableStateOf(false)
  val downloadProgress = mutableStateOf(0f)

  // 下载失败的dialog
  val downloadFailedDialogState = mutableStateOf(false)

  // 地图更新的dialog
  val updateMapDialogState = mutableStateOf(false)
  val isUpdateStart = mutableStateOf(false)

  // 地点详细信息
  val placeDetails = mutableStateOf<PlaceDetails?>(null)
  val bottomSheetState = BottomSheetState(hideable = true)

  // 地图主页与所有图片页的切换(0表示地图主页，1表示所有图片页)
  val mapPagerState = mutableStateOf(0)


  init {
    initMapInfo()
    getButtonInfo()
  }

  // 初始化地图信息
  fun initMapInfo() {
    launch {
      MapRepository.getMapInfo().getOrElse { throwable ->
        toast(NETWORK_ERROR_INFO)
        MapDataRepository.getMapInfo()
      }?.let {
        mapInfo.value = it
        MapDataRepository.saveMapInfo(it)
      } ?: run {
        downloadFailedDialogState.value = true
      }
    }
  }

  // 初始化聚焦信息
  fun initFocus(scope: CoroutineScope) {
    // 如果初始化时bottomSheet展开的，说明当前是从image页pop回来的，不需要重新focus
    if (bottomSheetState.state == BottomSheetValueState.Expanded) return
    mapInfo.value?.let { mapInfo ->
      mapInfo.placeList.find {
        it.placeId == mapInfo.openSiteId
      }?.let { placeItem ->
        getPlaceDetails(placeItem.placeId)
        focusOnPlace(placeItem, scope)
      }
    }
  }

  // 聚焦于某个地点
  fun focusOnPlace(placeItem: PlaceItem, scope: CoroutineScope) {
    mapInfo.value?.let { mapInfo ->
      if (mapContainer.value == IntSize.Zero) return
      val getOffset = calculatePlaceInMap(
        Offset(placeItem.placeCenterX.toFloat(), placeItem.placeCenterY.toFloat()),
        mapContainer.value,
        IntSize(mapInfo.mapWidth, mapInfo.mapHeight)
      )
      scope.launch {
        animateMapToPosition(this, getOffset)
        launch {
          anchorItemState.placeId = placeItem.placeId
          updateAnchorState(getOffset, true)
        }
      }
    }
  }

  // 获取按钮信息
  fun getButtonInfo() {
    launch {
      MapRepository.getButtonInfo().getOrElse { throwable ->
        toast(NETWORK_ERROR_INFO)
        MapDataRepository.getButtonInfo()
      }?.let { buttonInfo ->
        buttonInfoItemList.clear()
        buttonInfoItemList.addAll(buttonInfo.buttonInfo)
        MapDataRepository.saveButtonInfo(buttonInfo)
      }
    }
  }

  // 获取地点详细信息
  fun getPlaceDetails(placeId: String) {
    launch {
      MapRepository.getPlaceDetails(placeId).getOrElse { throwable ->
        toast(NETWORK_ERROR_INFO)
        MapDataRepository.getPlaceDetails(placeId)
      }?.let {
        placeDetails.value = it
        MapDataRepository.savePlaceDetails(placeId, it)
      }
    }
  }

  // 点击按钮展示anchorList
  fun showAnchorList(scope: CoroutineScope, index: Int) {
    var item = 0
    val buttonInfoItem = buttonInfoItemList[index]
    val duration = if (buttonInfoItem.placeIdList.size <= 5) 100 else 50
    scope.launch {
      if (anchorItemState.scale != 0f) anchorItemState.animateClose()
      anchorItemStateList.forEach { anchorItemState ->
        anchorItemState.animateClose(duration)
      }
      anchorItemStateList.clear()
      // 从地图信息中寻找匹配的建筑
      mapInfo.value?.let { mapInfo ->
        mapInfo.placeList.forEach { placeItem ->
          if (item < buttonInfoItem.placeIdList.size && placeItem.placeId == buttonInfoItem.placeIdList[item]) {
            val getOffset = calculatePlaceInMap(
              Offset(placeItem.placeCenterX.toFloat(), placeItem.placeCenterY.toFloat()),
              mapContainer.value,
              IntSize(mapInfo.mapWidth, mapInfo.mapHeight)
            )
            anchorItemStateList.add(
              AnchorItemState(
                initialPosition = getOffset,
                placeId = placeItem.placeId
              )
            )
            item++
          }
        }
      }
      resetMap(this)
      launch {
        anchorItemStateList.forEach { anchorItemState ->
          anchorItemState.visible = true
          anchorItemState.animateClick(duration)
        }
      }
    }
  }

  // 点击anchorItem
  fun clickAnchorItem(scope: CoroutineScope, anchorItemState: AnchorItemState) {
    getPlaceDetails(anchorItemState.placeId)
    scope.launch {
      launch {
        animateMapToPosition(scope, anchorItemState.position)
      }
      launch {
        bottomSheetState.expand()
      }
    }
  }

  // 点击地图后的判断
  fun clickPlace(scope: CoroutineScope, offset: Offset, mapInfo: MapInfo) {
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
    anchorItemState.placeId = placeId // 更新一下对应的placeId
    if (isFind) getPlaceDetails(placeId)
    // 启动动画
    scope.launch {
      if (isFind) {
        animateMapToPosition(this, realOffset)
      }
      // 对于点击建筑标签时，关闭列表动画采用同时进行，对齐之前的代码
      anchorItemStateList.filter {
        it.visible
      }.forEach { anchorItemState ->
        launch {
          anchorItemState.animateClose(300)
          anchorItemState.visible = false
        }
      }
      launch {
        // 如果找到就启动点击出现的动画并更新状态
        if (isFind) {
          launch {
            updateAnchorState(realOffset, true)
          }
          if (bottomSheetState.state == BottomSheetValueState.Hide) {
            launch {
              bottomSheetState.collapse()
            }
          }
        } else {
          launch {
            updateAnchorState(visible = false)
          }
          launch {
            bottomSheetState.hide() // 老逻辑这里是不会关闭dialog的，这里增加了一个如果没点到就关闭dialog的动画
          }
        }
      }
    }
  }

  // 还原地图为初始状态
  fun resetMap(scope: CoroutineScope) {
    scope.launch {
      launch {
        mapWidgetState.animateScale(MIN_SCALE)
      }
      launch {
        mapWidgetState.animateOffset(Offset.Zero)
      }
    }
  }

  // 将地图中心移至某点
  fun animateMapToPosition(scope: CoroutineScope, offset: Offset) {
    scope.launch {
      // 执行动画
      launch {
        mapWidgetState.animateScale(MAX_SCALE)
      }
      launch {
        mapWidgetState.animateOffset((mapCenter - offset) * 6f)
      }
    }
  }

  // 跳转导航
  open fun jumpToNavigation(endPlace: String) {}

  // 更新锚点的状态
  private suspend fun updateAnchorState(
    position: Offset = anchorItemState.position,
    visible: Boolean,
    animate: Boolean = true
  ) {
    if (anchorItemState.scale != 0f) {
      anchorItemState.animateClose()
    }
    anchorItemState.visible = visible
    if (animate && visible) {
      anchorItemState.position = position
      anchorItemState.animateClick()
    }
  }
}