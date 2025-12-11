package com.cyxbs.pages.map.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.utils.compose.clickableSingle
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.map.model.bean.MapInfo
import com.cyxbs.pages.map.util.calculateOriginPosition
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
      if (mapWidgetState.isLock) {
        toast("取消锁定后对地图进行操作")
      } else {
        viewmodel.clickPlace(
          coroutineScope,
          offset,
          mapInfo
        )
      }
    },
    onDoubleClick = { offset ->
      if (mapWidgetState.isLock) {
        toast("取消锁定后对地图进行操作")
      } else {
        // 如果大于6f,则还原初始
        if (mapWidgetState.scale >= 6f) {
          viewmodel.resetMap(coroutineScope)
        } else {
          val mapRatio = mapInfo.mapWidth.toFloat() / mapInfo.mapHeight.toFloat()
          val originOffset = calculateOriginPosition(
            mapWidgetState.center,
            mapWidgetState.offset,
            offset,
            mapWidgetState.scale
          ) - Offset(
            0f,
            (mapWidgetState.container.height.toFloat() - mapWidgetState.container.width.toFloat() / mapRatio) / 2f
          )
          viewmodel.animateMapToPosition(coroutineScope, originOffset)
        }
      }
    },
    anchorContent = {
      if (anchorItemState.visible) {
        AnchorItem(
          mapWidgetState,
          anchorItemState
        ) {
          viewmodel.clickAnchorItem(coroutineScope, anchorItemState)
        }
      }
      anchorItemStateList.forEach { anchorItemState ->
        if (anchorItemState.visible) {
          AnchorItem(
            mapWidgetState,
            anchorItemState
          ) {
            viewmodel.clickAnchorItem(coroutineScope, anchorItemState)
          }
        }
      }
    }
  )
}

@Composable
fun AnchorItem(
  mapWidgetState: MapWidgetState,
  anchorItemState: AnchorItemState,
  onClick: () -> Unit
) {
  // 这里不推荐写成size(45.dp / scale),因为会不断触发measure->layout->draw的重组
  // 应该写在graphicsLayer里面，只会在draw阶段重组
  val anchorScale by remember(mapWidgetState.scale) {
    mutableStateOf(
      if (mapWidgetState.scale != 0f) 1f / mapWidgetState.scale else 1f
    )
  }
  AnchorImage(
    modifier = Modifier
      .offset {
        IntOffset(
          x = anchorItemState.position.x.toInt() - (45.dp.roundToPx() / 2),
          y = anchorItemState.position.y.toInt() - 45.dp.roundToPx()
        )
      }
      .size(45.dp)
      .graphicsLayer {
        // 更改transformOrigin为底边中间
        transformOrigin = TransformOrigin(0.5f, 1f)
        scaleX = anchorScale * anchorItemState.scale
        scaleY = anchorScale * anchorItemState.scale
      }
      // 这里要在scale变换后调用，如果放在前面则会扩大点击区域
      .clickableSingle {
        onClick()
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