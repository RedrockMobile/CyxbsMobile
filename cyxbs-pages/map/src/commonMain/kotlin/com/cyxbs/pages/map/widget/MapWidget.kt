package com.cyxbs.pages.map.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.pages.map.model.bean.MapInfo
import com.cyxbs.pages.map.viewmodel.MapComposeViewModel
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
fun MapWidgetCompose(
  modifier: Modifier = Modifier,
  inputStream: ByteArray?,
  mapInfo: MapInfo,
  mapWidgetState: MapWidgetState,
  anchorItemState: AnchorItemState,
  anchorItemStateList: List<AnchorItemState>
) {
  val viewmodel = viewModel(MapComposeViewModel::class)
  val coroutineScope = rememberCoroutineScope()
  Box(
    modifier = modifier.background(Color(0xFFA8BCF1))
  ) {
    MapImageLoad(
      inputStream = inputStream,
      mapWidgetState = mapWidgetState,
      onMapContainerChange = { size ->
        viewmodel.mapContainer.value = size
      },
      onMapWidgetStateChange = { scale, offset ->
        coroutineScope.launch {
          mapWidgetState.setScale(scale)
          mapWidgetState.setOffset(offset)
        }
      },
      onClick = { offset ->
        viewmodel.clickAnchorItem(
          coroutineScope,
          offset,
          mapInfo
        )
      },
      onDoubleClick = { offset ->
        // 如果大于6f,则还原初始
        if (mapWidgetState.scale >= 6f) {
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
      },
      anchorContent = {
        if (anchorItemState.visible) {
          AnchorItem(
            mapWidgetState,
            anchorItemState
          )
        }
        anchorItemStateList.forEach { anchorItemState ->
          if (anchorItemState.visible) {
            AnchorItem(
              mapWidgetState,
              anchorItemState
            )
          }
        }
      }
    )
  }
}

@Composable
fun AnchorItem(
  mapWidgetState: MapWidgetState,
  anchorItemState: AnchorItemState
) {
  // 这里不推荐写成size(45.dp / scale),因为会不断触发measure->layout->draw的重组
  // 应该写在graphicsLayer里面，只会在draw阶段重组
  AnchorImage(
    modifier = Modifier
      .size(45.dp)
      .graphicsLayer {
        val scale = if (mapWidgetState.scale != 0f) 1f / mapWidgetState.scale else 1f
        // 更改transformOrigin为底边中间
        transformOrigin = TransformOrigin(0.5f, 1f)
        scaleX = scale * anchorItemState.scale
        scaleY = scale * anchorItemState.scale
        translationX = anchorItemState.position.x - size.width / 2
        translationY = anchorItemState.position.y - size.height
      }
  )
}

@Composable
fun AnchorImage(modifier: Modifier = Modifier) {
  Image(
    modifier = modifier,
    painter = painterResource(Res.drawable.map_ic_local),
    contentDescription = null
  )
}