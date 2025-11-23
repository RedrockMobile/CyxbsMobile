package com.cyxbs.pages.map.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.pages.map.model.MapRepository
import com.cyxbs.pages.map.model.bean.ButtonInfoItem
import com.cyxbs.pages.map.model.bean.MapInfo
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

class MapComposeViewModel : BaseViewModel() {

  companion object {
    const val NETWORK_ERROR_INFO = "服务君似乎打盹了呢"
  }

  // 地图组件状态
  var mapWidgetState = MapWidgetState()

  // 单独记录地图组件宽高
  var mapContainer = mutableStateOf(IntSize.Zero)
  val mapCenter: Offset
    get() = Offset(
      mapContainer.value.width / 2f,
      mapContainer.value.height / 2f
    )

  // 点击锚点状态
  var anchorItemState = AnchorItemState()

  // 展示锚点集合状态
  var anchorItemStateList = mutableStateListOf<AnchorItemState>()
  var mapInfo = mutableStateOf<MapInfo?>(null)
  var buttonInfoItemList = mutableStateListOf<ButtonInfoItem>()

  // 下载地图进度dialog相关信息
  var progressDialogState = mutableStateOf(false)
  var downloadProgress = mutableStateOf(0f)

  // 下载失败的dialog
  var downloadFailedDialogState = mutableStateOf(false)

  init {
    initMapInfo()
    getButtonInfo()
  }

  // 初始化地图信息
  fun initMapInfo() {
    launch {
      MapRepository.getMapInfo().getOrElse { throwable ->
        toast(NETWORK_ERROR_INFO)
        null // 暂时返回null
        // TODO 引入缓存数据
      }?.let {
        mapInfo.value = it
        // TODO 引入缓存数据
      }
    }
  }

  // 初始化聚焦信息
  fun initFocus(coroutine: CoroutineScope) {
    mapInfo.value?.let {
      it.placeList.forEach { placeItem ->
        if (placeItem.placeId == it.openSiteId) {
          focusOnPlace(placeItem, coroutine)
        }
      }
    }
  }

  // 聚焦于某个地点
  fun focusOnPlace(placeItem: PlaceItem, coroutine: CoroutineScope) {
    mapInfo.value?.let { mapInfo ->
      if (mapContainer.value != IntSize.Zero) {
        val getOffset = calculatePlaceInMap(
          Offset(placeItem.placeCenterX.toFloat(), placeItem.placeCenterY.toFloat()),
          mapContainer.value,
          IntSize(mapInfo.mapWidth, mapInfo.mapHeight)
        )
        val containerCenter = Offset(mapContainer.value.width / 2f, mapContainer.value.height / 2f)
        toast(placeItem.placeName)
        coroutine.launch {
          launch {
            mapWidgetState.animateScale(6f)
          }
          launch {
            mapWidgetState.animateOffset((containerCenter - getOffset) * 6f)
          }
          launch {
            anchorItemState.visible = true
            anchorItemState.position = getOffset
            anchorItemState.animateClick()
          }
        }
      }
    }
  }

  // 获取按钮信息
  fun getButtonInfo() {
    launch {
      MapRepository.getButtonInfo().getOrElse { throwable ->
        toast(NETWORK_ERROR_INFO)
        null
      }?.let { buttonInfo ->
        buttonInfoItemList.clear()
        buttonInfoItemList.addAll(buttonInfo.buttonInfo)
      }
    }
  }

  // 点击按钮展示anchorList
  fun showAnchorList(coroutine: CoroutineScope, index: Int) {
    var item = 0
    val buttonInfoItem = buttonInfoItemList[index]
    val duration = if (buttonInfoItem.placeIdList.size <= 5) 100 else 50
    coroutine.launch {
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
                initialPosition = getOffset
              )
            )
            item++
          }
        }
      }
      launch {
        mapWidgetState.animateScale(1f)
      }
      launch {
        mapWidgetState.animateOffset(Offset.Zero)
      }
      launch {
        anchorItemStateList.forEach { anchorItemState ->
          anchorItemState.visible = true
          anchorItemState.animateClick(duration)
        }
      }
    }
  }

  // 点击地图后的判断
  fun clickAnchorItem(coroutine: CoroutineScope, offset: Offset, mapInfo: MapInfo) {
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
          toast(placeItem.placeName)
          realOffset = getOffset
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
              toast(placeItem.placeName)
              realOffset = getOffsetFromBuilding
              isFind = true
            }
          }
        }
      }
    }
    // 启动动画
    coroutine.launch {
      launch {
        if (isFind) {
          mapWidgetState.animateScale(6f)
        }
      }
      launch {
        if (isFind) {
          mapWidgetState.animateOffset((mapCenter - realOffset) * 6f)
        }
      }
      // 对于点击建筑标签时，关闭列表动画采用同时进行，对齐之前的代码
      anchorItemStateList.forEach { anchorItemState ->
        if (anchorItemState.visible) {
          launch {
            anchorItemState.animateClose(300)
            anchorItemState.visible = false
          }
        }
      }
      launch {
        if (anchorItemState.scale != 0f) anchorItemState.animateClose()
        // 如果找到就启动点击出现的动画并更新状态
        if (isFind) {
          anchorItemState.visible = true
          anchorItemState.position = realOffset
          anchorItemState.animateClick()
        } else {
          anchorItemState.visible = false
        }
      }
    }
  }
}