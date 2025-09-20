package com.cyxbs.components.view.calendar.scroll

import androidx.compose.runtime.Stable
import androidx.compose.ui.util.packFloats
import androidx.compose.ui.util.unpackFloat1
import androidx.compose.ui.util.unpackFloat2
import kotlin.jvm.JvmInline

/**
 * .
 *
 * @author 985892345
 * @date 2024/2/27 13:43
 */
@Stable
sealed interface VerticalScrollState {
  val offset: Float
  val fraction: Float
  data object Collapsed : VerticalScrollState {
    override val offset: Float
      get() = 0F
    override val fraction: Float
      get() = 0F
  }

  @JvmInline
  value class Scrolling private constructor(private val value: Long) : VerticalScrollState {
    constructor(offset: Float, fraction: Float) : this(packFloats(offset, fraction))
    override val offset: Float
      get() = unpackFloat1(value)
    override val fraction: Float
      get() = unpackFloat2(value)
  }

  @JvmInline
  value class Expanded(override val offset: Float) : VerticalScrollState {
    override val fraction: Float
      get() = 1F
  }
}

@Stable
sealed interface HorizontalScrollState {
  val offset: Float
  data object Idle : HorizontalScrollState {
    override val offset: Float
      get() = 0F
  }

  @JvmInline
  value class Scrolling(override val offset: Float) : HorizontalScrollState
}