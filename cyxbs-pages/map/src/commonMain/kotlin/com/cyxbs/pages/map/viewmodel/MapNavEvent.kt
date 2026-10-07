package com.cyxbs.pages.map.viewmodel

import com.cyxbs.pages.map.model.bean.PlaceItem

sealed interface MapNavEvent {
  data class RequestPhotoUpload(val placeId: String) : MapNavEvent
  data class SelectPlace(val place: PlaceItem) : MapNavEvent
  data class ShowPlaceDetail(val placeId: String, val expanded: Boolean?) : MapNavEvent
  data class OpenAllPictures(val placeId: String, val images: List<String>) : MapNavEvent
  data class MapPageChanged(val picturesVisible: Boolean, val searchVisible: Boolean) : MapNavEvent
  data object CollapsePlaceDetail : MapNavEvent
  data object HidePlaceDetail : MapNavEvent
  data object CollapseSearch : MapNavEvent
  data object CollectionsChanged : MapNavEvent
}
