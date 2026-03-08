package com.cyxbs.pages.course.view.item.touch

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastForEach
import com.cyxbs.components.utils.utils.VibratorUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * .
 *
 * @author 985892345
 * @date 2026/3/1
 */

interface LongPressCreateItem {
  val initPosition: Offset
  var touchPosition: Offset

  // 移动结束，手指抬起或者事件被拦截
  fun onMoveEnd(coroutineScope: CoroutineScope)
}

@Composable
fun LongPressCreateItemCompose(
  onCreate: (beginPosition: Offset, size: IntSize) -> LongPressCreateItem?,
  onTap: () -> Unit, // 手指轻击时可被视为清理已有的 item
) {
  val layoutCoordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }
  Spacer(
    modifier = Modifier.fillMaxSize().onGloballyPositioned {
      layoutCoordinates.value = it
    }.pointerInputCreateItem(
      onCreate = onCreate,
    ).pointerInputClearItem(
      onTap = onTap,
    )
  )
}


// 长按移动创建 item 逻辑
private fun Modifier.pointerInputCreateItem(
  onCreate: (beginPosition: Offset, size: IntSize) -> LongPressCreateItem?,
): Modifier = pointerInput(Unit) {
  supervisorScope {
    awaitPointerEventScope {
      val longPressMap = hashMapOf<PointerId, Job>()
      val longPressInitOffset = hashMapOf<PointerId, Offset>()
      val touchingItems = hashMapOf<PointerId, LongPressCreateItem>()
      while (true) awaitPointerEvent().changes.fastForEach { change ->
        if (change.changedToDown()) {
          // DOWN 开始
          longPressInitOffset[change.id] = change.position
          longPressMap[change.id] = launch {
            // 执行当前手指事件的对应倒计时
            delay(viewConfiguration.longPressTimeoutMillis)
            // 触发震动
            VibratorUtil.longPress()
            // 倒计时结束，添加 item 展示
            val item = onCreate(change.position, size)
            if (item != null) {
              touchingItems[change.id] = item
            }
          }
        } else if (change.isConsumed || change.changedToUpIgnoreConsumed()) {
          // CANCEL 或者 UP
          longPressInitOffset.remove(change.id)
          longPressMap.remove(change.id)?.let { job ->
            if (job.isActive) job.cancel() else {
              // 事件被其他消耗或者抬手
              touchingItems.remove(change.id)?.onMoveEnd(this@supervisorScope)
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
            // 当前手指事件倒计时已经完成，移动扩大缩小 item 边界
            change.consume()
            touchingItems[change.id]?.touchPosition = change.position
          }
        }
      }
    }
  }
}


// 手指点击时清理已有的 item
private fun Modifier.pointerInputClearItem(
  onTap: () -> Unit,
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
        onTap.invoke()
      }
    }
  }
}