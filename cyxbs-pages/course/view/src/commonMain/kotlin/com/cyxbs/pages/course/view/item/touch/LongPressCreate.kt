package com.cyxbs.pages.course.view.item.touch

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.utils.compose.Wrapper
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.compose.rememberWrapper
import com.cyxbs.pages.course.view.item.courseItemBackground
import com.cyxbs.pages.course.view.item.touch.LongPressCreate.LongPressItem
import com.cyxbs.pages.course.view.page.CoursePageDecoration
import com.cyxbs.pages.course.view.timeline.LocalCourseScroll
import com.cyxbs.pages.course.view.timeline.LocalCourseScrollContext
import com.cyxbs.pages.course.view.timeline.data.MutableTimelineData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 长按移动创建 item
 *
 * @author 985892345
 * @date 2025/5/17
 */
@Stable
class LongPressCreate : CoursePageDecoration {

  @Composable
  override fun InnerCoursePage(content: @Composable () -> Unit) {
    LongPressCreateCoursePageWrapper(content)
  }

  @Stable
  class LongPressItem(
    val initTime: MinuteTime,
    val initPosition: Offset,
    val nowPosition: MutableState<Offset>,
    val nowScreenY: MutableFloatState,
    val nowTime: MutableState<MinuteTime?>, // 在抬起后才赋值，抬起前应以 nowScreenY 计算高度，抬起后以该时间计算高度
    val edgePosition: EdgeScroll.EdgePosition,
  ) {

    val alphaState = mutableFloatStateOf(1F)

    private val clickLock = mutableListOf<MutableTimelineData.ClickLock>()

    suspend fun clear() {
      try {
        animate(
          initialValue = 1F,
          targetValue = 0F,
        ) { value, _ ->
          alphaState.value = value
        }
      } finally {
        alphaState.value = 0F
      }
    }

    fun onMoveEnd(upOrCancel: Boolean) {
      edgePosition.remove()
      clickLock.forEach { it.unlock() }
      clickLock.clear()
    }

    // 移动过程中判断是否需要展开时间轴折叠部分
    fun tryExpandTimeline(
      scrollContext: LocalCourseScrollContext,
    ) {
      scrollContext.timeline.data.asSequence()
        .filterIsInstance<MutableTimelineData>()
        .filter { it.state.value == MutableTimelineData.State.Collapse }
        .mapNotNull { time ->
          scrollContext.timelineCoordinatesMap[time]?.let { coor ->
            val a1 = coor.positionInParent().y
            val a2 = a1 + coor.size.height
            val b1 = minOf(initPosition.y, nowPosition.value.y)
            val b2 = maxOf(initPosition.y, nowPosition.value.y)
            // 中间的折叠时间存在相交区域即可展开
            a1 < b2 && a2 > b1
          }?.let { if (it) time else null }
        }.forEach {
          it.click()
          clickLock.add(it.lockClick()) // 展开后就给点击上锁，直到结束解锁后才允许点击
        }
    }
  }
}

@Composable
private fun LongPressCreateCoursePageWrapper(content: @Composable () -> Unit) {
  val scrollContext = LocalCourseScroll.current
  val timeline = scrollContext.timeline
  val layoutCoordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }
  val edgeScroll = remember { EdgeScroll(bottomMoveBoundary = 80F) } // 扩大底部边界更容易触发滚动
  edgeScroll.attachCompose()

  val items = mutableStateMapOf<PointerId, LongPressItem>()

  // 创建出来的 item 能否允许点击，如果此时还处于触摸中，则是不允许点击的
  val enableClick = rememberWrapper(false)

  Box(
    modifier = Modifier.fillMaxSize().onGloballyPositioned {
      layoutCoordinates.value = it
    }.pointerInputCreateItem(
      items = items,
      edgeScroll = edgeScroll,
      scrollContext = scrollContext,
      layoutCoordinates = layoutCoordinates,
    ).pointerInputClearItem(
      items = items
    ).pointerInputAllUpEnableClick(
      enableClick = enableClick
    )
  ) {
    content()
    items.forEach { (pointerId, item) ->
      key(pointerId) {
        Box(
          modifier = Modifier.layout { measurable, constraints ->
            scrollContext.scrollState.value // 滚轴滚动时仍然触发布局
            val width = constraints.maxWidth / 7
            val y1 = timeline.calculateWeight(item.initTime) * constraints.maxHeight
            val y2 = item.nowTime.value?.let {
              timeline.calculateWeight(it) * constraints.maxHeight
            } ?: layoutCoordinates.value!!.screenToLocal(Offset(0F, item.nowScreenY.floatValue)).y
            val height = abs(y2 - y1).roundToInt()
            val placeable = measurable.measure(Constraints.fixed(width, height))
            layout(placeable.width, placeable.height) {
              placeable.placeRelative(
                x = (item.initPosition.x / width).toInt() * width,
                y = minOf(y1, y2).roundToInt()
              )
            }
          }.graphicsLayer {
            alpha = item.alphaState.floatValue
          }.courseItemBackground(0xFFE9EDF2.dark(0xFF202223))
            .pressScale() // 点击后的 Q 弹动画
        )
      }
    }
  }
}

// 手指点击时清理已有的 item
private fun Modifier.pointerInputClearItem(
  items: MutableMap<PointerId, LongPressItem>,
): Modifier = pointerInput(Unit) {
  supervisorScope {
    awaitEachGesture {
      val down = awaitFirstDown()
      var change: PointerInputChange
      do {
        change = awaitPointerEvent().changes.first { it.id == down.id }
      } while (!change.changedToUpIgnoreConsumed())
      // 等待 down 手指抬起
      if (change.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) {
        // 抬起时间较小时取消所有 items
        items.forEach { entry ->
          launch { entry.value.clear() }.invokeOnCompletion {
            items.remove(entry.key)
          }
        }
      }
    }
  }
}

// 手指抬起时允许点击
private fun Modifier.pointerInputAllUpEnableClick(
  enableClick: Wrapper<Boolean>,
): Modifier = pointerInput(Unit) {
  awaitEachGesture {
    awaitFirstDown(requireUnconsumed = false)
    enableClick.value = false
    var event = currentEvent
    while (event.changes.fastAny { it.pressed }) {
      event = awaitPointerEvent()
    }
    enableClick.value = true
  }
}

// 长按移动创建 item 逻辑
private fun Modifier.pointerInputCreateItem(
  items: MutableMap<PointerId, LongPressItem>,
  edgeScroll: EdgeScroll,
  scrollContext: LocalCourseScrollContext,
  layoutCoordinates: State<LayoutCoordinates?>,
): Modifier = pointerInput(Unit) {
  supervisorScope {
    awaitPointerEventScope {
      val longPressMap = hashMapOf<PointerId, Job>()
      val longPressInitOffset = hashMapOf<PointerId, Offset>()
      while (true) awaitPointerEvent().changes.fastForEach { change ->
        if (change.changedToDown()) {
          // DOWN 开始
          longPressInitOffset[change.id] = change.position
          longPressMap[change.id] = launch {
            // 执行当前手指事件的对应倒计时
            delay(viewConfiguration.longPressTimeoutMillis)
            // 倒计时结束，添加 item 展示
            items[change.id] = LongPressItem(
              initTime = scrollContext.timeline.calculateMinuteTime(scrollContext, change.position.y)!!,
              initPosition = change.position,
              nowPosition = mutableStateOf(change.position),
              nowScreenY = mutableFloatStateOf(layoutCoordinates.value!!.localToScreen(change.position).y),
              nowTime = mutableStateOf(null),
              edgePosition = edgeScroll.add(),
            )
          }
        } else if (change.isConsumed || change.changedToUpIgnoreConsumed()) {
          // CANCEL 或者 UP
          longPressInitOffset.remove(change.id)
          longPressMap.remove(change.id)?.let { job ->
            if (job.isActive) job.cancel() else {
              // 事件被其他消耗或者抬手
              items[change.id]?.let { item ->
                val nowTime = scrollContext.timeline.calculateMinuteTime(scrollContext, change.position.y)!!
                item.nowTime.value = nowTime
                item.edgePosition.remove()
                item.onMoveEnd(!change.isConsumed)
                if (abs(item.initTime.minutesUntil(nowTime)) < 30) {
                  // 暂定小于 30 分钟的事务不支持
                  launch { item.clear() }.invokeOnCompletion {
                    items.remove(change.id)
                  }
                }
              }
            }
          }
        } else longPressMap[change.id]?.let { job ->
          // MOVE
          if (job.isActive) {
            if ((change.position - longPressInitOffset[change.id]!!).getDistance() > viewConfiguration.touchSlop) {
              // 移动距离过大，取消倒计时
              job.cancel()
              longPressMap.remove(change.id)
              longPressInitOffset.remove(change.id)
            }
          } else if (job.isCompleted) {
            // 当前手指事件倒计时已经完成，移动扩大缩小 item
            change.consume()
            items[change.id]?.let { item ->
              item.nowPosition.value = change.position
              item.nowScreenY.floatValue =
                layoutCoordinates.value!!.localToScreen(change.position).y
              item.edgePosition.update(item.nowScreenY.floatValue, 0)
              item.tryExpandTimeline(scrollContext)
            }
          }
        }
      }
    }
  }
}