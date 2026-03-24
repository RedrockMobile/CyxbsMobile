package com.cyxbs.pages.schoolcar.bean

import kotlin.math.roundToInt

/**
 * description ： 用户位置信息的数据类
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/3/24 16:05
 */

// 地理上的位置信息
data class GeoLocation(
	val lat: Double, //  维度
	val lng: Double,  // 精度
)

data class UserLocation(
	val px: Int,
	val py: Int,
)

/*
		因为是图片地图，所以换地图记得换这个换算公式
		px = 93669.803250 * lng - 9984871.926823
		py = -106715.931319 * lat + 3152239.647902
 */
fun GeoLocation.toUserLocation() = UserLocation(
	(93669.803250 * lng - 9984871.926823).roundToInt(),
	(-106715.931319 * lat + 3152239.647902).roundToInt()
)