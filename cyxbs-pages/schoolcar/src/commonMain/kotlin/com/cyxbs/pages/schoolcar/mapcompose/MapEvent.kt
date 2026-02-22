package com.cyxbs.pages.schoolcar.mapcompose

/**
 * description ： 地图上的事件
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/2/21 23:04
 */
sealed class MapEvent {
	data class MarkerClick(val marker: MapMarkerState) : MapEvent()
	object MapClick : MapEvent()
}