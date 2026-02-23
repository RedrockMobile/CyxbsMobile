package com.cyxbs.pages.schoolcar.mapcompose

import android.graphics.Color
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.maps.AMapOptions
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.TextureMapView
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MyLocationStyle
import com.cyxbs.pages.schoolcar.R
import kotlinx.coroutines.flow.Flow

@Composable
actual fun PlatformSchoolCarMapCompose(
	modifier: Modifier,
	markers: List<MapMarkerState>,
	cameraEventFlow: Flow<CameraEvent>,
	currentLine: Int?,
	selectSiteId: Int?,
	onEvent: (MapEvent) -> Unit
) {
	val context = LocalContext.current
	val lifecycle = LocalLifecycleOwner.current.lifecycle
	val mapView = remember {
		MapsInitializer.updatePrivacyShow(context, true, true)
		MapsInitializer.updatePrivacyAgree(context, true)

		val defaultLatLng = LatLng(CameraStateDefault.lat, CameraStateDefault.lng) // 你的默认坐标
		val defaultZoom = CameraStateDefault.zoom
		val cameraPosition = CameraPosition(defaultLatLng, defaultZoom, 0f, 0f)
		val options = AMapOptions().apply {
			camera(cameraPosition)
		}

		TextureMapView(context, options).apply { onCreate(Bundle()) }
	}

	val renderer = remember(mapView) {
		mapView.map.uiSettings.isZoomControlsEnabled = false
		mapView.map.uiSettings.isMyLocationButtonEnabled = false
		mapView.map.minZoomLevel = 15f
		AndroidSchoolCarMapRenderer(context, mapView.map, onEvent)
	}

	DisposableEffect(lifecycle, mapView) {
		val observer = LifecycleEventObserver { _, event ->
			when (event) {
				Lifecycle.Event.ON_RESUME -> {
					mapView.onResume()
				}

				Lifecycle.Event.ON_PAUSE -> {
					mapView.onPause()
				}

				Lifecycle.Event.ON_DESTROY -> {
					mapView.onDestroy()
					renderer.onDestroy()
				}

				Lifecycle.Event.ON_CREATE -> {}
				else -> {}
			}
		}
		lifecycle.addObserver(observer)

		onDispose {
			lifecycle.removeObserver(observer)
			// 在该MapCompose被移除的时候一定要调用销毁函数，不然进入详情页的时候并不会走ON_DESTROY，map资源没有被正确释放
			mapView.onDestroy()
			renderer.onDestroy()
		}
	}

	// 当 markers 列表变化时，通知渲染器更新
	LaunchedEffect(markers, currentLine, selectSiteId) {
		renderer.render(markers, currentLine, selectSiteId)
	}
	// 当摄像头状态变化时，更新摄像头
	LaunchedEffect(Unit) {
		cameraEventFlow.collect {
			renderer.doCameraEvent(it)
		}
	}

	AndroidView(
		factory = {
			mapView
		},
		modifier = modifier,
		update = {
		}
	)
}

//得到自己定位图标样式
private fun initLocationType(): MyLocationStyle = MyLocationStyle().apply {
	val descriptor: BitmapDescriptor =
		BitmapDescriptorFactory.fromResource(R.drawable.schoolcar_ic_my)
	interval(2000)
	strokeWidth(0f)
	radiusFillColor(Color.alpha(0))
	myLocationIcon(descriptor)
	myLocationType(MyLocationStyle.LOCATION_TYPE_FOLLOW_NO_CENTER)
}