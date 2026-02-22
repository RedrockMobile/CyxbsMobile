package com.cyxbs.pages.schoolcar.mapcompose

import androidx.compose.runtime.Stable
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.Res
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_background_1
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_background_2
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_background_3
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_background_4
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_background_default
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_icon_1
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_icon_2
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_icon_3
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_icon_4
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_mine
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_site_1
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_site_2
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_site_3
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_site_4
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_site_default
import org.jetbrains.compose.resources.DrawableResource

/**
 * description ： 地图上的Marker
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/2/21 22:18
 */
@Stable
data class MapMarkerState(
	/*
			mine -> 用户
			car_1_1 -> 校车一号线第一辆
			site_2 -> 2号站点
	 */
	val uid: String, // 用于唯一标识处一个Marker
	val type: MarkerType,
	val lat: Double, // 维度
	val lng: Double, // 经度
	val rotation: Float = 0f,
	val visible: Boolean = true,
)

// Marker的Type
@Stable
sealed interface MarkerType {
	val id: Int
	val zIndex: Float

	// 汽车
	data class Car(
		override val id: Int, // 当前线路的第几量车
		val type: Int, // 线路id
	) : MarkerType {
		override val zIndex: Float
			get() = 10f
	}

	// 站点
	data class Site(
		override val id: Int,
	) : MarkerType {
		override val zIndex: Float
			get() = 50f
	}

	// 用户
	object Mine : MarkerType {
		override val id: Int
			get() = -1
		override val zIndex: Float
			get() = 100f
	}
}

