package com.cyxbs.pages.course.view.item.touch

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.cyxbs.components.utils.compose.getValue
import com.cyxbs.components.utils.compose.rememberUpdatedWrapper
import com.cyxbs.components.utils.compose.rememberWrapper
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.overlay.mergeOverlapRange
import com.cyxbs.pages.course.view.page.LocalCoursePageContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 点击后的 Q 弹动画实现
 *
 * @author 985892345
 * @date 2025/5/4
 */
open class PressScaleController {
  open fun onStartPress() {}
  open fun onEndPress() {}
}


@Stable
@Composable
fun Modifier.pressScale(
  controller: PressScaleController,
): Modifier {
  val controllerWrapper by rememberUpdatedWrapper(controller)
  val pointerOffset = rememberWrapper<Offset?>(null)
  val scale = remember { Animatable(initialValue = 1F) }
  val coroutineScope = rememberCoroutineScope()
  return pointerInput(Unit) {
    awaitEachGesture {
      val down = awaitFirstDown(
        requireUnconsumed = false,
        pass = PointerEventPass.Initial,
      )
      pointerOffset.value = down.position
      controllerWrapper.onStartPress()
      coroutineScope.launch { scale.animateTo(0.8F) }
      val endBlock: () -> Unit = {
        pointerOffset.value = null
        if (coroutineScope.isActive) coroutineScope.launch {
          try {
            scale.animateTo(1F)
          } finally {
            controllerWrapper.onEndPress()
          }
        } else controllerWrapper.onEndPress()
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


// 点击后的 Q 弹动画控制器的默认实现
class PressScaleControllerImpl(
  val itemState: CourseItemState,
  var coursePageContext: LocalCoursePageContext,
) : PressScaleController() {

  // overlap 更新触发器
  private val overlapChangeTrigger =
    CourseItemState.CoveredItemShowRangeTransformerTrigger(coursePageContext) { show, overlap ->
      // 只有没有 showRange 时才能移除覆盖区域展示出来
      // 防止部分覆盖的这类 item 一碰就立马触发 text 扩展动画，看起来就很怪异
      // 如果 item 本身存在一些区域可以展示，那么它的背景是能完全展示的，也不需要单独取消覆盖区域
      show.ifEmpty {
        val coveredRange = overlap.coveredRangeList
          .filter { it.data.item === itemState.item }
          .map { it.range }
        (show + coveredRange).mergeOverlapRange()
      }
    }

  override fun onStartPress() {
    super.onStartPress()
    itemState.addOverlapChangeTrigger(overlapChangeTrigger)
  }

  override fun onEndPress() {
    super.onEndPress()
    itemState.removeOverlapChangeTrigger(overlapChangeTrigger)
  }
}