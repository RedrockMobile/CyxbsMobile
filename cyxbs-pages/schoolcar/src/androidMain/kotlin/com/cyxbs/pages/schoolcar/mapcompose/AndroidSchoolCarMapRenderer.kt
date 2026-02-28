package com.cyxbs.pages.schoolcar.mapcompose

import android.content.Context
import android.view.animation.LinearInterpolator
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.animation.Animation
import com.amap.api.maps.model.animation.ScaleAnimation
import com.amap.api.maps.utils.overlay.MovingPointOverlay
import com.cyxbs.components.utils.extensions.log
import com.cyxbs.pages.schoolcar.R

/**
 * description ： 安卓平台下的SchoolCarMap渲染器
 * 用来缓存，渲染marker
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/2/21 23:40
 */
class AndroidSchoolCarMapRenderer(
	private val context: Context,
	private val aMap: AMap,
	private val onEvent: (MapEvent) -> Unit,
) {
	//UID to Marker
	private val markerCache = mutableMapOf<String, Marker>()

	//UID to car
	private val movingOverlayCache = mutableMapOf<String, MovingPointOverlay>()

	//UID  to markerState
	private val dataCache = mutableMapOf<String, MapMarkerState>()

	private val backgroundCache = getMarkerBackGroundDescriptors()

	private val siteCache = getSiteMakerDescriptors()

	private val carCache = getCarMakerDescriptors()


	private var backgroundHighlightMarker: Marker? = null

	private var prevHighlightStation: Int? = null

	private var prevSelectLine: Int? = null

	private val highlightAnimate by lazy {
		ScaleAnimation(0.8F, 1F, 0.8F, 1F).apply {
			setInterpolator(LinearInterpolator())
			setDuration(3000)
			repeatMode = Animation.REVERSE
			repeatCount = Animation.INFINITE
		}
	}

	init {
		setupListeners()
	}

	private fun setupListeners() {
		// 点击 Marker
		aMap.setOnMarkerClickListener { marker ->
			val uid = marker.`object` as? String
			val data = uid?.let { dataCache[it] }
			if (data != null) {
				onEvent(MapEvent.MarkerClick(data))
			}
			true
		}

		aMap.setOnMapClickListener {
			onEvent(MapEvent.MapClick)
		}
	}

	// 刷新
	// 根据MarkerList + currentLineId + 高亮背景站点id来刷新地图marker显示
	fun render(
		newList: List<MapMarkerState>,
		currentLineId: Int?,
		highlightStationId: Int?
	) {
		val safeLineId = currentLineId ?: -1
		val newMap = newList.associateBy { it.uid }
		val iterator = dataCache.iterator()
		while (iterator.hasNext()) {
			val entry = iterator.next()
			if (!newMap.containsKey(entry.key)) {
				removeMarker(entry.key)
				iterator.remove()
			}
		}
		newList.forEach { newState ->
			val oldState = dataCache[newState.uid]
			if (oldState == null) {
				addMarker(newState, safeLineId)
			} else {
				updateMarker(newState, safeLineId)
			}
			dataCache[newState.uid] = newState
		}

		val highlightChanged = highlightStationId != prevHighlightStation
		val lineChanged = currentLineId != prevSelectLine
		if (highlightChanged || lineChanged) {
			if (highlightStationId != null) {
				val targetUid = "site_$highlightStationId"
				val targetData = newMap[targetUid]
				showBackgroundForSite(targetData, safeLineId)
			} else {
				hideBackground()
			}
		}
		prevHighlightStation = highlightStationId
		prevSelectLine = currentLineId
	}

	private fun addMarker(item: MapMarkerState, currentLineId: Int) {
		val latLng = LatLng(item.lat, item.lng)
		val res = calculateRes(item, currentLineId)

		val options = MarkerOptions()
			.position(latLng)
			.icon(res)
			.anchor(0.5f, 0.5f)
			.zIndex(item.type.zIndex)
			.visible(item.visible)

		val marker = aMap.addMarker(options) ?: return
		marker.`object` = item.uid
		markerCache[item.uid] = marker

		// 如果不是站点，创建平滑移动层
		if (item.type !is MarkerType.Site) {
			val movingOverlay = MovingPointOverlay(aMap, marker)
			movingOverlayCache[item.uid] = movingOverlay
		}
	}

	private fun updateMarker(newItem: MapMarkerState, currentLineId: Int) {
		val marker = markerCache[newItem.uid] ?: return
		val oldItem = dataCache[newItem.uid] ?: return

		if (oldItem.visible != newItem.visible) {
			marker.isVisible = newItem.visible
		}

		if (newItem.type is MarkerType.Site) {
			log("HIIR", "${currentLineId},${prevSelectLine}")
			if (currentLineId != prevSelectLine) {
				val res = calculateRes(newItem, currentLineId)
				marker.setIcon(res)
			}

			val newLatLng = LatLng(newItem.lat, newItem.lng)
			if (marker.position != newLatLng) {
				marker.position = newLatLng
			}
		} else {
			val newLatLng = LatLng(newItem.lat, newItem.lng)
			if (marker.position != newLatLng) {
				val overlay = movingOverlayCache[newItem.uid]
				if (overlay != null) {
					overlay.stopMove()
					val currentPos = overlay.position ?: marker.position
					overlay.setPoints(listOf(currentPos, newLatLng))
					overlay.setTotalDuration(2)
					overlay.startSmoothMove()
				}
			}
		}
	}

	private fun removeMarker(uid: String) {
		movingOverlayCache[uid]?.destroy()
		movingOverlayCache.remove(uid)
		markerCache[uid]?.remove()
		markerCache.remove(uid)
	}

	fun onDestroy() {
		movingOverlayCache.values.forEach { it.destroy() }
		movingOverlayCache.clear()

		markerCache.values.forEach { it.remove() }
		markerCache.clear()

		dataCache.clear()

		backgroundCache.forEach { it.recycle() }
		siteCache.forEach { it.recycle() }
		carCache.forEach { it.recycle() }

		backgroundHighlightMarker?.remove()
	}


	private fun getMarkerBackGroundDescriptors(): List<BitmapDescriptor> {
		return buildList {
			with(context) {
				add(BitmapDescriptorFactory.fromBitmap(getBitmapBySvg(R.drawable.schoolcar_ic_background_0)))
				add(BitmapDescriptorFactory.fromBitmap(getBitmapBySvg(R.drawable.schoolcar_ic_background_1)))
				add(BitmapDescriptorFactory.fromBitmap(getBitmapBySvg(R.drawable.schoolcar_ic_background_2)))
				add(BitmapDescriptorFactory.fromBitmap(getBitmapBySvg(R.drawable.schoolcar_ic_background_3)))
				add(BitmapDescriptorFactory.fromBitmap(getBitmapBySvg(R.drawable.schoolcar_ic_background_4)))
			}
		}
	}

	private fun getSiteMakerDescriptors(): List<BitmapDescriptor> {
		return buildList {
			with(context) {
				add(BitmapDescriptorFactory.fromBitmap(getBitmapBySvg(R.drawable.schoolcar_ic_site_0)))
				add(BitmapDescriptorFactory.fromBitmap(getBitmapBySvg(R.drawable.schoolcar_ic_site_1)))
				add(BitmapDescriptorFactory.fromBitmap(getBitmapBySvg(R.drawable.schoolcar_ic_site_2)))
				add(BitmapDescriptorFactory.fromBitmap(getBitmapBySvg(R.drawable.schoolcar_ic_site_3)))
				add(BitmapDescriptorFactory.fromBitmap(getBitmapBySvg(R.drawable.schoolcar_ic_site_4)))
			}
		}
	}

	private fun getCarMakerDescriptors(): List<BitmapDescriptor> {
		return buildList {
			with(context) {
				add(BitmapDescriptorFactory.fromBitmap(getBitmapBySvg(R.drawable.schoolcar_ic_car_1)))
				add(BitmapDescriptorFactory.fromBitmap(getBitmapBySvg(R.drawable.schoolcar_ic_car_2)))
				add(BitmapDescriptorFactory.fromBitmap(getBitmapBySvg(R.drawable.schoolcar_ic_car_3)))
				add(BitmapDescriptorFactory.fromBitmap(getBitmapBySvg(R.drawable.schoolcar_ic_car_4)))
			}
		}
	}


	private fun calculateRes(item: MapMarkerState, safeLineId: Int = -1): BitmapDescriptor {
		return when (item.type) {
			is MarkerType.Car -> {
				val index = item.type.type.takeIf { it in carCache.indices } ?: 0
				carCache[index]
			}

			is MarkerType.Site -> {
				val index = (safeLineId + 1).takeIf { it in siteCache.indices } ?: 1
				siteCache[index]
			}
		}
	}

	private fun showBackgroundForSite(siteData: MapMarkerState?, currentLineId: Int) {
		siteData ?: return
		val resIndex = (currentLineId + 1).takeIf { it in backgroundCache.indices } ?: 1
		val bgRes = backgroundCache[resIndex]
		if (backgroundHighlightMarker == null) {
			// 第一次点击，初始化这个唯一的背景 Marker
			val options = MarkerOptions()
				.position(LatLng(siteData.lat, siteData.lng))
				.icon(bgRes)
				.anchor(0.5f, 0.5f)
				.zIndex(40f)
				.visible(true)
			backgroundHighlightMarker = aMap.addMarker(options)
			backgroundHighlightMarker?.setAnimation(highlightAnimate)
		} else {
			// 已经存在，直接瞬间移动过去并更新配置
			backgroundHighlightMarker?.apply {
				position = LatLng(siteData.lat, siteData.lng)
				setIcon(bgRes)
				isVisible = true
				startAnimation()
			}
		}
	}

	fun hideBackground() {
		backgroundHighlightMarker?.isVisible = false
	}

	fun doCameraEvent(event: CameraEvent) {
		when (event) {
			is CameraEvent.Focus -> doFocus(event)
			is CameraEvent.ZoomExpand -> doZoomExpand()
			is CameraEvent.ZoomOut -> doZoomOut()
			is CameraEvent.Positioning -> doPositioning()
		}
	}

	fun doFocus(event: CameraEvent.Focus) {
		if (event.lat == 0.0 && event.lng == 0.0) return

		val update = CameraUpdateFactory.newLatLngZoom(
			LatLng(event.lat, event.lng),
			event.zoom
		)
		aMap.animateCamera(update, 400, null)
	}

	fun doZoomExpand() {
		aMap.animateCamera(CameraUpdateFactory.zoomIn())
	}

	fun doZoomOut() {
		aMap.animateCamera(CameraUpdateFactory.zoomOut())
	}

	fun doPositioning() {
		val validLocation = aMap.myLocation?.takeIf {
			it.latitude != 0.0 || it.longitude != 0.0
		}
		val cameraUpdate = if (validLocation != null) {
			CameraUpdateFactory.newLatLngZoom(
				LatLng(validLocation.latitude, validLocation.longitude),
				17f
			)
		} else {
			CameraUpdateFactory.newLatLngZoom(
				LatLng(CameraStateDefault.lat, CameraStateDefault.lng),
				CameraStateDefault.zoom
			)
		}
		aMap.animateCamera(cameraUpdate)
	}
}

