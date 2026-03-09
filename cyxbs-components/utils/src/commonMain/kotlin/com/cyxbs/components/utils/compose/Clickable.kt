package com.cyxbs.components.utils.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.util.fastFirstOrNull
import kotlin.time.Clock

/**
 * .
 *
 * @author 985892345
 * @date 2024/2/19 19:38
 */

/**
 * 点击不带虚影的 clickable
 */
fun Modifier.clickableNoIndicator(
  enabled: Boolean = true,
  onClickLabel: String? = null,
  role: Role? = null,
  onClick: () -> Unit
) = clickable(
  interactionSource = null,
  indication = null,
  enabled = enabled,
  onClickLabel = onClickLabel,
  role = role,
  onClick = onClick
)

private var lastClickTime = 0L

/**
 * @param interval 毫秒为单位，点击间隔小于这个值监听事件无法生效
 * @param onClick 具体的点击事件
 */
fun Modifier.clickableSingle(
  enabled: Boolean = true,
  onClickLabel: String? = null,
  role: Role? = null,
  interval: Long = 500L,
  onClick: () -> Unit,
) = clickable(
    enabled = enabled,
    onClickLabel = onClickLabel,
    role = role,
    interactionSource = null
) {
    val nowClickTime = Clock.System.now().toEpochMilliseconds()
    if(nowClickTime - lastClickTime >= interval) {
      lastClickTime = nowClickTime
      onClick()
    }
}

/**
 * 在事件分发的初始阶段就触发点击事件
 * 用于子组件会默认消耗事件但是却不执行啥操作的场景，比如 TextField
 */
fun Modifier.clickableInPassInitial(
  enabled: () -> Boolean = { true },
  onClick: () -> Unit,
): Modifier {
  return pointerInput(Unit) {
    awaitEachGesture {
      val firstDown = awaitFirstDown(pass = PointerEventPass.Initial)
      while (enabled.invoke()) {
        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
        val pointer = event.changes.fastFirstOrNull { it.id == firstDown.id }
        if (pointer == null || pointer.isConsumed) break
        if (pointer.uptimeMillis - firstDown.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) break
        if (pointer.positionChangeIgnoreConsumed().x > viewConfiguration.touchSlop) break
        if (pointer.positionChangeIgnoreConsumed().y > viewConfiguration.touchSlop) break
        if (pointer.changedToUp()) {
          // 触发点击事件
          pointer.consume()
          onClick.invoke()
          break
        }
      }
    }
  }
}