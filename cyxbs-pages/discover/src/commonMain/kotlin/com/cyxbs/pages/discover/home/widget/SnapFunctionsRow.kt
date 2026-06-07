package com.cyxbs.pages.discover.home.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.login.rememberLoginDialogState
import com.cyxbs.components.utils.compose.px
import com.cyxbs.pages.discover.home.DiscoverFunctionItem

/**
 * 功能按钮横向滚动条：
 *
 * 1. **双击置顶 / 取消置顶**：双击某个 item 触发 [onPin]，由外部判断该 id 是否已 pin：
 *    未 pin 则追加到 pinned 列表末尾，已 pin 则从列表中移除（回到规范顺序的对应位置）。
 *    单击触发 [DiscoverFunctionItem.onClick]，并在 [DiscoverFunctionItem.loginPrompt] 非空时
 *    自动弹出登录拦截。
 * 2. 通过 [onProgressChanged] 把横向滚动进度（[0, 1]）回报给指示条；
 *    进度按「已滑像素 / 最大可滑像素」算，最大可滑像素 = 总内容宽 − LazyRow 视口宽，
 *    后者从 `state.layoutInfo` 直接取，无需提前测屏宽。
 *
 * @param state 外部持有的 LazyListState，方便宿主在 pin 后主动 `animateScrollToItem(0)`
 *   滚回起点展示 pinned 区。默认 `rememberLazyListState()`。
 */
@Composable
fun FunctionsRow(
  items: List<DiscoverFunctionItem>,
  modifier: Modifier = Modifier,
  state: LazyListState = rememberLazyListState(),
  itemSize: Dp = 60.dp,
  itemSpacing: Dp = 22.dp, // 让最后一个 item 展示出来半截以提醒用户可以滑动
  pinnedIds: Set<String> = emptySet(),
  onPin: (id: String) -> Unit,
  onProgressChanged: (Float) -> Unit,
) {
  val loginDialogState = rememberLoginDialogState()
  val itemSizePx = itemSize.px
  val spacingPx = itemSpacing.px
  val stepPx = itemSizePx + spacingPx

  // 滚动进度上报：进度 = 当前已滑像素 / 最大可滑像素
  LaunchedEffect(state, items.size) {
    snapshotFlow {
      val firstIdx = state.firstVisibleItemIndex
      val firstOffset = state.firstVisibleItemScrollOffset.toFloat()
      // LazyRow 视口宽（不含 contentPadding 的可滚区间）
      val viewportWidth = (state.layoutInfo.viewportEndOffset - state.layoutInfo.viewportStartOffset).toFloat()
      val contentWidth = items.size * itemSizePx + (items.size - 1).coerceAtLeast(0) * spacingPx
      val totalRange = contentWidth - viewportWidth
      if (totalRange <= 0f) 0f
      else (firstIdx * stepPx + firstOffset) / totalRange
    }.collect { onProgressChanged(it.coerceIn(0f, 1f)) }
  }

  LazyRow(
    state = state,
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(itemSpacing),
  ) {
    items(items, key = { it.id }) { item ->
      FunctionItemCell(
        item = item,
        itemSize = itemSize,
        isPinned = item.id in pinnedIds,
        onClick = {
          val prompt = item.loginPrompt
          if (prompt != null) {
            loginDialogState.doIfLogin(function = prompt) { item.onClick() }
          } else {
            item.onClick()
          }
        },
        onDoubleClick = { onPin(item.id) },
        // 不挂 animateItem 的 placementSpec：
        // 取消 pin 时被 unpin 的 item 会进 movingAwayToEndBound（钉到视口右边缘动画），
        // 腾出的 slot 同时被 movingInFromEndBound 的下一个 item 占位（从右边缘动画进来），
        // 两条动画在视口右侧一两格里反向穿过，看起来像「整排从右往左刷一遍」的闪屏。
        // 新 pin 的视觉流畅感来自 onPin 里的 animateScrollToItem(0)，跟 animateItem 无关，
        // 因此这里直接让 reorder 瞬时落位最干净。
      )
    }
  }
}

@Composable
private fun FunctionItemCell(
  item: DiscoverFunctionItem,
  itemSize: Dp,
  isPinned: Boolean,
  onClick: () -> Unit,
  onDoubleClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val currentOnClick by rememberUpdatedState(onClick)
  val currentOnDoubleClick by rememberUpdatedState(onDoubleClick)
  Column(
    modifier = modifier
      .width(itemSize)
      .pointerInput(Unit) {
        detectTapGestures(
          onTap = { currentOnClick() },
          onDoubleTap = { currentOnDoubleClick() },
        )
      },
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Image(
      painter = item.painter,
      contentDescription = item.title,
      modifier = Modifier.size(43.dp),
    )
    Row(
      modifier = Modifier.padding(top = 6.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // 已 pin 时在文字左侧画一个 11dp 的小图钉
      if (isPinned) {
        Icon(
          imageVector = PinImageVector,
          contentDescription = "已置顶",
          tint = LocalAppColors.current.positive,
          modifier = Modifier.size(11.dp),
        )
        Spacer(modifier = Modifier.width(1.dp))
      }
      Text(
        text = item.title,
        color = LocalAppColors.current.tvLv4,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.alpha(0.6f),
      )
    }
  }
}

