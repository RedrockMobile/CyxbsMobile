package com.cyxbs.pages.discover.home.widget

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.pages.discover.home.bean.JwNewsItemBean
import kotlinx.coroutines.delay

/**
 * 还原原 ViewFlipper：每 [intervalMillis] 毫秒切换一条新闻，从下往上滑入/滑出，
 * 滑入有 250ms 的 startOffset（对齐原 discover_text_in_anim.xml）。
 *
 * 点击当前条目调用 [onItemClick] 并传入对应 id。
 */
@Composable
fun JwNewsFlipper(
  items: List<JwNewsItemBean>,
  modifier: Modifier = Modifier,
  textColor: Color = Color(0xFF15315B),
  intervalMillis: Long = 6000L,
  onItemClick: (id: String) -> Unit,
) {
  if (items.isEmpty()) {
    Box(modifier = modifier)
    return
  }
  var index by remember(items) { mutableIntStateOf(0) }
  LaunchedEffect(items, intervalMillis) {
    if (items.size <= 1) return@LaunchedEffect
    while (true) {
      delay(intervalMillis)
      index = (index + 1) % items.size
    }
  }
  val current = items[index]
  AnimatedContent(
    targetState = current,
    modifier = modifier,
    transitionSpec = textTransitionSpec(),
    contentKey = { it.id },
    label = "JwNewsFlipper",
  ) { item ->
    Box(
      modifier = Modifier.clickableNoIndicator { onItemClick(item.id) },
      contentAlignment = Alignment.CenterStart,
    ) {
      Text(
        text = item.title,
        color = textColor,
        fontSize = 15.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

private fun textTransitionSpec(): AnimatedContentTransitionScope<JwNewsItemBean>.() -> ContentTransform = {
  // 对齐原 discover_text_in_anim.xml: duration 500ms, startOffset 250ms, 从底部滑入
  // 对齐原 discover_text_out_anim.xml: duration 500ms, 向顶部滑出
  val enter = slideInVertically(
    animationSpec = tween(durationMillis = 500, delayMillis = 250, easing = LinearEasing),
    initialOffsetY = { it },
  )
  val exit = slideOutVertically(
    animationSpec = tween(durationMillis = 500, easing = LinearEasing),
    targetOffsetY = { -it },
  )
  enter togetherWith exit
}
