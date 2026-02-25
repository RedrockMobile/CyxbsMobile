package com.cyxbs.pages.schoolcar.viewmodel

import androidx.compose.runtime.mutableStateOf
import com.amap.api.location.AMapLocation
import com.amap.api.maps.AMapUtils
import com.amap.api.maps.model.LatLng
import com.cyxbs.pages.schoolcar.bean.CarStation

actual class SchoolCarViewModel : CommonSchoolCarViewModel() {
	var currentLocation: LatLng? = null

	val enableMyLocation = mutableStateOf(true)

	fun updateLocation(location: AMapLocation) {
		val center = LatLng(29.531876, 106.606789)
		val myLatLng = LatLng(location.latitude, location.longitude)
		val distance = AMapUtils.calculateLineDistance(center, myLatLng)
		if (distance > 2000) {
			enableMyLocation.value = false
		} else {
			currentLocation = myLatLng
		}
	}

	actual override fun getClosedSite(): CarStation? {
		// currentLocation为null说明没开启定位，或者超出了校园的区域。寻找最近站点就以校园中心点
		val location = currentLocation ?: LatLng(29.531876, 106.606789)
		val info = carLineInfo.value ?: return null
		val sites = info.lines.flatMap { it.stations }.distinctBy { it.id }
		val first = sites.minByOrNull {
			AMapUtils.calculateLineDistance(LatLng(it.lat, it.lng), location)
		} ?: return null
		return first
	}
}