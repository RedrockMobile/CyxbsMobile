package com.cyxbs.pages.schoolcar.viewmodel

import com.cyxbs.pages.schoolcar.bean.CarStation
import com.cyxbs.pages.schoolcar.bean.GeoLocation
import com.cyxbs.pages.schoolcar.bean.toUserLocation

actual class SchoolCarViewModel : CommonSchoolCarViewModel() {
	actual override fun getClosedSite(): CarStation? {
		val location = GeoLocation(
			29.531876,
			106.606789
		).toUserLocation()
		val info = carLineInfo.value ?: return null
		val sites = info.lines.flatMap { it.stations }.distinctBy { it.id }
		val first = sites.minByOrNull {
			val dx = it.px - location.px
			val dy = it.py - location.py
			dx * dx + dy * dy
		} ?: return null

		return first
	}

	actual override val isSupportLocation: Boolean
		get() = false
}