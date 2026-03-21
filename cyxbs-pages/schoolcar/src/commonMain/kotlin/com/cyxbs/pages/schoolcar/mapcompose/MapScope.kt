package com.cyxbs.pages.schoolcar.mapcompose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.extensions.log
import kotlinx.coroutines.CoroutineScope

/**  
 * description ： 校车地图组件的Scope
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/3/19 21:42
 */
interface MapScope {
	// 将原图像素坐标 (px, py) 转换为当前屏幕上图片的 Offset(以左上角为原点)
	fun mapToImageScreen(px: Float, py: Float): Offset

	/**
	 * @param position 基于原图的坐标
	 */
	@Composable
	fun Marker(
		position: Offset,
		anchor: Offset = Offset(0.5f, 0.5f),//默认居中对齐
		onMarkerClick: (() -> Unit)? = null,
		content: @Composable BoxScope.() -> Unit
	)
}

class MapScopeImpl(
	private val coroutineScope: CoroutineScope,
	private val mapState: MapState,
	private val imageSize: Size,
	private val ratio: Float
) : MapScope {
	override fun mapToImageScreen(
		px: Float,
		py: Float
	): Offset {
		val containerSize = mapState.container
		if (containerSize.width <= 0 || containerSize.height <= 0) return Offset.Zero
		val boxWidth = containerSize.width.toFloat()
		val boxHeight = boxWidth / ratio
		val u = px / imageSize.width
		val v = py / imageSize.height

		return Offset(
			x = u * boxWidth,
			y = v * boxHeight
		)
	}

	@Composable
	override fun Marker(
		position: Offset,
		anchor: Offset,
		onMarkerClick: (() -> Unit)?,
		content: @Composable BoxScope.() -> Unit
	) {
		Box(
			modifier = Modifier
				.graphicsLayer {
					// 因为可能会涉及到高频运动的车俩，所以用graphicsLayer来平移定位，让刷新限制在绘制阶段
					val localPosition = mapToImageScreen(position.x, position.y)
					val offsetX = (localPosition.x - size.width * anchor.x)
					val offsetY = (localPosition.y - size.height * anchor.y)
					translationX = offsetX
					translationY = offsetY

					// 方向缩放
					val invScale = if (mapState.scale > 0f) 1f / mapState.scale else 1f
					scaleX = invScale
					scaleY = invScale
					transformOrigin = TransformOrigin(anchor.x, anchor.y)
				}.clickableNoIndicator(enabled = onMarkerClick != null) {
					onMarkerClick?.invoke()
				}
		) {
			content()
		}
	}

}