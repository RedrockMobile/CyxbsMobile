package com.cyxbs.components.utils.compose

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
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

var lastClickTime = 0L

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