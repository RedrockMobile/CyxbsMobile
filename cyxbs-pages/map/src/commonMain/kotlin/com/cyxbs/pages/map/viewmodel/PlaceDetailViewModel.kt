package com.cyxbs.pages.map.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.view.ui.bottomsheet.BottomSheetState
import com.cyxbs.components.view.ui.bottomsheet.BottomSheetAnchor
import com.cyxbs.pages.map.model.MapDataRepository
import com.cyxbs.pages.map.model.MapRepository
import com.cyxbs.pages.map.model.bean.PlaceDetails
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job

class PlaceDetailViewModel : BaseViewModel() {
  val placeDetails = mutableStateOf<PlaceDetails?>(null)
  val placeDetailsId = mutableStateOf("")
  val sheetVisible = mutableStateOf(true)
  val bottomSheetState = BottomSheetState(hideable = true, requestDismissOnDrag = false)
  val collectListState = mutableStateListOf<String>()
  private var detailsJob: Job? = null
  private var hasLoadedDetails = false

  init {
    if (IAccountService::class.impl().isLogin()) getCollect()
    MapComposeViewModel.navEvents.collectLaunch(start = CoroutineStart.UNDISPATCHED) { event ->
      when (event) {
        is MapNavEvent.ShowPlaceDetail -> showPlace(event.placeId, event.expanded)
        MapNavEvent.CollapsePlaceDetail -> bottomSheetState.collapseAsync()
        MapNavEvent.HidePlaceDetail -> bottomSheetState.hideAsync()
        is MapNavEvent.MapPageChanged -> sheetVisible.value = !event.picturesVisible && !event.searchVisible
        else -> Unit
      }
    }
  }

  fun openAllPictures() {
    val images = placeDetails.value?.images.orEmpty().toList()
    val placeId = placeDetailsId.value
    launchByViewModelScope {
      MapComposeViewModel.emitNavEvent(MapNavEvent.OpenAllPictures(placeId, images))
    }
  }

  fun requestPhotoUpload() {
    val placeId = placeDetailsId.value
    launchByViewModelScope {
      MapComposeViewModel.emitNavEvent(MapNavEvent.RequestPhotoUpload(placeId))
    }
  }

  fun showPlace(placeId: String, expanded: Boolean?) {
    val placeChanged = placeDetailsId.value != placeId
    if (placeChanged) {
      detailsJob?.cancel()
      hasLoadedDetails = false
      placeDetailsId.value = placeId
      placeDetails.value = MapDataRepository.getPlaceDetails(placeId)
    }
    if (placeChanged || (!hasLoadedDetails && detailsJob?.isActive != true)) {
      detailsJob = launchByViewModelScope {
        if (placeDetails.value == null) {
          val localPlace = MapDataRepository.getMapInfo()?.placeList?.find { it.placeId == placeId }
          if (placeDetailsId.value == placeId && localPlace != null) {
            placeDetails.value = PlaceDetails(localPlace.placeName, null, null, null)
          }
        }
        MapRepository.getPlaceDetails(placeId).onSuccess {
          // 切换地点后，旧请求不能覆盖新地点的数据。
          if (placeDetailsId.value == placeId) {
            placeDetails.value = it
            hasLoadedDetails = true
          }
          MapDataRepository.savePlaceDetails(placeId, it)
        }.onFailure { toast(MapComposeViewModel.NETWORK_ERROR_INFO) }
      }
    }
    when {
      expanded == true -> bottomSheetState.expandAsync()
      expanded == false || bottomSheetState.isSettledAt(BottomSheetAnchor.Hidden) ->
        bottomSheetState.collapseAsync()
    }
  }

  fun getCollect() {
    launchByViewModelScope {
      MapRepository.getCollect().getOrElse { throwable ->
        toast(MapComposeViewModel.NETWORK_ERROR_INFO)
        MapDataRepository.getCollectList()
      }?.let {
        collectListState.clear()
        collectListState.addAll(it)
        MapDataRepository.saveCollectList(it)
      }
    }
  }

  fun addCollect(placeId: String) {
    launchByViewModelScope {
      MapRepository.addCollect(placeId).getOrElse { throwable ->
        toast("添加收藏失败~")
        null
      }?.let {
        if (it.isSuccess()) {
          toast("收藏成功!")
          if (placeId !in collectListState) collectListState.add(placeId)
          MapDataRepository.saveCollectList(collectListState)
          MapComposeViewModel.emitNavEvent(MapNavEvent.CollectionsChanged)
        }
      }
    }
  }

  fun deleteCollect(placeId: String) {
    launchByViewModelScope {
      MapRepository.deleteCollect(placeId).getOrElse { throwable ->
        toast("删除收藏失败~")
        null
      }?.let {
        if (it.isSuccess()) {
          toast("已取消收藏!")
          collectListState.remove(placeId)
          MapDataRepository.saveCollectList(collectListState)
          MapComposeViewModel.emitNavEvent(MapNavEvent.CollectionsChanged)
        }
      }
    }
  }

}

expect fun openMapNavigation(endPlace: String)
