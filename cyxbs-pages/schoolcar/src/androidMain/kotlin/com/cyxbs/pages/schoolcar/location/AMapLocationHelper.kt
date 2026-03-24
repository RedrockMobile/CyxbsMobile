package com.cyxbs.pages.schoolcar.location

import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.cyxbs.components.init.appContext
import com.cyxbs.pages.schoolcar.bean.GeoLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**  
 * description ： 安卓端的定位由高德定位来实现
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/3/24 16:41
 */
class AMapLocationHelper {
	//https://lbs.amap.com/api/android-location-sdk/guide/android-location/getlocation
	// 官方示例文档

	private val _locationFlow = MutableStateFlow<GeoLocation?>(GeoLocation(29.533059, 106.604296))
	val locationFlow: StateFlow<GeoLocation?> = _locationFlow.asStateFlow()

	private val locationClient: AMapLocationClient by lazy {
		AMapLocationClient.updatePrivacyShow(appContext, true, true)
		AMapLocationClient.updatePrivacyAgree(appContext, true)
		AMapLocationClient(appContext)
	}

	init {
		val options = AMapLocationClientOption().apply {
			locationPurpose = AMapLocationClientOption.AMapLocationPurpose.Transport
			locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
			interval = 1000
			isSensorEnable = false // 因为高德定位SDK的角度实现是根据GPS移动来确定的，在速度很慢的时候失效了，这里调用系统传感器来做角度
			isNeedAddress = false
			isMockEnable = false
			isLocationCacheEnable = true
		}
		locationClient.setLocationOption(options)
		locationClient.setLocationListener { amapLocation ->
			// 官方文档错误码为0的时候定位成功
			if (amapLocation != null && amapLocation.errorCode == 0) {
				_locationFlow.tryEmit(
					GeoLocation(lat = amapLocation.latitude, lng = amapLocation.longitude)
				)
			}
		}
	}


	fun start() {
		// 官方文档建议，在开始定位前，先关闭一次定位
		locationClient.stopLocation()
		locationClient.startLocation()
	}

	fun stop() {
		locationClient.stopLocation()
	}

	fun clear() {
		locationClient.stopLocation()
		locationClient.onDestroy()
	}
}