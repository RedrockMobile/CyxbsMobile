package com.cyxbs.pages.schoolcar.mapcompose

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.amap.api.maps.AMapOptions
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.TextureMapView
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MyLocationStyle
import com.cyxbs.components.config.isDebug
import com.cyxbs.components.utils.extensions.log
import com.cyxbs.pages.schoolcar.R
import com.cyxbs.pages.schoolcar.viewmodel.SchoolCarViewModel
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
	val applicationContext = LocalContext.current.applicationContext
	val viewModel = viewModel(SchoolCarViewModel::class)
	// 需要的权限
	val locationPermissions = arrayOf(
		Manifest.permission.ACCESS_FINE_LOCATION,
		Manifest.permission.ACCESS_COARSE_LOCATION
	)

	var isLocationGranted by remember {
		mutableStateOf(
			locationPermissions.all {
				ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
			}
		)
	}

	val permissionLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.RequestMultiplePermissions()
	) { permissions ->
		isLocationGranted = permissions.values.all { it }
		if (isLocationGranted && isDebug()) {
			log("HIIR", "定位权限已获取")
		} else {
			log("HIIR", "定位权限被拒绝")
		}
	}

	// 每次进入界面先来一波权限权限申请
	LaunchedEffect(Unit) {
		if (!isLocationGranted) {
			permissionLauncher.launch(locationPermissions)
		}
	}

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

	val locationClient = remember {
		AMapLocationClient(applicationContext)
	}
	val locationListener = remember(mapView) {
		AMapLocationListener { location ->
			viewModel.updateLocation(location)
		}
	}

	// 用于控制是否定位
	LaunchedEffect(isLocationGranted) {
		if (isLocationGranted) {
			locationClient.apply {
				setLocationOption(AMapLocationClientOption())
				setLocationListener(locationListener)
			}
			locationClient.startLocation()
		} else {
			locationClient.stopLocation()
		}
	}

	// 监听是否开启定位地图
	LaunchedEffect(viewModel.enableMyLocation.value, isLocationGranted, mapView) {
		if (viewModel.enableMyLocation.value && isLocationGranted) {
			val myLocationStyle = initLocationType()
			mapView.map.myLocationStyle = myLocationStyle
			mapView.map.isMyLocationEnabled = true
		} else {
			mapView.map.isMyLocationEnabled = false
		}
	}


	DisposableEffect(lifecycle, mapView) {
		val observer = LifecycleEventObserver { _, event ->
			when (event) {
				Lifecycle.Event.ON_RESUME -> mapView.onResume()
				Lifecycle.Event.ON_PAUSE -> mapView.onPause()
				else -> {}
			}
		}
		lifecycle.addObserver(observer)
		onDispose {
			lifecycle.removeObserver(observer)
			mapView.onDestroy()
			renderer.onDestroy()
			locationClient.onDestroy()
		}
	}


	// 当 markers 列表变化时，通知渲染器更新
	LaunchedEffect(markers, currentLine, selectSiteId) {
		renderer.render(markers, currentLine, selectSiteId)
	}

	// 接收摄像头事件
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