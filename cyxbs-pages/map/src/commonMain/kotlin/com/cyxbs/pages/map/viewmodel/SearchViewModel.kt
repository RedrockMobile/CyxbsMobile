package com.cyxbs.pages.map.viewmodel

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.view.ui.BottomSheetState
import com.cyxbs.pages.map.model.MapDataRepository
import com.cyxbs.pages.map.model.MapRepository
import com.cyxbs.pages.map.model.bean.MapInfo
import com.cyxbs.pages.map.model.bean.PlaceItem
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay

class SearchViewModel : BaseViewModel() {
  val searchTextFieldState = TextFieldState()
  val searchResultList = mutableStateListOf<PlaceItem>()
  val searchHistory = mutableStateListOf<PlaceItem>()
  val mapInfo = mutableStateOf<MapInfo?>(null)
  val searchBottomSheetState = BottomSheetState(hideable = false)
  val mapSearchPagerState = mutableStateOf(0)
  val sheetVisible = mutableStateOf(true)

  init {
    getSearchHistory()
    MapComposeViewModel.navEvents.collectLaunch(start = CoroutineStart.UNDISPATCHED) {
      when (it) {
        MapNavEvent.CollapseSearch -> searchBottomSheetState.collapseAsync()
        is MapNavEvent.MapPageChanged -> sheetVisible.value = !it.picturesVisible
        else -> Unit
      }
    }
    launchByViewModelScope {
      mapInfo.value = MapDataRepository.getMapInfo()
      search()
      MapRepository.getMapInfo().getOrNull()?.let {
        mapInfo.value = it
        search()
      }
    }
  }

  fun searchToPlace(place: PlaceItem) {
    mapSearchPagerState.value = 0
    launchByViewModelScope {
      MapComposeViewModel.emitNavEvent(MapNavEvent.SelectPlace(place))
      searchBottomSheetState.collapseAsync()
      delay(500)
      searchTextFieldState.clearText()
    }
  }

  fun search() {
    searchResultList.clear()
    if (searchTextFieldState.text.isEmpty()) return
    mapInfo.value?.let { mapInfo ->
      val resultList = mapInfo.placeList.filter { placeItem ->
        placeItem.placeName.contains(searchTextFieldState.text, true)
      }
      searchResultList.addAll(resultList)
    }
  }

  fun getSearchHistory() {
    MapDataRepository.getSearchHistory()?.let {
      searchHistory.clear()
      searchHistory.addAll(it)
    }
  }

  fun addSearchHistory(placeItem: PlaceItem) {
    searchHistory.removeAll { it.placeId == placeItem.placeId }
    searchHistory.add(placeItem)
    MapDataRepository.saveSearchHistory(searchHistory)
  }

  fun deleteSearchHistory(placeItem: PlaceItem) {
    searchHistory.remove(placeItem)
    MapDataRepository.saveSearchHistory(searchHistory)
  }

  fun clearSearchHistory() {
    searchHistory.clear()
    MapDataRepository.saveSearchHistory(searchHistory)
  }

  fun addHot(placeId: String) {
    launchByViewModelScope {
      MapRepository.addHot(placeId).getOrElse { throwable ->
        toast("上传搜索热度失败~")
      }
    }
  }
}
