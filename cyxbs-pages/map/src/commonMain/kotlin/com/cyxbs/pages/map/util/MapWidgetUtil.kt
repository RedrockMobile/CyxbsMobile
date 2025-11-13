package com.cyxbs.pages.map.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize

/**
 * @Desc : 对于一些Map数据计算的工具类
 * @Author : zzx
 * @Date : 2025/11/13 20:00
 */

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

/**
 * 用于计算当前坐标在地图中的真实坐标
 * @param center 容器中心的坐标
 * @param offset 当前的偏移量
 * @param currentOffset 目前点击的实际坐标
 * @param scale 放缩倍数
 * @return 返回在地图中的真实坐标
 */
fun calculateOriginPosition(
  center: Offset,
  offset: Offset,
  currentOffset: Offset,
  scale: Float
): Offset {
  return Offset(
    x = center.x + (currentOffset.x - offset.x - center.x) / scale,
    y = center.y + (currentOffset.y - offset.y - center.y) / scale
  )
}


fun calculateRatio(
  containerSize: Size
): Float = containerSize.width.run {
  div(containerSize.height)
}
