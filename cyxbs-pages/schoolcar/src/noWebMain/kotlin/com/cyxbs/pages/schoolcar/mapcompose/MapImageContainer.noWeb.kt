package com.cyxbs.pages.schoolcar.mapcompose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toIntSize
import com.cyxbs.pages.schoolcar.utils.calculateAspectRatio
import com.jvziyaoyao.scale.image.sampling.SamplingCanvas
import com.jvziyaoyao.scale.image.sampling.SamplingCanvasViewPort
import com.jvziyaoyao.scale.image.sampling.rememberSamplingDecoder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
actual fun MapImageContainer(
	modifier: Modifier,
	imageBytes: ByteArray?,
	mapState: MapState,
	cameraEventFlow: Flow<CameraEvent>,
	onMapEvent: (MapEvent) -> Unit,
	markerContent: @Composable MapScope.() -> Unit
) {
	val (samplingDecoder) = rememberSamplingDecoder(imageBytes)
	samplingDecoder?.let {
		val ratio = samplingDecoder.intrinsicSize.calculateAspectRatio()
		val coroutineScope = rememberCoroutineScope()
		val viewPort by remember {
			derivedStateOf {
				if (mapState.container.width == 0 || mapState.container.height == 0 || mapState.scale == 0f) {
					return@derivedStateOf SamplingCanvasViewPort(
						scale = mapState.scale,
						visualRect = Rect.Zero
					)
				}
				val containerWidth = mapState.container.width.toFloat()
				val containerHeight = mapState.container.height.toFloat()

				val baseContentHeight = containerWidth / ratio

				val scaleWidth = containerWidth * mapState.scale
				val scaleHeight = baseContentHeight * mapState.scale

				val visibleLeftPx = (scaleWidth - containerWidth) / 2f - mapState.offset.x
				val visibleTopPx = (scaleHeight - containerHeight) / 2f - mapState.offset.y
				val visibleRightPx = visibleLeftPx + containerWidth
				val visibleBottomPx = visibleTopPx + containerHeight
				// 归一化操作
				SamplingCanvasViewPort(
					scale = mapState.scale,
					visualRect = Rect(
						left = (visibleLeftPx / scaleWidth).coerceIn(0f, 1f),
						top = (visibleTopPx / scaleHeight).coerceIn(0f, 1f),
						right = (visibleRightPx / scaleWidth).coerceIn(0f, 1f),
						bottom = (visibleBottomPx / scaleHeight).coerceIn(0f, 1f)
					)
				)
			}
		}


		Box(
			modifier = modifier
				.onSizeChanged {
					mapState.container = it
				}
				.pointerInput(Unit) {
					detectTransformGestures(
						onTap = {
							onMapEvent(MapEvent.MapClick)
						},
						onDoubleTap = {
							coroutineScope.launch {
								mapState.zoomByPosition(it)
							}
						},
						onGesture = { centroid, panDelta, zoom, _, _ ->
							// 虽然在这里启动协程在运行时会创建很多个协程出来，但是官方就这么写例子的
							coroutineScope.launch {
								mapState.updateTransform(zoom, panDelta, centroid)
							}
							true
						})
				}
		) {

			//=============地图渲染层级===============
			Box(
				modifier = Modifier
					.align(Alignment.Center)
					.graphicsLayer {
						translationX = mapState.offset.x
						translationY = mapState.offset.y
						scaleX = mapState.scale
						scaleY = mapState.scale
						transformOrigin = TransformOrigin.Center
					}
					.fillMaxWidth()
					.aspectRatio(ratio)
			) {
				SamplingCanvas(
					samplingDecoder = samplingDecoder,
					viewPort = viewPort
				)

				// ==============Marker的列图层================
				val mapScopeImpl =
					remember(coroutineScope, mapState, samplingDecoder.intrinsicSize, ratio) {
						MapScopeImpl(mapState, samplingDecoder.intrinsicSize, ratio)
					}
				mapScopeImpl.markerContent()
			}

		}

		LaunchedEffect(samplingDecoder.intrinsicSize) {
			mapState.ratio = samplingDecoder.intrinsicSize.calculateAspectRatio()
			mapState.imageSize = samplingDecoder.intrinsicSize.toIntSize()
		}

		LaunchedEffect(samplingDecoder.intrinsicSize) {
			cameraEventFlow.collect {
				when (it) {
					is CameraEvent.Focus -> {
						mapState.focusOnImagePoint(it.x, it.y, it.zoom)
					}

					CameraEvent.Positioning -> {
					}

					CameraEvent.ZoomExpand -> {
						mapState.zoomExpand()
					}

					CameraEvent.ZoomOut -> {
						mapState.zoomOut()
					}

					CameraEvent.Recover -> {
						mapState.cameraRecover()
					}
				}
			}
		}
	}
}
