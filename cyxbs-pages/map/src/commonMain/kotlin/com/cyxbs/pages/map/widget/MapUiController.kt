package com.cyxbs.pages.map.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.cyxbs.components.view.ui.BottomSheetState
import com.cyxbs.components.view.ui.BottomSheetValueState
import com.cyxbs.pages.map.model.bean.MapInfo
import com.cyxbs.pages.map.util.calculatePlaceInMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Stable
class MapUiController(
  val mapWidgetState: MapWidgetState,
  val mainAnchorState: AnchorItemState,
  val anchorItemStateList: SnapshotStateList<AnchorItemState>,
  val bottomSheetState: BottomSheetState,
  val searchBottomSheetState: BottomSheetState,
  val mapContainer: MutableState<IntSize>
) {

  private var mapAnimationJob: Job? = null
  private var anchorListJob: Job? = null

  private val mapCenter get() = Offset(mapContainer.value.width / 2f, mapContainer.value.height / 2f)

  private suspend fun updateMainAnchorState(
    position: Offset = mainAnchorState.position,
    visible: Boolean,
    animate: Boolean = true
  ) {
    if (mainAnchorState.scale != 0f) {
      mainAnchorState.animateClose()
    }
    mainAnchorState.visible = visible
    if (animate && visible) {
      mainAnchorState.position = position
      mainAnchorState.animateClick()
    }
  }

  private suspend fun resetMapAnimation(scope: CoroutineScope) {
    mapAnimationJob?.cancel()
    mapAnimationJob = scope.launch {
      launch {
        mapWidgetState.animateScale(1f)
      }
      launch {
        mapWidgetState.animateOffset(Offset.Zero)
      }
    }
  }

  private suspend fun animateMapToPosition(
    scope: CoroutineScope,
    maxScale: Float,
    offset: Offset
  ) {
    mapAnimationJob?.cancel()
    mapAnimationJob = scope.launch {
      launch {
        mapWidgetState.animateScale(maxScale)
      }
      launch {
        mapWidgetState.animateOffset((mapCenter - offset) * maxScale)
      }
    }
  }

  private suspend fun showAnchorList(
    scope: CoroutineScope,
    placeIds: List<String>,
    isCollectList: Boolean,
    mapInfo: MapInfo?
  ) {
    val duration = if (anchorItemStateList.size <= 5) 100 else 50
    anchorListJob?.cancel()
    anchorListJob = scope.launch {
      if (!isCollectList) {
        launch {
          searchBottomSheetState.collapse()
        }
      }
      launch {
        bottomSheetState.collapse()
      }
      if (mainAnchorState.scale != 0f) mainAnchorState.animateClose()
      anchorItemStateList.forEach { anchorItemState ->
        if (anchorItemState.scale != 0f) anchorItemState.animateClose(duration)
      }
      anchorItemStateList.clear()
      mapInfo?.let {
        val tmpList: MutableList<AnchorItemState> = mutableListOf()
        if (isCollectList) {
          placeIds.forEach { placeId ->
            it.placeList.find { placeItem ->
              placeId == placeItem.placeId
            }?.let { placeItem ->
              val getOffset = calculatePlaceInMap(
                Offset(placeItem.placeCenterX.toFloat(), placeItem.placeCenterY.toFloat()),
                mapContainer.value,
                IntSize(it.mapWidth, it.mapHeight)
              )
              tmpList.add(
                AnchorItemState(
                  initialPosition = getOffset,
                  placeId = placeItem.placeId
                )
              )
            }
          }
        } else {
          var item = 0
          it.placeList.forEach { placeItem ->
            if (item < placeIds.size && placeItem.placeId == placeIds[item]) {
              val getOffset = calculatePlaceInMap(
                Offset(placeItem.placeCenterX.toFloat(), placeItem.placeCenterY.toFloat()),
                mapContainer.value,
                IntSize(it.mapWidth, it.mapHeight)
              )
              tmpList.add(
                AnchorItemState(
                  initialPosition = getOffset,
                  placeId = placeItem.placeId
                )
              )
              item++
            }
          }
        }
        anchorItemStateList.addAll(tmpList)
      }
      resetMapAnimation(this)
      val newDuration = if (placeIds.size <= 5) 100 else 50
      anchorItemStateList.forEach { anchorItemState ->
        anchorItemState.visible = true
        anchorItemState.animateClick(newDuration)
      }
    }
  }

  private suspend fun closeAnchorList(scope: CoroutineScope) {
    val duration = if (anchorItemStateList.size <= 5) 100 else 50
    anchorListJob?.cancel()
    anchorListJob = scope.launch {
      anchorItemStateList.forEach { anchorItemState ->
        anchorItemState.animateClose(duration)
      }
    }
  }

  suspend fun handleMapUiEvent(
    event: MapUiEvent,
    scope: CoroutineScope,
    mapInfo: MapInfo?,
    maxScale: Float,
    collectList: List<String>,
    calculatePlaceOffset: (Float, Float) -> Offset?,
    clearSearchText: suspend () -> Unit
  ) {
    when (event) {
      is MapUiEvent.SearchToPlace -> {
        val getOffset = calculatePlaceOffset(event.placeCenterX, event.placeCenterY) ?: return
        coroutineScope {
          launch {
            searchBottomSheetState.collapse()
          }
          resetMapAnimation(this)
          anchorItemStateList.filter {
            it.visible
          }.forEach { anchorItemState ->
            launch {
              anchorItemState.animateClose(300)
              anchorItemState.visible = false
            }
          }
          launch {
            bottomSheetState.collapse()
          }
          launch {
            mainAnchorState.placeId = event.placeId
            updateMainAnchorState(getOffset, true)
          }
          launch {
            delay(500)
            clearSearchText()
          }
        }
      }

      is MapUiEvent.FocusOnPlace -> {
        val getOffset = calculatePlaceOffset(event.placeCenterX, event.placeCenterY) ?: return
        coroutineScope {
          launch {
            searchBottomSheetState.collapse()
          }
          animateMapToPosition(this, maxScale, getOffset)
          anchorItemStateList.filter {
            it.visible
          }.forEach { anchorItemState ->
            launch {
              anchorItemState.animateClose(300)
              anchorItemState.visible = false
            }
          }
          launch {
            mainAnchorState.placeId = event.placeId
            updateMainAnchorState(getOffset, true)
          }
        }
      }

      is MapUiEvent.ShowAnchorList -> {
        showAnchorList(scope, event.placeIds, false, mapInfo)
      }

      MapUiEvent.ShowCollectAnchors -> {
        showAnchorList(scope, collectList, true, mapInfo)
      }

      MapUiEvent.CloseAnchorList -> {
        closeAnchorList(scope)
      }

      is MapUiEvent.ClickMapPlace -> {
        val realOffset = if (
          event.placeId != null &&
          event.focusOffsetX != null &&
          event.focusOffsetY != null
        ) {
          Offset(event.focusOffsetX, event.focusOffsetY)
        } else {
          null
        }
        coroutineScope {
          launch {
            searchBottomSheetState.collapse()
          }
          if (realOffset != null) {
            animateMapToPosition(this, maxScale, realOffset)
          }
          anchorItemStateList.filter {
            it.visible
          }.forEach { anchorItemState ->
            launch {
              anchorItemState.animateClose(300)
              anchorItemState.visible = false
            }
          }
          launch {
            if (realOffset != null && event.placeId != null) {
              launch {
                updateMainAnchorState(realOffset, true)
              }
              if (bottomSheetState.state == BottomSheetValueState.Hide) {
                launch {
                  bottomSheetState.collapse()
                }
              }
            } else {
              launch {
                updateMainAnchorState(visible = false)
              }
              launch {
                bottomSheetState.hide()
              }
            }
          }
        }
      }

      is MapUiEvent.OpenPlaceDetail -> {
        coroutineScope {
          launch {
            animateMapToPosition(this, maxScale, Offset(event.anchorPositionX, event.anchorPositionY))
          }
          launch {
            searchBottomSheetState.collapse()
          }
          launch {
            bottomSheetState.expand()
          }
        }
      }

      MapUiEvent.ResetMap -> {
        resetMapAnimation(scope)
      }

      is MapUiEvent.AnimateMapToPosition -> {
        animateMapToPosition(scope, maxScale, Offset(event.offsetX, event.offsetY))
      }

      MapUiEvent.CollapseSearchSheet -> {
        searchBottomSheetState.collapse()
      }

      MapUiEvent.CollapseBottomSheet -> {
        bottomSheetState.collapse()
      }

      MapUiEvent.ExpandBottomSheet -> {
        bottomSheetState.expand()
      }

      MapUiEvent.HideBottomSheet -> {
        bottomSheetState.hide()
      }
    }
  }
}

@Composable
fun rememberMapUiController(
  mapWidgetState: MapWidgetState,
  mainAnchorState: AnchorItemState,
  anchorItemStateList: SnapshotStateList<AnchorItemState>,
  bottomSheetState: BottomSheetState,
  searchBottomSheetState: BottomSheetState,
  mapContainer: MutableState<IntSize>
): MapUiController {
  return remember(
    mapWidgetState,
    mainAnchorState,
    anchorItemStateList,
    bottomSheetState,
    searchBottomSheetState,
    mapContainer
  ) {
    MapUiController(
      mapWidgetState = mapWidgetState,
      mainAnchorState = mainAnchorState,
      anchorItemStateList = anchorItemStateList,
      bottomSheetState = bottomSheetState,
      searchBottomSheetState = searchBottomSheetState,
      mapContainer = mapContainer
    )
  }
}
