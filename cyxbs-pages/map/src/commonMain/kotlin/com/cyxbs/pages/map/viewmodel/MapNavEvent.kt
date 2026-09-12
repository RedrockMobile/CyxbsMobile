package com.cyxbs.pages.map.viewmodel

sealed interface MapNavEvent {
  data class OpenPlaceDetail(val placeId: String) : MapNavEvent
  data class FocusPlace(val placeId: String) : MapNavEvent
  data object CollapsePlaceDetail : MapNavEvent
  data object ExpandPlaceDetail : MapNavEvent
  data object HidePlaceDetail : MapNavEvent
  data object CloseSearch : MapNavEvent
}
