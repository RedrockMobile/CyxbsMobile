package com.cyxbs.components.utils.compose

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier

/**
 * .
 *
 * @author 985892345
 * 2024/10/1 11:44
 */

/**
 * 使用 DSL 的方式添加 Modifier
 */
inline fun Modifier.plusDsl(action: Modifier.() -> Unit): Modifier {
  val wrapper = ModifierPlusWrapper(this)
  action.invoke(wrapper)
  return wrapper.thenModifier
}

@Stable
class ModifierPlusWrapper(origin: Modifier) : Modifier by origin {
  var thenModifier: Modifier = origin
  override fun then(other: Modifier): Modifier {
    return thenModifier.then(other).also { thenModifier = it }
  }
}
