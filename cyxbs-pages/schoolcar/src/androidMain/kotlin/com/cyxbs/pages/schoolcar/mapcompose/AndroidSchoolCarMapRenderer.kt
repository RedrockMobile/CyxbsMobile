package com.cyxbs.pages.schoolcar.mapcompose

import android.content.Context
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
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
	private val onEvent: (MapEvent) -> Unit
) {
	//ID to Marker
	private val markerCache = mutableMapOf<String, Marker>()

	//ID to car
	private val movingOverlayCache = mutableMapOf<String, MovingPointOverlay>()

	//ID  to markerState
	private val dataCache = mutableMapOf<String, MapMarkerState>()

	private val backgroundCache = getMarkerBackGroundDescriptors()

	private val siteCache = getSiteMakerDescriptors()

	private val carCache = getCarMakerDescriptors()


	private val mineCache =
		BitmapDescriptorFactory.fromBitmap(context.getBitmapBySvg(R.drawable.schoolcar_ic_my))

	init {
		setupListeners()
	}

	private fun setupListeners() {
		// 点击 Marker
		aMap.setOnMarkerClickListener { marker ->
			val uid = markerCache.entries.find { it.value == marker }?.key
			val data = dataCache[uid]
			if (data != null) {
				onEvent(MapEvent.MarkerClick(data))
			}
			true
		}

		aMap.setOnMapClickListener {
			onEvent(MapEvent.MapClick)
		}
	}

	fun updateCamera(state: CameraState) {
		if (state.lat == 0.0 && state.lng == 0.0) return

		val update = CameraUpdateFactory.newLatLngZoom(
			LatLng(state.lat, state.lng),
			state.zoom
		)
		aMap.animateCamera(update)
	}

	// 刷新
	fun render(newList: List<MapMarkerState>, lineId: Int?) {
		val newUids = newList.map { it.uid }.toSet()
		val currentUids = markerCache.keys.toSet()

		(currentUids - newUids).forEach { uid ->
			removeMarker(uid)// 移除老marker
		}

		newList.forEach { item ->
			if (markerCache.containsKey(item.uid)) {
				updateMarker(item, lineId) // 如果已经在marker中了，则更新marker
			} else {
				addMarker(item, lineId) // 没有的话就添加
			}

			dataCache[item.uid] = item
		}
	}

	private fun addMarker(item: MapMarkerState, lineId: Int?) {
		val latLng = LatLng(item.lat, item.lng)

		val safeLineId = lineId ?: -1 // 未选择线路的话id 为 -1， 1号线id 为 0

		val res = calculateResId(item, safeLineId)

		val options = MarkerOptions()
			.position(latLng)
			.icon(res)
			.anchor(0.5f, 0.5f)
			.zIndex(item.type.zIndex)
			.visible(item.visible)

		val marker = aMap.addMarker(options) ?: return
		markerCache[item.uid] = marker

		// 如果不是站点，创建平滑移动层
		if (item.type !is MarkerType.Site) {
			val movingOverlay = MovingPointOverlay(aMap, marker)
			movingOverlayCache[item.uid] = movingOverlay
		}
	}

	private fun updateMarker(newItem: MapMarkerState, lineId: Int?) {
		log("HIIR", lineId.toString())
		val marker = markerCache[newItem.uid] ?: return
		val newLatLng = LatLng(newItem.lat, newItem.lng)
		marker.isVisible = newItem.visible
		marker.zIndex = newItem.type.zIndex

		// 如果多了线路，但没有添加新的线路资源，默认用一号线的资源
		if (newItem.type is MarkerType.Site) {
			val safeLineId = lineId ?: -1
			val res = calculateResId(newItem, safeLineId)
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
			val duration = if (newItem.type is MarkerType.Mine) 2 else 3
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
		mineCache.recycle()

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



	private fun calculateResId(item: MapMarkerState, safeLineId: Int = -1): BitmapDescriptor {
		return when (item.type) {
			is MarkerType.Car -> {
				val index = item.type.type.takeIf { it in carCache.indices } ?: 0
				carCache[index]
			}
			is MarkerType.Mine -> mineCache
			is MarkerType.Site -> {
				val index = (safeLineId + 1).takeIf { it in siteCache.indices } ?: 1
				siteCache[index]
			}
		}
	}
}

