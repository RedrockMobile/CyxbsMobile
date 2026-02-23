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
		backgroundHighlightMarker?.let { bgMarker ->
			if (bgMarker.isVisible) {
				val resIndex = (safeLineId + 1).takeIf { it in backgroundCache.indices } ?: 1
				bgMarker.setIcon(backgroundCache[resIndex])
			}
		}
		val newUids = newList.map { it.uid }.toSet()
		val currentUids = markerCache.keys.toSet()

		(currentUids - newUids).forEach { uid ->
			removeMarker(uid)// 移除老marker
		}

		newList.forEach { item ->
			if (markerCache.containsKey(item.uid)) {
				updateMarker(item, safeLineId) // 如果已经在marker中了，则更新marker
			} else {
				addMarker(item, safeLineId) // 没有的话就添加
			}

			dataCache[item.uid] = item
		}
		if (highlightStationId != null) {
			val targetUid = "site_$highlightStationId"
			val targetData = newList.find { it.uid == targetUid }
			if (targetData != null) {
				showBackgroundForSite(targetData, safeLineId)
			} else {
				hideBackground()
			}
		} else {
			hideBackground()
		}
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
		val newLatLng = LatLng(newItem.lat, newItem.lng)
		marker.isVisible = newItem.visible
		marker.zIndex = newItem.type.zIndex

		// 如果多了线路，但没有添加新的线路资源，默认用一号线的资源
		if (newItem.type is MarkerType.Site) {
			val res = calculateRes(newItem, currentLineId)
			marker.setIcon(res)
		}
		if (marker.position == newLatLng) return
		// 站点的直接把站点的位置移动到新位置
		if (newItem.type is MarkerType.Site) {
			marker.position = newLatLng
		} else {
			val overlay = movingOverlayCache[newItem.uid] ?: return
			overlay.stopMove()
			val currentPos = overlay.position ?: marker.position
			overlay.setPoints(listOf(currentPos, newLatLng))
			val duration = 3
			overlay.setTotalDuration(duration)
			overlay.startSmoothMove()
			if (newItem.rotation != 0f) {
				marker.rotateAngle = newItem.rotation
			}
		}
	}

	private fun removeMarker(uid: String) {
		movingOverlayCache[uid]?.destroy()
		movingOverlayCache.remove(uid)
		markerCache[uid]?.remove()
		markerCache.remove(uid)
		dataCache.remove(uid)
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
			CameraEvent.ZoomExpand -> doZoomExpand()
			CameraEvent.ZoomOut -> doZoomOut()
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
		aMap.animateCamera( CameraUpdateFactory.zoomIn())
	}

	fun doZoomOut() {
		aMap.animateCamera( CameraUpdateFactory.zoomOut())
	}
}

