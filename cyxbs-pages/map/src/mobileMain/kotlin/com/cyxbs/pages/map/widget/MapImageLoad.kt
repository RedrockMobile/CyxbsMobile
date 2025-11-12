package com.cyxbs.pages.map.widget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.jvziyaoyao.scale.image.sampling.SamplingCanvas
import com.jvziyaoyao.scale.image.sampling.SamplingCanvasViewPort
import com.jvziyaoyao.scale.image.sampling.rememberSamplingDecoder
import com.jvziyaoyao.scale.zoomable.zoomable.detectTransformGestures

/**
 * 分叉后的地图大图加载，目前这个库只支持mobileMain
 * @param inputStream 图片的ByteArray对象
 * @param pointerEventCallback 手势检测后返回的PointerEvent回调，后续用于点击处理
 */
@Composable
actual fun MapImageLoad(
  inputStream: ByteArray?,
  pointerEventCallback: (PointerEvent) -> Unit
) {
  val (samplingDecoder) = rememberSamplingDecoder(inputStream)
  samplingDecoder?.let { samplingDecoder ->
    val offset = remember { mutableStateOf(Offset.Zero) }
    val scale = remember { mutableStateOf(1F) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val ratio = samplingDecoder.intrinsicSize.run {
      width.div(height)
    }
    Box(
      modifier = Modifier
        .fillMaxSize()
        .onSizeChanged { containerSize = it }
        .pointerInput(Unit) {
          detectTransformGestures { centroid, pan, zoom, _, pointerEvent ->
            pointerEventCallback(pointerEvent) // 传入手势信息的回调
            val oldScale = scale.value
            // 放大比例的约束范围
            val newScale = (oldScale * zoom).coerceIn(1f, 15f)
            val ratioScale = newScale / oldScale
            // 拿到容器中心
            val center = Offset(
              containerSize.width / 2f,
              containerSize.height / 2f
            )
            // 手指相对容器中心的OffSet
            val offCenter = centroid - center
            /*
            如果直接使用(offset.value+offset)，默认是以图片中心进行缩放，会导致手指中心点会"跑"出屏幕外。
            解决思路就是让他"跑回来"
            offset*ratioScale先将当前偏移值*放大的比例，然后他需要跑回来的距离就是目前的位置减去原来的位置
            就是-(offCenter*ratioScale-offCenter)
             */
            val realOffset = offset.value * ratioScale + offCenter * (1f - ratioScale)
            scale.value = newScale
            offset.value = actualOffset(ratio, containerSize, realOffset + pan, newScale)
            true
          }
        }
    ) {
      Box(
        modifier = Modifier
          .graphicsLayer {
            translationX = offset.value.x
            translationY = offset.value.y
            scaleX = scale.value
            scaleY = scale.value
          }
          .fillMaxWidth()
          .aspectRatio(ratio)
          .align(Alignment.Center)
      ) {
        if (containerSize.width > 0 && containerSize.height > 0) {
          // 拿到容器的宽高以及中心坐标
          val weight = containerSize.width.toFloat()
          val height = weight / ratio
          val center = Offset(
            weight / 2f,
            height / 2f
          )
          /*
          计算当前区域原本大小下的位置,screen=center+offset+scale*(local-center)
          故反推就是:local=(screen-center-offset) / scale + center
          因为当前需要高清切片的矩形位置是(0,0,weight,height),根据这个计算而来
           */
          val leftLocal = center.x + (0f - offset.value.x - center.x) / scale.value
          val topLocal = center.y + (0f - offset.value.y - center.y) / scale.value
          val rightLocal = center.x + (weight - offset.value.x - center.x) / scale.value
          val bottomLocal = center.y + (height - offset.value.y - center.y) / scale.value
          // 将区域原本的位置/weight或者height,进行归一化,因为参数传的是需要高清的切片占原来整个图的相对位置(0f-1f)
          val leftVisual = (leftLocal / weight).coerceIn(0f, 1f)
          val rightVisual = (rightLocal / weight).coerceIn(0f, 1f)
          val topVisual = (topLocal / height).coerceIn(0f, 1f)
          val bottomVisual = (bottomLocal / height).coerceIn(0f, 1f)
          SamplingCanvas(
            samplingDecoder = samplingDecoder,
            viewPort = SamplingCanvasViewPort(
              scale = scale.value,
              visualRect = Rect(leftVisual, topVisual, rightVisual, bottomVisual)
            )
          )
        }
      }
    }
  }
}

/**
 * @param ratio 盒子的宽高比
 * @param containerSize 盒子的尺寸
 * @param offset 偏离的offset
 * @param scale 放缩倍数
 * @return 返回经过放缩后的偏离位置(不超过盒子范围)
 */
fun actualOffset(ratio: Float, containerSize: IntSize, offset: Offset, scale: Float): Offset {
  val containerWith = containerSize.width.toFloat() // 容器的宽
  val containerHeight = containerWith / ratio // 用宽/宽高比拿到高
  val scaleWith = containerWith * scale // 放缩后的宽
  val scaleHeight = containerHeight * scale // 放缩后的高
  val maxX = (scaleWith - containerWith).coerceAtLeast(0f) / 2f // 计算以中心为原点两边的范围
  val maxY = (scaleHeight - containerHeight).coerceAtLeast(0f) / 2f // 同上
  return Offset(
    x = offset.x.coerceIn(-maxX, maxX),
    y = offset.y.coerceIn(-maxY, maxY)
  )
}