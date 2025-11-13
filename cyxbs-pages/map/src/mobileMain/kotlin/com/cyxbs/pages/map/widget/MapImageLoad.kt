package com.cyxbs.pages.map.widget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.cyxbs.pages.map.util.actualOffset
import com.cyxbs.pages.map.util.calculateOriginPosition
import com.cyxbs.pages.map.util.calculateRatio
import com.jvziyaoyao.scale.image.sampling.SamplingCanvas
import com.jvziyaoyao.scale.image.sampling.SamplingCanvasViewPort
import com.jvziyaoyao.scale.image.sampling.rememberSamplingDecoder
import com.jvziyaoyao.scale.zoomable.zoomable.detectTransformGestures

/**
 * 分叉后的地图大图加载，目前这个库只支持mobileMain
 * @param inputStream 图片的ByteArray对象
 * @param mapWidgetState 地图状态的统一管理处
 * @param onMapWidgetStateChange 地图状态改变
 * @param onClick 点击回调
 */
@Composable
actual fun MapImageLoad(
  inputStream: ByteArray?,
  mapWidgetState: MapWidgetState,
  anchorItemState: List<AnchorItemState>,
  onMapWidgetStateChange: (scale: Float, offset: Offset) -> Unit,
  onClick: (offset: Offset) -> Unit,
  onDoubleClick: (offset: Offset) -> Unit
) {
  val (samplingDecoder) = rememberSamplingDecoder(inputStream)
  samplingDecoder?.let { samplingDecoder ->
    val ratio = calculateRatio(samplingDecoder.intrinsicSize)
    Box(
      modifier = Modifier
        .fillMaxSize()
        .onSizeChanged {
          mapWidgetState.container = it
        }
        .pointerInput(Unit) {
          mapWidgetState.stop()
          detectTransformGestures(
            onTap = onClick,
            onDoubleTap = onDoubleClick
          ) { centroid, pan, zoom, _, _ ->
            val oldScale = mapWidgetState.scale
            // 放大比例的约束范围
            val newScale = (oldScale * zoom).coerceIn(1f, 15f)
            val ratioScale = newScale / oldScale
            // 手指相对容器中心的OffSet
            val offCenter = centroid - mapWidgetState.center
            /*
            如果直接使用(offset.value+offset)，默认是以图片中心进行缩放，会导致手指中心点会"跑"出屏幕外。
            解决思路就是让他"跑回来"
            offset*ratioScale先将当前偏移值*放大的比例，然后他需要跑回来的距离就是目前的位置减去原来的位置
            就是-(offCenter*ratioScale-offCenter)
             */
            val realOffset = mapWidgetState.offset * ratioScale + offCenter * (1f - ratioScale)
            onMapWidgetStateChange(
              newScale,
              actualOffset(ratio, mapWidgetState.container, realOffset + pan, newScale)
            )
            true
          }
        }
    ) {
      Box(
        modifier = Modifier
          .graphicsLayer {
            translationX = mapWidgetState.offset.x
            translationY = mapWidgetState.offset.y
            scaleX = mapWidgetState.scale
            scaleY = mapWidgetState.scale
          }
          .fillMaxWidth()
          .aspectRatio(ratio)
          .align(Alignment.Center)
      ) {
        if (mapWidgetState.container.width > 0 && mapWidgetState.container.height > 0) {
          // 拿到容器的宽高以及中心坐标
          val weight = mapWidgetState.container.width.toFloat()
          val height = weight / ratio
          val center = mapWidgetState.center
          /*
          计算当前区域原本大小下的位置,screen=center+offset+scale*(local-center)
          故反推就是:local=(screen-center-offset) / scale + center
          因为当前需要高清切片的矩形位置是(0,0,weight,height),根据这个计算而来
           */
          val leftTopOrigin = calculateOriginPosition(
            center,
            mapWidgetState.offset,
            Offset(0f, 0f),
            mapWidgetState.scale
          )
          val rightBottomOrigin = calculateOriginPosition(
            center,
            mapWidgetState.offset,
            Offset(weight, height),
            mapWidgetState.scale
          )
          // 将区域原本的位置/weight或者height,进行归一化,因为参数传的是需要高清的切片占原来整个图的相对位置(0f-1f)
          val leftVisual = (leftTopOrigin.x / weight).coerceIn(0f, 1f)
          val rightVisual = (rightBottomOrigin.x / weight).coerceIn(0f, 1f)
          val topVisual = (leftTopOrigin.y / height).coerceIn(0f, 1f)
          val bottomVisual = (rightBottomOrigin.y / height).coerceIn(0f, 1f)
          SamplingCanvas(
            samplingDecoder = samplingDecoder,
            viewPort = SamplingCanvasViewPort(
              scale = mapWidgetState.scale,
              visualRect = Rect(leftVisual, topVisual, rightVisual, bottomVisual)
            )
          )
          anchorItemState.forEach { anchorItemState ->
            if (anchorItemState.visible) {
              AnchorItem(
                modifier = Modifier
                  .size(45.dp / mapWidgetState.scale)
                  .onSizeChanged { anchorItemState.size = it }
                  .graphicsLayer {
                    translationX = anchorItemState.position.x - anchorItemState.size.width / 2
                    translationY = anchorItemState.position.y - anchorItemState.size.height
                  }
              )
            }
          }
        }
      }
    }
  }
}