package com.cyxbs.pages.schoolcar.ui

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import com.cyxbs.pages.schoolcar.mapcompose.BaseMarkerState

/**  
 * description ： 校车地图组件的业务层MarkerState
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/3/21 18:45
 */

/*
	因为car的Id是可能重复的，他表示什该线路的第几量车
	规定一下id命名：站点: station_id
								车辆：car_lineId_id
 */
@Stable
class StationMarkerState(
	id: String,
	val name: String,
	val lineIds: Set<Int>, // 该站点所属的所有线路 ID
	position: Offset,
	visible: Boolean
) : BaseMarkerState(id, position, visible) {
	companion object {
		fun getStationIdByString(id: String): Int? {
			val parts = id.split("_")
			return parts.getOrNull(1)?.toIntOrNull()
		}
	}
}

@Stable
class CarMarkerState(
	id: String,
	val lineId: Int,
	val updateAt: Long,
	position: Offset,
	visible: Boolean
) : BaseMarkerState(id, position, visible) {

	companion object {
		fun getLineIdByString(id: String): String? {
			val parts = id.split("_")
			return parts.getOrNull(1)
		}
	}
}

@Stable
class UserPositionMarkerState(
	id: String,
	position: Offset,
	visible: Boolean
) : BaseMarkerState(id, position, visible)
