package com.cyxbs.pages.course.view.item

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.zIndex
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.getValue
import com.cyxbs.components.utils.compose.rememberBooleanWrapper
import com.cyxbs.components.utils.compose.rememberDerivedStateOfStructure
import com.cyxbs.components.utils.compose.rememberWrapper
import com.cyxbs.components.utils.compose.setValue
import com.cyxbs.components.utils.compose.sharePointerInput
import com.cyxbs.pages.course.view.overlay.CoveredRange
import com.cyxbs.pages.course.view.overlay.IOverlayController
import com.cyxbs.pages.course.view.overlay.LocalOverlayController
import com.cyxbs.pages.course.view.overlay.OverlayData
import com.cyxbs.pages.course.view.overlay.OverlayManager
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek

/**
 * .
 *
 * @author 985892345
 * @date 2025/2/14
 */
@Stable
interface CourseItem {

  // 用于单天课程 item 的遍历定位
  val key: String

  val page: Int
  val dayOfWeek: DayOfWeek
  val beginTime: MinuteTime
  val finalTime: MinuteTime // 如果 finalTime < beginTime 则表示跨了一天

  /**
   * 绘制 item 内容，使用 [CourseDefaultItemContent]
   */
  @Composable
  fun CourseItemContent(
    modifier: Modifier,
    overlap: OverlayData,
    timeline: CourseTimeline,
  )
}

@Composable
fun CourseDefaultItemContent(
  modifier: Modifier = Modifier,
  lastModifier: Modifier = Modifier,
  timeline: CourseTimeline,
  overlap: OverlayData, // 注：overlap 是会发生改变的，但其中 overlap.item 不会
  topText: String,
  bottomText: String,
  textColor: Color,
  backgroundColor: Color,
  onClick: ((CoveredRange) -> Unit)? = null,
) {
  Box(
    modifier = modifier
      .then(timeline.createLayoutModifier(overlap.item.beginTime, overlap.item.finalTime))
      .longPressMove( // 长按移动 item
        item = overlap.item,
        isCompleteCover = overlap.showRangeList.isEmpty(),
      )
      .pressScale(overlap.item) // 点击后的 Q 弹动画
      .courseItemBackground(backgroundColor) // 通用背景
      .then(lastModifier)
  ) {
    val showRange = overlap.showRangeList.ifEmpty {
      /**
       * 正常情况下 showRangeList 为空是会被拦截的（详细看 [OverlayManager.getSingleDayOverlapData]）
       * 如果能走到这种情况说明单独设置了 [IOverlayController.allowNoShowRange]
       * 此时我们默认修改为添加整个显示区域即可
       * 但是对于 [CoveredRange.coveredItems] 我们无法反向计算，只能忽略
       */
      listOf(CoveredRange(overlap.item.beginTime, overlap.item.finalTime))
    }
    showRange.fastForEach { range ->
      AnimatedContent(range) {
        CourseItemTopBottomText(
          modifier = Modifier.then(
            timeline.createLayoutModifier(
              it.begin, it.final,
              overlap.item.beginTime to overlap.item.finalTime,
            )
          ).drawWithContent {
            drawContent()
            if (it.coveredItems.size > 0) {
              drawRoundRect(
                color = textColor,
                topLeft = Offset(x = size.width - 12.dp.toPx(), y = 4.dp.toPx()),
                size = Size(width = 6.dp.toPx(), height = 2.dp.toPx()),
                cornerRadius = CornerRadius(1.dp.toPx()),
              )
            }
          }.clickableNoIndicator {
            onClick?.invoke(it)
          },
          topText = topText,
          bottomText = bottomText,
          textColor = textColor,
        )
      }
    }
  }
}

@Stable
fun Modifier.courseItemBackground(backgroundColor: Color): Modifier = composed {
  padding(1.dp)
    .background(LocalAppColors.current.topBg, RoundedCornerShape(8.dp))
    .padding(0.6.dp)
    .shadow(elevation = 0.5.dp, shape = RoundedCornerShape(8.dp))
    .background(LocalAppColors.current.topBg) // 遮挡 shadow 阴影
    .background(backgroundColor)
}

/**
 * 添加统一样式的顶部和底部文字
 */
@Composable
fun CourseItemTopBottomText(
  modifier: Modifier = Modifier,
  topText: String,
  bottomText: String,
  textColor: Color,
) {
  Layout(
    modifier = modifier.fillMaxSize(),
    content = {
      Text(
        text = topText,
        textAlign = TextAlign.Center,
        color = textColor,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        fontSize = 11.sp,
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 6.dp)
      )
      Text(
        text = bottomText,
        textAlign = TextAlign.Center,
        color = textColor,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        fontSize = 11.sp,
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
      )
    },
    measurePolicy = { measurables, constraints ->
      val topPlaceable = measurables[0].measure(
        constraints.copy(
          minHeight = 0
        )
      )
      val space = 2.dp.roundToPx()
      val bottomPlaceable = measurables[1].measure(
        constraints.copy(
          minHeight = 0,
          maxHeight = (constraints.maxHeight - topPlaceable.height - space).coerceAtLeast(0),
        )
      )
      layout(constraints.maxWidth, constraints.maxHeight) {
        topPlaceable.place(0, 0)
        if (topPlaceable.height + bottomPlaceable.height + space < constraints.maxHeight) {
          // 底部文本只有在能放下时才会显示
          bottomPlaceable.place(0, constraints.maxHeight - bottomPlaceable.height)
        }
      }
    }
  )
}

// 点击后的 Q 弹动画实现
@Stable
@Composable
private fun Modifier.pressScale(item: CourseItem): Modifier {
  val pointerOffset = rememberWrapper<Offset?>(null)
  val scale = remember { Animatable(initialValue = 1F) }
  val coroutineScope = rememberCoroutineScope()
  val localOverlayController = LocalOverlayController.current
  return pointerInput(item) {
    awaitEachGesture {
      val down = awaitFirstDown(
        requireUnconsumed = false,
        pass = PointerEventPass.Initial,
      )
      pointerOffset.value = down.position
      // 点击后会缩小，这里让被覆盖的 item 显示出来
      val coverBottomLock = localOverlayController.ignoreCoverBottom(item)
      coroutineScope.launch { scale.animateTo(0.8F) }
      val endBlock: () -> Unit = {
        pointerOffset.value = null
        if (coroutineScope.isActive) coroutineScope.launch {
          try {
            scale.animateTo(1F)
          } finally {
            coverBottomLock.unlock()
          }
        } else coverBottomLock.unlock()
      }
      while (true) {
        val pointer = try {
          awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
        } catch (e: Exception) {
          endBlock.invoke() // 可能会存在 CancellationException
          throw e
        }
        if (
          pointer == null
          || pointer.isConsumed // 被消耗
          || pointer.changedToUp() // 抬起
          || pointer.isOutOfBounds(size, Size.Zero) // 越界
          || pointer.positionChange().getDistance() > viewConfiguration.touchSlop // 移动距离过大
        ) {
          endBlock.invoke()
          break
        }
      }
    }
  }.graphicsLayer {
    scaleX = scale.value
    scaleY = scale.value
    val pointer = pointerOffset.value
    if (pointer != null) {
      val centerX = size.width / 2F
      val centerY = size.height / 2F
      rotationX = -(pointer.y - centerY) / centerY * ((-0.0023F * size.height + 1.7F) * 16) // 上下翻转
      rotationY = (pointer.x - centerX) / centerX * ((-0.0023F * size.width + 1.7F) * 10) // 左右翻转
    }
  }
}


sealed interface LongPressMoveState {
  data object Idle : LongPressMoveState
  data class Touching(val offset: Offset) : LongPressMoveState
  data class Animating(val beginOffset: Offset, val finalOffset: Offset) : LongPressMoveState
}

// 长按移动 item
@Stable
@Composable
private fun Modifier.longPressMove(
  state: MutableState<LongPressMoveState> = remember { mutableStateOf(LongPressMoveState.Idle) },
  item: CourseItem,
  isCompleteCover: Boolean, // 是否被完全覆盖
): Modifier {
  if (item !is IMovableItem) return this
  val coroutineScope = rememberCoroutineScope()
  val localOverlayController = LocalOverlayController.current
  var coverBottomLock: IOverlayController.Lock? = null
  var noShowRangeLock: IOverlayController.Lock? = null
  var layoutCoordinate by rememberWrapper<LayoutCoordinates?>(null)
  val alphaState = remember { mutableFloatStateOf(1F) }
  var screenPosition by rememberWrapper(Offset.Zero)
  val transition = remember { mutableStateOf(Offset.Zero) }
  val isMoving by rememberDerivedStateOfStructure { state.value !is LongPressMoveState.Idle }
  val isCompleteCoverWrapper = rememberBooleanWrapper(isCompleteCover)
  // 移动结束时调用
  val moveToNewLocationBlock: (Offset) -> Unit by rememberWrapper {
    state.value = LongPressMoveState.Animating(transition.value, it)
    if (coroutineScope.isActive) coroutineScope.launch {
      try {
        item.moveToNewLocation(
          alphaState = alphaState,
          offsetState = transition,
          newOffset = it,
          isCompleteCover = isCompleteCoverWrapper.value, // 这里需要使用包裹类以获取重组后的最新值
        )
      } finally {
        coverBottomLock?.unlock()
        noShowRangeLock?.unlock()
        alphaState.value = 1F
        state.value = LongPressMoveState.Idle
      }
    } else {
      coverBottomLock?.unlock()
      noShowRangeLock?.unlock()
      state.value = LongPressMoveState.Idle
    }
  }
  // 需要使用 sharePointerInput 允许触摸事件分发给兄弟节点
  return sharePointerInput(true).pointerInput(item) {
    // pointerInput 需要在 graphicsLayer 之后，否则下面的 item 无法处理事件
    awaitEachGesture {
      val down = awaitFirstDown(
        requireUnconsumed = false,
        // 使用 Initial 会导致 awaitLongPressOrCancellation 中的 Main 拿到的 pointer 是已经被 consume 的
        // 所以这里也设置成 Main，让 awaitLongPressOrCancellation 里面拿到的是下一次 Main 的
        pass = PointerEventPass.Main,
      )
      val longPressPointer = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
      while (true) {
        val pointer = try {
          awaitPointerEvent(pass = PointerEventPass.Initial).changes.fastFirstOrNull { it.id == longPressPointer.id }
        } catch (e: Exception) {
          moveToNewLocationBlock.invoke(Offset.Zero) // 可能会存在 CancellationException
          throw e
        }
        if (pointer == null || pointer.isConsumed) {
          moveToNewLocationBlock.invoke(Offset.Zero)
          break
        }
        if (pointer.changedToUp()) {
          moveToNewLocationBlock.invoke(item.getMoveToNewLocation())
          break
        }
        pointer.consume()
        if (state.value == LongPressMoveState.Idle) {
          // 触发移动的起点
          state.value = LongPressMoveState.Touching(Offset.Zero)
          coverBottomLock = localOverlayController.ignoreCoverBottom(item)
          noShowRangeLock = localOverlayController.allowNoShowRange(item)
        }
        // 记录相对于屏幕的位置
        screenPosition =
          layoutCoordinate!!.localToScreen(pointer.position) - longPressPointer.position
        // item 当前的偏移量
        val offset = pointer.position - longPressPointer.position
        transition.value = offset
        state.value = LongPressMoveState.Touching(offset)
      }
    }
  }.onGloballyPositioned {
    layoutCoordinate = it
    if (state.value is LongPressMoveState.Touching) {
      // 当相对屏幕的位置发生改变时，则需要重新计算 transition（比如时间轴展开、滚轴上下滑动）
      val offset = it.screenToLocal(screenPosition)
      transition.value = offset
      state.value = LongPressMoveState.Touching(offset)
    }
  }.zIndex(
    if (isMoving) 1F else 0F
  ).graphicsLayer {
    translationX = transition.value.x
    translationY = transition.value.y
    alpha = alphaState.floatValue // 如果当前 item 被完全覆盖时，就会在动画期间从 1 -> 0
  }
}


