package com.cyxbs.pages.schoolcar.mapcompose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
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
	initialScale: Float = 2.3f,
	initialOffset: Offset = Offset.Zero,
	initialContainer: IntSize = IntSize.Zero,
) {
	// 地图的大小
	var container: IntSize by mutableStateOf(initialContainer)

	// 这个center既是地图组件的中心点，因为给图片Box设置了Center的对齐，所以这个center也是地图图片Box的中心
	val center: Offset get() = Offset(container.width / 2f, container.height / 2f)


	// 摄像头视角状态
	val scale get() = scaleAnim.value

	// 基于图片中心点的偏移
	val offset get() = offsetAnim.value

	// Animatable动画数值包装器
	private val scaleAnim = Animatable(initialScale)
	private val offsetAnim = Animatable(initialOffset, Offset.VectorConverter)

	suspend fun animateOffset(newPos: Offset) {
		offsetAnim.animateTo(newPos)
	}

	suspend fun animateScale(targetScale: Float) {
		scaleAnim.animateTo(targetScale)
	}

	suspend fun setOffset(newPos: Offset) {
		offsetAnim.snapTo(newPos)
	}

	suspend fun setScale(targetScale: Float) {
		scaleAnim.snapTo(targetScale)
	}

	// 并行缩放和移动动画
	suspend fun animateCamera(targetScale: Float, targetOffset: Offset) {
		coroutineScope {
			launch { scaleAnim.animateTo(targetScale) }
			launch { offsetAnim.animateTo(targetOffset) }
		}
	}


	// 更新平移
	suspend fun updateTransform(
		zoomFactor: Float,
		panDelta: Offset,
		centroid: Offset,
		imageAspectRatio: Float
	) {
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
		val finalOffset = calculateOffsetWithBounds(newOffset, newScale, imageAspectRatio)
		scaleAnim.snapTo(newScale)
		offsetAnim.snapTo(finalOffset)
	}

	// 将无图片边界加入offset的计算
	private fun calculateOffsetWithBounds(
		offset: Offset,
		scale: Float,
		imageAspectRatio: Float
	): Offset {
		// 容器的高
		val containerWidth = container.width.toFloat()
		val containerHeight = container.height.toFloat()

		//图片Box的宽高
		val baseContentWidth = container.width.toFloat()
		val baseContentHeight = baseContentWidth / imageAspectRatio

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
	 * 聚焦到原图上的某个像素点
	 * @param px 原图 X 坐标
	 * @param py 原图 Y 坐标
	 * @param intrinsicWidth 原图总宽度
	 * @param intrinsicHeight 原图总高度
	 * @param targetScale 聚焦时的目标缩放倍数，默认使用当前缩放
	 * @param ratio 图片宽高比
	 */
	suspend fun focusOnImagePoint(
		px: Float,
		py: Float,
		intrinsicWidth: Float,
		intrinsicHeight: Float,
		ratio: Float,
		targetScale: Float = scale,
	) {
		val containerWidth = container.width.toFloat()
		if (containerWidth <= 0f) return


		val u = px / intrinsicWidth
		val v = py / intrinsicHeight

		val baseW = containerWidth
		val baseH = containerWidth / ratio

		val relX = (u - 0.5f) * baseW
		val relY = (v - 0.5f) * baseH

		val targetOffset = Offset(
			x = -(relX * targetScale),
			y = -(relY * targetScale)
		)

		val finalOffset = calculateOffsetWithBounds(targetOffset, targetScale, ratio)

		animateCamera(targetScale, finalOffset)
	}


}
