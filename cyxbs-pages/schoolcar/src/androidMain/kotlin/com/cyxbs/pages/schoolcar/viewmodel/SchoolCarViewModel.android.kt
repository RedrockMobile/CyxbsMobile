package com.cyxbs.pages.schoolcar.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import com.cyxbs.pages.schoolcar.bean.CarStation
import com.cyxbs.pages.schoolcar.bean.GeoLocation
import com.cyxbs.pages.schoolcar.bean.toUserLocation
import com.cyxbs.pages.schoolcar.location.AMapLocationHelper
import com.cyxbs.pages.schoolcar.location.PermissionsHelper
import com.cyxbs.pages.schoolcar.location.RotationHelper
import dev.icerock.moko.permissions.PermissionsController

actual class SchoolCarViewModel : CommonSchoolCarViewModel() {
	private val locationHelper = AMapLocationHelper()
	private val rotationHelper = RotationHelper()
	val permissionsHelper = PermissionsHelper(PermissionsController(appContext))
	val hasPermission = mutableStateOf(false)
	actual override val isSupportLocation: Boolean
		get() = true

	init {
		// 监听位置变化
		launchByViewModelScope {
			locationHelper.locationFlow.collect { geoLocation ->
				if (geoLocation != null) {
					val userLoc = geoLocation.toUserLocation()
					userPositionState.moveToTarget(
						newPos = Offset(userLoc.px.toFloat(), userLoc.py.toFloat()),
						duration = 1000,
					)
				}
			}
		}

		// 监听角度变化
		launchByViewModelScope {
			rotationHelper.rotationFlow.collect { rotation ->
				userPositionState.rotationToTarget(rotation, 300)
			}
		}
	}

	actual override fun getClosedSite(): CarStation? {
		// currentLocation为null说明没开启定位，或者超出了校园的区域。寻找最近站点就以校园中心点
		val location = (locationHelper.locationFlow.replayCache.firstOrNull() ?: GeoLocation(
			29.531876,
			106.606789
		)).toUserLocation()
		val info = carLineInfo.value ?: return null
		val sites = info.lines.flatMap { it.stations }.distinctBy { it.id }
		val first = sites.minByOrNull {
			val dx = it.px - location.px
			val dy = it.py - location.py
			dx * dx + dy * dy
		} ?: return null

		return first
	}

	fun tryStartLocation() {
		launchByViewModelScope {
			if (permissionsHelper.hasPermission()) {
				hasPermission.value = true
				startLocation()
			} else {
				permissionsHelper.requestLocationPermission(
					onSuccess = {
						hasPermission.value = true
						startLocation()
					},
					onError = {
						hasPermission.value = false
						toast("未授予定位权限，无法显示定位信息")
						stopLocation()
					}
				)
			}
		}
	}

	fun startLocation() {
		locationHelper.start()
		rotationHelper.start()
	}

	fun stopLocation() {
		locationHelper.stop()
		rotationHelper.stop()
	}

	override fun onCleared() {
		super.onCleared()
		locationHelper.clear()
		rotationHelper.stop()
	}
}