package com.cyxbs.pages.course.view.item.touch

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.utils.compose.Wrapper
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.compose.rememberWrapper
import com.cyxbs.pages.course.view.item.touch.LongPressCreate.TouchingItem
import com.cyxbs.pages.course.view.decoration.CoursePageDecoration
import com.cyxbs.pages.course.view.item.modifier.PressScaleController
import com.cyxbs.pages.course.view.item.modifier.RoundedShadowItemModifier
import com.cyxbs.pages.course.view.item.modifier.pressScale
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import com.cyxbs.pages.course.view.timeline.LocalCourseScroll
import com.cyxbs.pages.course.view.timeline.LocalCourseScrollContext
import com.cyxbs.pages.course.view.timeline.data.MutableTimelineData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.datetime.DayOfWeek
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.collections.component1
import kotlin.collections.component2
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
  override fun CoursePage(nextContent: @Composable (() -> Unit)) {
    LongPressCreateCoursePageWrapper(nextContent)
  }

  @Stable
  class TouchingItem(
    val initTime: MinuteTime,
    val initPosition: Offset,
    val nowPosition: MutableState<Offset>,
    val nowScreenY: MutableFloatState,
    val edgePosition: EdgeScroll.EdgePosition,
  ) {

    private val clickLock = mutableListOf<MutableTimelineData.ClickLock>()

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

  @Serializable
  class TouchedItem(
    val id: Long,
    val dayOfWeek: DayOfWeek,
    val start: MinuteTime,
    val end: MinuteTime,
  ) {
    @Transient
    val alphaState = mutableFloatStateOf(1F)

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
  }
}

@Composable
private fun LongPressCreateCoursePageWrapper(nextContent: @Composable (() -> Unit)) {
  val scrollContext = LocalCourseScroll.current
  val timeline = scrollContext.timeline
  val layoutCoordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }
  val edgeScroll = remember { EdgeScroll(bottomMoveBoundary = 80F) } // 扩大底部边界更容易触发滚动
  edgeScroll.attachCompose()

  // 触摸中的 item
  val touchingItems = remember { mutableStateMapOf<PointerId, TouchingItem>() }

  // 触摸抬起后的 item
  // 使用 rememberSaveable 进行保存，即使页面滑走再滑回仍会显示
  val touchedItems = rememberSaveable(
    saver = Saver(
      save = {
        defaultJson.encodeToString<List<LongPressCreate.TouchedItem>>(it)
      },
      restore = {
        defaultJson.decodeFromString<List<LongPressCreate.TouchedItem>>(it).toMutableStateList()
      }
    )) {
    mutableStateListOf<LongPressCreate.TouchedItem>()
  }

  // 创建出来的 item 能否允许点击，如果此时还处于触摸中，则是不允许点击的
  val enableClick = rememberWrapper(false)

  Box(
    modifier = Modifier.fillMaxSize().onGloballyPositioned {
      layoutCoordinates.value = it
    }.pointerInputCreateItem(
      touchingItems = touchingItems,
      touchedItems = touchedItems,
      edgeScroll = edgeScroll,
      scrollContext = scrollContext,
      layoutCoordinates = layoutCoordinates,
    ).pointerInputClearItem(
      touchedItems = touchedItems
    ).pointerInputAllUpEnableClick(
      enableClick = enableClick
    )
  ) {
    nextContent.invoke()
    TouchedItems(
      touchedItems = touchedItems,
      timeline = timeline,
    )
    TouchingItems(
      touchingItems = touchingItems,
      scrollContext = scrollContext,
      layoutCoordinates = layoutCoordinates,
    )
  }
}

@Composable
private fun TouchedItems(
  touchedItems: SnapshotStateList<LongPressCreate.TouchedItem>,
  timeline: CourseTimeline
) {
  touchedItems.forEach { item ->
    key(item.id) {
      Box(
        modifier = Modifier.layout { measurable, constraints ->
          val width = constraints.maxWidth / 7
          val weight = timeline.calculateBeginFinalWeight(item.start, item.end)
          val height = (constraints.maxHeight * (weight.y - weight.x)).roundToInt()
          val placeable = measurable.measure(Constraints.fixed(width, height))
          layout(placeable.width, placeable.height) {
            placeable.placeRelativeWithLayer(
              x = item.dayOfWeek.ordinal * width,
              y = (constraints.maxHeight * weight.x).roundToInt(),
              layerBlock = {
                alpha = item.alphaState.floatValue
              }
            )
          }
        }.then(RoundedShadowItemModifier.createModifier())
          .background(0xFFE9EDF2.dark(0xFF202223))
          .pressScale(PressScaleController) // 点击后的 Q 弹动画
      )
    }
  }
}

@Composable
private fun TouchingItems(
  touchingItems: SnapshotStateMap<PointerId, TouchingItem>,
  scrollContext: LocalCourseScrollContext,
  layoutCoordinates: State<LayoutCoordinates?>,
) {
  touchingItems.forEach { (pointerId, item) ->
    key(pointerId.value) {
      Box(
        modifier = Modifier.layout { measurable, constraints ->
          scrollContext.scrollState.value // 滚轴滚动时仍然触发布局
          val width = constraints.maxWidth / 7
          val y1 = scrollContext.timeline.calculateWeight(item.initTime) * constraints.maxHeight
          val y2 =
            layoutCoordinates.value!!.screenToLocal(Offset(0F, item.nowScreenY.floatValue)).y
          val height = abs(y2 - y1).roundToInt()
          val placeable = measurable.measure(Constraints.fixed(width, height))
          layout(placeable.width, placeable.height) {
            placeable.placeRelative(
              x = (item.initPosition.x / width).toInt() * width,
              y = minOf(y1, y2).roundToInt()
            )
          }
        }.then(RoundedShadowItemModifier.createModifier())
          .background(0xFFE9EDF2.dark(0xFF202223))
      )
    }
  }
}

// 手指点击时清理已有的 item
private fun Modifier.pointerInputClearItem(
  touchedItems: SnapshotStateList<LongPressCreate.TouchedItem>,
): Modifier = pointerInput(Unit) {
  supervisorScope {
    awaitEachGesture {
      val down = awaitFirstDown()
      var change: PointerInputChange
      do {
        change = awaitPointerEvent().changes.first { it.id == down.id }
      } while (!change.changedToUpIgnoreConsumed())
      // 等待 down 手指抬起
      if (
        change.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
        && (change.position - down.position).getDistance() < viewConfiguration.touchSlop
      ) {
        // 抬起时间较小时取消所有 items
        touchedItems.forEach { item ->
          launch { item.clear() }.invokeOnCompletion {
            touchedItems.remove(item)
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
  touchingItems: SnapshotStateMap<PointerId, TouchingItem>,
  touchedItems: SnapshotStateList<LongPressCreate.TouchedItem>,
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
            touchingItems[change.id] = TouchingItem(
              initTime = scrollContext.timeline.calculateMinuteTime(
                scrollContext,
                change.position.y
              )!!,
              initPosition = change.position,
              nowPosition = mutableStateOf(change.position),
              nowScreenY = mutableFloatStateOf(layoutCoordinates.value!!.localToScreen(change.position).y),
              edgePosition = edgeScroll.add(),
            )
          }
        } else if (change.isConsumed || change.changedToUpIgnoreConsumed()) {
          // CANCEL 或者 UP
          longPressInitOffset.remove(change.id)
          longPressMap.remove(change.id)?.let { job ->
            if (job.isActive) job.cancel() else {
              // 事件被其他消耗或者抬手
              touchingItems.remove(change.id)?.let { item ->
                item.onMoveEnd(!change.isConsumed)
                val nowTime =
                  scrollContext.timeline.calculateMinuteTime(scrollContext, change.position.y)!!
                val touchedItem = LongPressCreate.TouchedItem(
                  id = change.id.value,
                  dayOfWeek = DayOfWeek((item.initPosition.x / (size.width / 7)).toInt() + 1),
                  start = minOf(item.initTime, nowTime),
                  end = maxOf(item.initTime, nowTime),
                )
                touchedItems.add(touchedItem)
                if (abs(item.initTime.minutesUntil(nowTime)) < 30) {
                  // 暂定小于 30 分钟的事务不支持
                  launch { touchedItem.clear() }.invokeOnCompletion {
                    touchedItems.remove(touchedItem)
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
            touchingItems[change.id]?.let { item ->
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