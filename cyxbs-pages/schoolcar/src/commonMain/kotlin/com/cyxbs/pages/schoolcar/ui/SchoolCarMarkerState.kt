package com.cyxbs.pages.schoolcar.ui

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import com.cyxbs.pages.schoolcar.mapcompose.MovableMarkerState
import com.cyxbs.pages.schoolcar.mapcompose.StaticMarkerState

/**  
 * description ： 校车地图组件的业务层MarkerState
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/3/21 18:45
 */

@Stable
class StationMarkerState(
	override val id: Int,
	val name: String,
	val lineIds: Set<Int>, // 该站点所属的所有线路 ID
	initialPosition: Offset,
	initialVisible: Boolean
) : StaticMarkerState(id, initialPosition, initialVisible)

@Stable
class CarMarkerState(
	override val id: Int,
	val lineId: Int,
	var updateAt: Long,
	initialPosition: Offset,
	initialVisible: Boolean
) : MovableMarkerState(id, initialPosition, initialVisible)

@Stable
class UserPositionMarkerState(
	initialPosition: Offset,
	initialVisible: Boolean,
	initRotation: Float
) : MovableMarkerState(-1, initialPosition, initialVisible, initRotation)
