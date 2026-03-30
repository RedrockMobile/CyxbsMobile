package com.cyxbs.pages.schoolcar.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import com.cyxbs.pages.schoolcar.bean.CarStation
import com.cyxbs.pages.schoolcar.bean.GeoLocation
import com.cyxbs.pages.schoolcar.bean.toUserLocation
import com.cyxbs.pages.schoolcar.location.AMapLocationHelper
import com.cyxbs.pages.schoolcar.location.AMapLocationHelper.Companion.CENTER_LAT
import com.cyxbs.pages.schoolcar.location.AMapLocationHelper.Companion.CENTER_LNG
import com.cyxbs.pages.schoolcar.location.PermissionsHelper
import com.cyxbs.pages.schoolcar.location.RotationHelper
import dev.icerock.moko.permissions.PermissionsController

actual class SchoolCarViewModel : CommonSchoolCarViewModel() {
	actual override val isSupportLocation: Boolean = true
	actual override val shouldShowUserPositionMarker: State<Boolean> =
		derivedStateOf { hasPermission.value && isSupportLocation && isUserInRange.value }

	private val center = GeoLocation(CENTER_LAT, CENTER_LNG)

	private val locationHelper = AMapLocationHelper()
	private val rotationHelper = RotationHelper()

	val permissionsHelper = PermissionsHelper(PermissionsController(appContext))
	val hasPermission = mutableStateOf(false)
	val isUserInRange = mutableStateOf(true)

	init {
		observeLocation()
		observeRotation()

	}

	actual override fun getClosedSite(): CarStation? {
		// currentLocation为null说明没开启定位，或者超出了校园的区域。寻找最近站点就以校园中心点
		val location =
			(locationHelper.locationFlow.replayCache.firstOrNull() ?: center).toUserLocation()
		val info = carLineInfo.value ?: return null
		val sites = info.lines.flatMap { it.stations }.distinctBy { it.id }
		val first = sites.minByOrNull {
			val dx = it.px - location.px
			val dy = it.py - location.py
			dx * dx + dy * dy
		} ?: return null

		return first
	}

	// 监听位置变化
	fun observeLocation() {
		launchByViewModelScope {
			locationHelper.locationFlow.collect { geoLocation ->
				geoLocation?.let {
					val distance = AMapLocationHelper.calculateDistance(it, center)
					if (distance > AMapLocationHelper.MAX_DISTANCE) {
						isUserInRange.value = false
						stopLocation()
						toast("已超出校园服务范围，定位已关闭")
					} else {
						val userLoc = geoLocation.toUserLocation()
						userPositionState.moveToTarget(
							Offset(userLoc.px.toFloat(), userLoc.py.toFloat()), 1000,
						)
					}
				}
			}
		}
	}

	// 监听角度变化
	fun observeRotation() {
		launchByViewModelScope {
			rotationHelper.rotationFlow.collect { rotation ->
				if (isUserInRange.value) {
					userPositionState.rotationToTarget(rotation, 300)
				}
			}
		}
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
		isUserInRange.value = true
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