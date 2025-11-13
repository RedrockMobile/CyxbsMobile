package com.cyxbs.pages.map.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.cyxbs.pages.map.util.calculateOriginPosition
import cyxbsmobile.cyxbs_pages.map.generated.resources.Res
import cyxbsmobile.cyxbs_pages.map.generated.resources.map_ic_local
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/**
 * @Desc : Map主体
 * @Author : zzx
 * @Date : 2025/11/12 17:23
 */

@Composable
fun MapWidget(inputStream: ByteArray?) {
  val coroutineScope = rememberCoroutineScope()
  val mapWidgetState = rememberMapWidgetState()
  val anchorItemState = rememberAnchorItemState()
  Box() {
    MapImageLoad(
      inputStream = inputStream,
      mapWidgetState = mapWidgetState,
      anchorItemState = listOf(anchorItemState),
      onMapWidgetStateChange = { scale, offset ->
        coroutineScope.launch {
          mapWidgetState.setScale(scale)
          mapWidgetState.setOffset(offset)
        }
      },
      onClick = { offset ->
        anchorItemState.visible = true
        val ratio = 8022f / 14267f // 图片的宽高比例，后续拿api替换，这里与box的宽高不同
        // TODO 补充注释
        val originOffset = calculateOriginPosition(
          mapWidgetState.center,
          mapWidgetState.offset,
          offset,
          mapWidgetState.scale
        ) - Offset(
          0f,
          (mapWidgetState.container.height.toFloat() - mapWidgetState.container.width.toFloat() / ratio) / 2f
        )
        anchorItemState.position = originOffset
      },
      onDoubleClick = { offset ->
        // 如果大于6f,则还原初始
        if (mapWidgetState.scale > 6f) {
          coroutineScope.launch {
            launch {
              mapWidgetState.animateScale(1f)
            }
            launch {
              mapWidgetState.animateOffset(Offset.Zero)
            }
          }
        } else {
          coroutineScope.launch {
            // 执行动画
            launch {
              mapWidgetState.animateScale(6f)
            }
            launch {
              mapWidgetState.animateOffset((mapWidgetState.center - offset) * (6f / mapWidgetState.scale) + mapWidgetState.offset)
            }
          }
        }
      }
    )
  }
}

@Composable
fun AnchorItem(modifier: Modifier = Modifier) {
  Image(
    modifier = modifier,
    painter = painterResource(Res.drawable.map_ic_local),
    contentDescription = null
  )
}