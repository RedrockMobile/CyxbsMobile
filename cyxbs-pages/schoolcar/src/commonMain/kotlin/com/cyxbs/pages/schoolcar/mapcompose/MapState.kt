package com.cyxbs.pages.schoolcar.mapcompose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * description ： 地图的状态
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/3/18 15:40
 */

@Stable
class MapState(
	private val initialScale: Float = 2.3f,
	private val initialOffset: Offset = Offset.Zero,
	initialContainer: IntSize = IntSize.Zero,
) {
	// 地图的大小
	var container: IntSize by mutableStateOf(initialContainer)

	// 这个center既是地图组件的中心点，因为给图片Box设置了Center的对齐，所以这个center也是地图图片Box的中心
	val center: Offset get() = Offset(container.width / 2f, container.height / 2f)

	// 地图图片的相关数据信息
	var imageSize: IntSize by mutableStateOf(IntSize.Zero)
	var ratio: Float by mutableStateOf(0f)

	// 摄像头视角状态
	val scale get() = scaleAnim.value

	// 基于图片中心点的偏移
	val offset get() = offsetAnim.value

	// Animatable动画数值包装器
	private val scaleAnim = Animatable(initialScale)
	private val offsetAnim = Animatable(initialOffset, Offset.VectorConverter)


	suspend fun setOffset(newPos: Offset) {
		offsetAnim.snapTo(newPos)
	}

	suspend fun setScale(targetScale: Float) {
		scaleAnim.snapTo(targetScale)
	}

	/**
	 * 以动画的方式移动摄像头
	 */
	private suspend fun animateCamera(targetScale: Float, targetOffset: Offset) {
		coroutineScope {
			launch {
				scaleAnim.animateTo(
					targetScale,
					animationSpec = tween(
						durationMillis = 500,
						easing = FastOutSlowInEasing
					)
				)
			}
			launch {
				offsetAnim.animateTo(
					targetOffset,
					animationSpec = tween(
						durationMillis = 500,
						easing = FastOutSlowInEasing
					)
				)
			}
		}
	}

	//更新

	// 更新手势移动平移
	suspend fun updateTransform(
		zoomFactor: Float,
		panDelta: Offset,
		centroid: Offset,
	) {
		if (ratio <= 0f) return
		val oldScale = scale
		val oldOffset = offset // 拿到当前的位移状态

		val newScale = (oldScale * zoomFactor).coerceIn(2.2f, 10f)
		val actualRatio = newScale / oldScale // 实际发生的缩放比例

		// 移动质心相对于屏幕中心点的偏移
		// 因为设置了承载Image的Box平移的中心是Center，并配置了对齐为Center
		val relativeCentroid = centroid - center

		// 补偿因为缩放导致的质心偏移
		val zoomOffset = relativeCentroid * (1f - actualRatio)

		val newOffset = (oldOffset * actualRatio) + zoomOffset + panDelta
		val finalOffset = calculateOffsetWithBounds(newOffset, newScale)
		scaleAnim.snapTo(newScale)
		offsetAnim.snapTo(finalOffset)
	}

	/**
	 * 将图片边界加入offset的计算
	 */
	private fun calculateOffsetWithBounds(
		offset: Offset,
		scale: Float,
	): Offset {
		if (ratio <= 0f) return Offset.Zero
		// 容器的高
		val containerWidth = container.width.toFloat()
		val containerHeight = container.height.toFloat()

		//图片Box的宽高
		val baseContentWidth = container.width.toFloat()
		val baseContentHeight = baseContentWidth / ratio

		val scaleWith = baseContentWidth * scale //缩放后的理论宽度
		val scaleHeight = baseContentHeight * scale// 缩放后的理论高度

		val maxX = ((scaleWith - containerWidth) / 2f).coerceAtLeast(0f)
		val maxY = ((scaleHeight - containerHeight) / 2f).coerceAtLeast(0f)

		return Offset(
			x = offset.x.coerceIn(-maxX, maxX),
			y = offset.y.coerceIn(-maxY, maxY)
		)
	}

	/**
	 * 以目前显示屏幕中心为原点缩放
	 */
	private suspend fun animateScale(targetScale: Float) {
		val finalScale = targetScale.coerceIn(2.2f, 10f)
		val scaleRatio = finalScale / scale
		val newOffset = offset * scaleRatio
		val finalOffset = calculateOffsetWithBounds(newOffset, finalScale)
		animateCamera(finalScale, finalOffset)
	}

	/**
	 * 聚焦到原图上的某个像素点
	 * @param px 原图 X 坐标
	 * @param py 原图 Y 坐标
	 * @param targetScale 聚焦时的目标缩放倍数
	 */
	suspend fun focusOnImagePoint(
		px: Float,
		py: Float,
		targetScale: Float = scale,
	) {
		val containerWidth = container.width.toFloat()
		if (containerWidth <= 0f) return
		// 归一化寻找在图片Box上的坐标
		val u = px / imageSize.width
		val v = py / imageSize.height

		val imageBoxHeight = containerWidth / ratio

		// 因为平移的中心点是Center
		val relX = (u - 0.5f) * containerWidth
		val relY = (v - 0.5f) * imageBoxHeight

		val newScale = targetScale.coerceIn(2.2f, 10f)

		val targetOffset = Offset(
			x = -(relX * newScale),
			y = -(relY * newScale)
		)
		val finalOffset = calculateOffsetWithBounds(targetOffset, newScale)
		animateCamera(newScale, finalOffset)
	}

	/**
	 * 按照屏幕点击位置放大
	 */
	suspend fun zoomByPosition(position: Offset) {
		val relativePosition = center - position
		// 加入新的缩放前，把该点放到屏幕中心应该走的偏移量
		val shouldOffset = offset + relativePosition

		val newScale = (scale + 1.5f).coerceIn(2.2f, 10f)
		val scaleRatio = newScale / scale
		val targetOffset = shouldOffset * scaleRatio
		val finalOffset = calculateOffsetWithBounds(targetOffset, newScale)

		animateCamera(newScale, finalOffset)
	}


	// 放大
	suspend fun zoomExpand() {
		val targetScale = scale + 1.5f
		animateScale(targetScale)
	}

	// 缩小
	suspend fun zoomOut() {
		val targetScale = scale - 1.5f
		animateScale(targetScale)
	}

	// 摄像头恢复
	suspend fun cameraRecover() {
		animateCamera(initialScale, initialOffset)
	}


}
