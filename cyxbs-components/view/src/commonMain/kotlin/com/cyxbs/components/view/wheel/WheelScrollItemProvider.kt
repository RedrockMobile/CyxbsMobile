package com.cyxbs.components.view.wheel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.layout.LazyLayoutItemProvider
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import com.cyxbs.components.utils.compose.rememberDerivedStateOfStructure
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * .
 *
 * @author 985892345
 * 2024/4/29 19:13
 */
class WheelScrollItemProvider(
  val items: List<String>,
  val draggedLine: () -> Float,
  val textStyle: State<TextStyle>,
  val selectedTextSizeRatio: State<Float>,
) : LazyLayoutItemProvider {

  override val itemCount: Int
    get() = items.size

  @Composable
  override fun Item(index: Int, key: Any) {
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center,
    ) {
      BasicText(
        text = items[index],
        style = rememberDerivedStateOfStructure(index) {
          val unselected = textStyle.value
          val draggedLineValue = draggedLine.invoke()
          if (draggedLineValue.roundToInt() != index) {
            unselected.copy(
              color = unselected.color.copy(
                alpha = unselected.color.alpha * 0.5F
              )
            )
          } else {
            val ratio = abs(draggedLineValue - index) * 2
            unselected.copy(
              fontSize = selectedTextSizeRatio.value.let {
                if (it == 1F) unselected.fontSize else {
                  TextUnit(
                    unselected.fontSize.value + (unselected.fontSize * (it - 1) * (1 - ratio)).value,
                    unselected.fontSize.type
                  )
                }
              },
              color = unselected.color.copy(
                alpha = unselected.color.alpha - unselected.color.alpha * 0.5F * ratio
              )
            )
          }
        }.value
      )
    }
  }

  override fun getIndex(key: Any): Int {
    return key as Int
  }

  override fun getKey(index: Int): Any {
    return index
  }
}
