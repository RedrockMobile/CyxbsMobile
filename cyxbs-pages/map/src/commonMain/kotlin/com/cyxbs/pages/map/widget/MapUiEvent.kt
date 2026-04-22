package com.cyxbs.pages.map.widget

/**
 * @Desc : 地图的动画事件
 * @Author : zzx
 * @Date : 2026/3/22 16:05
 */

/**
 * 一开始是直接在viewmodel里面传入coroutineScope，但页面与viewmodel之间应该是事件的通信
 * 不能把涉及自身生命周期的传入viewmodel
 * 故这里进行一定修改，通过事件流的形式传递，然后在LaunchedEffect里面收集并开启动画
 */
sealed class MapUiEvent {

  data class SearchToPlace(
    val placeId: String,
    val placeCenterX: Float,
    val placeCenterY: Float
  ) : MapUiEvent()

  data class FocusOnPlace(
    val placeId: String,
    val placeCenterX: Float,
    val placeCenterY: Float
  ) : MapUiEvent()

  data class ShowAnchorList(
    val placeIds: List<String>
  ) : MapUiEvent()

  object ShowCollectAnchors : MapUiEvent()

  object CloseAnchorList : MapUiEvent()

  data class ClickMapPlace(
    val placeId: String?,
    val focusOffsetX: Float?,
    val focusOffsetY: Float?
  ) : MapUiEvent()

  data class OpenPlaceDetail(
    val placeId: String,
    val anchorPositionX: Float,
    val anchorPositionY: Float
  ) : MapUiEvent()

  data class AnimateMapToPosition(
    val offsetX: Float,
    val offsetY: Float
  ) : MapUiEvent()

  object ResetMap : MapUiEvent()

  object CollapseSearchSheet : MapUiEvent()

  object CollapseBottomSheet : MapUiEvent()

  object ExpandBottomSheet : MapUiEvent()

  object HideBottomSheet : MapUiEvent()
}
