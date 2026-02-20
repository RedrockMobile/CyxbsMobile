package com.cyxbs.components.view.wheel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.layout.LazyLayout
import androidx.compose.foundation.lazy.layout.LazyLayoutItemProvider
import androidx.compose.material.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/**
 * .
 *
 * @author 985892345
 * 2024/4/29 19:03
 */
@Composable
fun WheelSelectCompose(
  selectedLine: Animatable<Float, AnimationVector1D>, // 使用 Animatable 以支持外界操控滚动
  options: ImmutableList<String>,
  modifier: Modifier = Modifier,
  textStyle: TextStyle = remember {
    TextStyle(
      fontSize = 14.sp,
      textAlign = TextAlign.Center,
      color = Color.Black,
    )
  },
  selectedTextSizeRatio: Float = 1F,
  onDrag: (() -> Unit)? = null,
  onDragStart: (() -> Unit)? = null,
  onDragStopped: (() -> Unit)? = null,
) {
  val textStyleState = rememberUpdatedState(textStyle)
  val selectedTextSizeRatioState = rememberUpdatedState(selectedTextSizeRatio)
  val itemProvider = remember(selectedLine, options) {
    WheelScrollItemProvider(
      items = options,
      textStyle = textStyleState,
      draggedLine = { selectedLine.value },
      selectedTextSizeRatio = selectedTextSizeRatioState,
    )
  }
  val parentHeight = remember { mutableIntStateOf(0) }
  val measurePolicy = remember(selectedLine, options) {
    WheelScrollMeasurePolicy(
      items = options,
      draggedLine = { selectedLine.value },
      parentHeight = parentHeight,
    )
  }
  var draggedOffset by remember { mutableFloatStateOf(0F) }
  val channel = remember {
    Channel<Float>(
      capacity = 1,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
  }
  remember(selectedLine, options) {
    selectedLine.updateBounds(
      lowerBound = maxOf(0F, selectedLine.lowerBound ?: 0F),
      upperBound = minOf(options.size - 1F, selectedLine.upperBound ?: (options.size - 1F)),
    )
  }
  var isDragging by remember { mutableStateOf(false) }
  LazyLayout(
    itemProvider = remember<() -> LazyLayoutItemProvider> { { itemProvider } },
    measurePolicy = measurePolicy,
    modifier = modifier.draggable(
      orientation = Orientation.Vertical,
      state = rememberDraggableState {
        val itemHeight = parentHeight.intValue / 3F
        draggedOffset = (draggedOffset - it)
          .coerceIn(0F, itemHeight * (options.size - 1).coerceAtLeast(0))
          .coerceIn(
            selectedLine.lowerBound?.times(itemHeight),
            selectedLine.upperBound?.times(itemHeight)
          )
        val value = (draggedOffset / itemHeight).coerceIn(0F, options.size - 1F)
        channel.trySend(value)
        onDrag?.invoke()
      },
      onDragStarted = {
        isDragging = true
        try {
          selectedLine.stop()
        } catch (e: CancellationException) { }
        draggedOffset = selectedLine.value * (parentHeight.intValue / 3F)
        onDragStart?.invoke()
      },
      onDragStopped = { velocity ->
        isDragging = false
        try {
          val decayAnimationSpec = exponentialDecay<Float>(
            frictionMultiplier = 100F
          )
          val targetValue = decayAnimationSpec.calculateTargetValue(
            initialValue = selectedLine.value,
            initialVelocity = -velocity,
          ).coerceIn(0F, options.size - 1F)
            .coerceIn(selectedLine.lowerBound, selectedLine.upperBound)
          selectedLine.animateTo(
            targetValue = targetValue.roundToInt().toFloat(),
            animationSpec = tween(
              durationMillis = 300,
            ),
            initialVelocity = -velocity,
          )
        } catch (e: CancellationException) {
        } finally {
          onDragStopped?.invoke()
        }
      },
    ).clipToBounds(),
  )
  LaunchedEffect(selectedLine) {
    while (isActive) {
      val value = channel.receive()
      if (isDragging) {
        try {
          selectedLine.snapTo(value)
        } catch (e: CancellationException) {
        }
      }
    }
  }
}


@Composable
fun WheelSelectBackground(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Card(modifier) {
    content()
    Column(modifier = Modifier.fillMaxSize()) {
      Spacer(
        modifier = Modifier.weight(1F).fillMaxWidth().background(
          brush = Brush.verticalGradient(
            colors = listOf(
              Color.Black.copy(alpha = 0.05F),
              Color.Transparent,
            )
          )
        )
      )
      Spacer(modifier = Modifier.weight(1F))
      Spacer(
        modifier = Modifier.weight(1F).fillMaxWidth().background(
          brush = Brush.verticalGradient(
            colors = listOf(
              Color.Transparent,
              Color.Black.copy(alpha = 0.05F),
            )
          )
        )
      )
    }
  }
}