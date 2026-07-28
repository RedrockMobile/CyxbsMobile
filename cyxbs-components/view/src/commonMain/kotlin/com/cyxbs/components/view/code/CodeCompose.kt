package com.cyxbs.components.view.code

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastMapIndexed
import androidx.compose.ui.util.fastSumBy
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * .
 *
 * @author 985892345
 * 2024/3/24 19:21
 */

@Composable
fun CodeCompose(
  text: MutableState<String>,
  editable: Boolean = true,
  modifier: Modifier = Modifier,
  hint: String = "",
  minLine: Int = 1,
  fontFamily: FontFamily = FontFamily.Monospace,
  style: MutableState<TextStyle> = remember {
    mutableStateOf(
      TextStyle(
        fontSize = 12.sp,
        fontFamily = fontFamily,
      )
    )
  },
) {
  val offsetX = remember { mutableFloatStateOf(0F) }
  val offsetY = remember { mutableFloatStateOf(0F) }
  val diffWidth = remember { mutableFloatStateOf(0F) }
  val diffHeight = remember { mutableFloatStateOf(0F) }
  Layout(
    modifier = modifier.pointerInput(Unit) {
      detectPanZoomGestures { pan, zoom ->
        offsetX.floatValue = (offsetX.floatValue + pan.x).coerceIn(-diffWidth.floatValue, 0F)
        offsetY.floatValue = (offsetY.floatValue + pan.y).coerceIn(-diffHeight.floatValue, 0F)
        style.value = style.value.copy(
          fontSize = style.value.fontSize.times(zoom)
        )
      }
    }.mouseWheelAnimScroll(Unit) {
      offsetX.floatValue = (offsetX.floatValue + it.x).coerceIn(-diffWidth.floatValue, 0F)
      offsetY.floatValue = (offsetY.floatValue + it.y).coerceIn(-diffHeight.floatValue, 0F)
    }.mouseWheelRawScroll(Unit, consume = { event ->
      event.changes.fastAll { !it.isConsumed } && event.keyboardModifiers.isMetaPressed
    }) {
      // 暂不支持触摸板多指：https://github.com/JetBrains/compose-multiplatform/issues/1953
      // 桌面端底层触摸事件使用的 awt，不支持触摸板多指
      val old = style.value.fontSize
      style.value = style.value.copy(
        fontSize = TextUnit(old.value + it.y.sign, old.type)
      )
    },
    content = {
      val paddingTop = 6.dp
      val textLinesBottomState = remember { mutableStateOf(emptyList<Float>()) }
      val hintLinesBottomState = remember { mutableStateOf(emptyList<Float>()) }
      LineNumberCompose(
        modifier = Modifier.graphicsLayer {
          translationY = offsetY.floatValue
        },
        minLine = minLine,
        paddingTop = paddingTop,
        style = style,
        linesBottom = remember {
          derivedStateOf {
            if (textLinesBottomState.value.size >= hintLinesBottomState.value.size) {
              textLinesBottomState.value
            } else {
              List(hintLinesBottomState.value.size) {
                if (it < textLinesBottomState.value.size) {
                  textLinesBottomState.value[it]
                } else {
                  hintLinesBottomState.value[it]
                }
              }
            }
          }
        },
      )
      TextCompose(
        modifier = Modifier.graphicsLayer {
          translationX = offsetX.floatValue
          translationY = offsetY.floatValue
        },
        text = text,
        editable = editable,
        hint = hint,
        paddingTop = paddingTop,
        style = style,
        textLinesBottom = textLinesBottomState,
        hintLinesBottom = hintLinesBottomState,
      )
    },
    measurePolicy = remember {
      { measurables: List<Measurable>, constraints: Constraints ->
        val linePlaceable = measurables[0].measure(Constraints())
        val textPlaceable = measurables[1].measure(Constraints(
          minWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth,
          minHeight = linePlaceable.height,
        ))
        val width =
          if (constraints.hasBoundedWidth) constraints.maxWidth
          else maxOf(linePlaceable.width + textPlaceable.width + 50, constraints.minWidth)
        val height = linePlaceable.height.coerceIn(constraints.minHeight, constraints.maxHeight)
        diffWidth.floatValue = (linePlaceable.width + textPlaceable.width + 50 - width)
          .coerceAtLeast(0).toFloat()
        diffHeight.floatValue = (linePlaceable.height - height)
          .coerceAtLeast(0).toFloat()
        layout(width, height) {
          textPlaceable.placeRelative(linePlaceable.width, 0)
          linePlaceable.placeRelative(0, 0)
        }
      }
    }
  )
}

@Composable
private fun LineNumberCompose(
  modifier: Modifier,
  minLine: Int,
  paddingTop: Dp,
  style: MutableState<TextStyle>,
  linesBottom: State<List<Float>>,
) {
  val minLineState = rememberUpdatedState(minLine)
  val count by remember { derivedStateOf { linesBottom.value.size.coerceAtLeast(minLineState.value) } }
  Layout(
    modifier = modifier.background(Color(0xFFE6E6E6))
      .padding(top = paddingTop, start = 8.dp, bottom = 8.dp, end = 6.dp),
    content = {
      val color = remember { { Color(0xFF595959) } }
      repeat(count) {
        BasicText(
          text = "${it + 1}",
          color = color,
          style = style.value,
        )
      }
    },
    measurePolicy = remember {
      { measurables, constraints ->
        var maxWidth = 0
        var itemsHeight = 0
        val placeables = measurables.fastMapIndexed { _, measurable ->
          measurable.measure(Constraints()).also {
            maxWidth = maxOf(maxWidth, it.width)
            itemsHeight += it.height
          }
        }
        val layoutHeight = maxOf(itemsHeight, linesBottom.value.lastOrNull()?.roundToInt() ?: 0)
          .coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(maxWidth, layoutHeight) {
          var top = 0F
          placeables.fastForEachIndexed { index, placeable ->
            val height = if (index < linesBottom.value.size) {
              linesBottom.value[index] - top
            } else placeable.height.toFloat()
            placeable.placeRelative(
              x = maxWidth - placeable.width,
              y = (top + (height - placeable.height) / 2).roundToInt()
            )
            top += height
          }
        }
      }
    }
  )
}

@Composable
private fun TextCompose(
  modifier: Modifier,
  text: MutableState<String>,
  editable: Boolean,
  hint: String,
  paddingTop: Dp,
  style: MutableState<TextStyle>,
  textLinesBottom: MutableState<List<Float>>,
  hintLinesBottom: MutableState<List<Float>>,
) {
  val hintState by rememberUpdatedState(hint)
  Box(
    modifier = modifier.background(Color.Transparent)
      .padding(top = paddingTop, start = 6.dp),
    propagateMinConstraints = true,
  ) {
    if (editable) {
      val interactionSource = remember { MutableInteractionSource() }
      BasicTextField(
        value = text.value,
        onValueChange = { text.value = it },
        interactionSource = interactionSource,
        textStyle = style.value,
        decorationBox = { compose ->
          BasicText(
            modifier = Modifier.layout { measurable, constraints ->
              val placeable = measurable.measure(constraints)
              layout(
                if (text.value.isEmpty()) placeable.width else 0, // 有输入文本时 hint 宽度设置为 0
                placeable.height
              ) {
                placeable.placeRelative(0, 0)
              }
            },
            text = hintState,
            color = { if (text.value.isEmpty()) Color.Gray else Color.Transparent },
            style = style.value,
            onTextLayout = { result ->
              hintLinesBottom.value = List(result.lineCount) { result.getLineBottom(it) }
            }
          )
          compose.invoke()
        },
        onTextLayout = { result ->
          textLinesBottom.value = List(result.lineCount) { result.getLineBottom(it) }
        }
      )
    } else {
      SelectionContainer {
        BasicText(
          text = text.value.ifEmpty { hint },
          color = { if (text.value.isEmpty()) Color.Gray else style.value.color },
          style = style.value,
          onTextLayout = { result ->
            if (text.value.isNotEmpty()) {
              textLinesBottom.value = List(result.lineCount) { result.getLineBottom(it) }
            } else {
              hintLinesBottom.value = List(result.lineCount) { result.getLineBottom(it) }
            }
          }
        )
      }
    }
  }
}

/**
 * 监听移动和缩放
 *
 * 代码来自官方的 [detectTransformGestures]，去掉了质心和旋转，
 * 同时将事件优先级更改为 Initial 以拦截子组件事件
 */
private suspend fun PointerInputScope.detectPanZoomGestures(
  onGesture: (pan: Offset, zoom: Float) -> Unit
) {
  awaitEachGesture {
    val first = awaitFirstBeforeLongPress() ?: return@awaitEachGesture
    var event = currentEvent
    while (true) {
      val pointer = event.changes.firstOrNull { it.id == first.id } ?: break
      if (!pointer.pressed) break
      val zoomChange = event.calculateZoom()
      val panChange = event.calculatePan()
      onGesture(panChange, zoomChange)
      event.changes.fastForEach {
        it.consume()
      }
      event = awaitPointerEvent(PointerEventPass.Initial)
    }
  }
}

/**
 * 挂起直到满足条件时才恢复
 * - 第一根手指在长按触发前移动距离大于 touchSlop
 * - 存在多根手指处于按下状态
 * - Main pass 中事件未被消耗时
 */
private suspend fun AwaitPointerEventScope.awaitFirstBeforeLongPress(): PointerInputChange? {
  val down = awaitFirstDownNotMouse()
  return try {
    val timeout = viewConfiguration.longPressTimeoutMillis
    withTimeout(timeout) {
      while (true) {
        val initialEvent = awaitPointerEvent(PointerEventPass.Initial)
        val initialFirst = initialEvent.changes.firstOrNull { it.id == down.id } ?: break
        if (!initialFirst.pressed) break
        if ((initialFirst.position - down.position).getDistance() > viewConfiguration.touchSlop) {
          // 如果第一根手指移动距离大于 touchSlop 则返回 first
          return@withTimeout initialFirst
        }
        if (initialEvent.changes.fastSumBy { if (it.pressed) 1 else 0 } >= 2) {
          // 如果有多个手指按下则退出则返回 first
          return@withTimeout initialFirst
        }
        val mainEvent = awaitPointerEvent(PointerEventPass.Main)
        val mainFirst = mainEvent.changes.first { it.id == down.id }
        if (!mainFirst.isConsumed) {
          // 如果 Main pass 中事件未被消耗则返回 first
          return@withTimeout mainFirst
        }
      }
      null
    }
  } catch (_: PointerEventTimeoutCancellationException) {
    val event = awaitPointerEvent(PointerEventPass.Main)
    val first = event.changes.firstOrNull { it.id == down.id } ?: return null
    if (first.pressed && !first.isConsumed) {
      // 如果 Main pass 中事件未被消耗则返回 first
      return first
    }
    null
  }
}

// 挂起直到第一个指针非鼠标按下时才恢复
private suspend fun AwaitPointerEventScope.awaitFirstDownNotMouse(): PointerInputChange {
  while (true) {
    val event = awaitPointerEvent(PointerEventPass.Initial)
    val first = event.changes.first()
    if (event.type == PointerEventType.Press && first.type != PointerType.Mouse && !first.isConsumed) {
      return first
    }
  }
}