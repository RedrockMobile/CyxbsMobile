package com.cyxbs.pages.schoolcar.mapcompose

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
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.TextureMapView
import com.cyxbs.components.utils.extensions.log

@Composable
actual fun PlatformSchoolCarMapCompose(
	modifier: Modifier,
	markers: List<MapMarkerState>,
	cameraState: CameraState,
	currentLine: Int?,
	onEvent: (MapEvent) -> Unit
) {
	val context = LocalContext.current
	val lifecycle = LocalLifecycleOwner.current.lifecycle
	val mapView = remember {
		MapsInitializer.updatePrivacyShow(context, true, true)
		MapsInitializer.updatePrivacyAgree(context, true)
		TextureMapView(context).apply { onCreate(Bundle()) }
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
			log("HIIR", "onDispose")
			lifecycle.removeObserver(observer)
			// 在该MapCompose被移除的时候一定要调用销毁函数，不然进入详情页的时候并不会走ON_DESTROY，map资源没有被正确释放
			mapView.onDestroy()
			renderer.onDestroy()
		}
	}

	// 当 markers 列表变化时，通知渲染器更新
	LaunchedEffect(markers,currentLine) {
		renderer.render(markers,currentLine)
	}

	// 当摄像头状态变化时，更新摄像头
	LaunchedEffect(cameraState) {
		renderer.updateCamera(cameraState)
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