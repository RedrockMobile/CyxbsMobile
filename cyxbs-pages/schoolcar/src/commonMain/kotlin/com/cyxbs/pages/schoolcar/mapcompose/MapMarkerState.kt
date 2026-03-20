package com.cyxbs.pages.schoolcar.mapcompose

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * description ： 地图上的Marker
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/2/21 22:18
 */
@Stable
class MapMarkerState(
	val id: String,// 这个会作为key来区分每个marker
	val type: MarkerType,
	initialPosition: Offset = Offset.Zero,
	initialSelect: Boolean = false,
	visible: Boolean = true,
) {
	var position by mutableStateOf(initialPosition)
	var visible by mutableStateOf(visible)
	var isSelect by mutableStateOf(initialSelect)
	fun moveToPos(newPos: Offset) {
		if (position != newPos) position = newPos
	}

	fun isBelongToLine(lineId: Int?): Boolean {
		// 如果传入的id为null说明是全选模式
		if (lineId == null) return true
		return when (type) {
			is MarkerType.Car -> type.lineId == lineId
			is MarkerType.Site -> type.parentLineIds.contains(lineId)
			MarkerType.User -> true
		}
	}
}


@Stable
sealed interface MarkerType {
	// 汽车
	data class Car(
		val lineId: Int,
		val updateAt: Long,
	) : MarkerType

	// 站点
	data class Site(
		val name: String,
		val parentLineIds: Set<Int> // 该站点所属的所有线路 ID
	) : MarkerType

	object User : MarkerType
}