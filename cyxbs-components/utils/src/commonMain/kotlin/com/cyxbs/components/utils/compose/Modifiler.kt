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
class ModifierPlusWrapper(origin: Modifier) : Modifier {
  var thenModifier: Modifier = origin
  override fun <R> foldIn(
    initial: R,
    operation: (R, Modifier.Element) -> R
  ): R {
    return thenModifier.foldIn(initial, operation)
  }

  override fun <R> foldOut(
    initial: R,
    operation: (Modifier.Element, R) -> R
  ): R {
    return thenModifier.foldOut(initial, operation)
  }

  override fun any(predicate: (Modifier.Element) -> Boolean): Boolean {
    return thenModifier.any(predicate)
  }

  override fun all(predicate: (Modifier.Element) -> Boolean): Boolean {
    return thenModifier.all(predicate)
  }

  override fun then(other: Modifier): Modifier {
    return thenModifier.then(other).also { thenModifier = it }
  }
}
