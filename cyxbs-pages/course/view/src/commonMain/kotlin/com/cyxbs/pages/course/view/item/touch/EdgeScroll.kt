package com.cyxbs.pages.course.view.item.touch

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import com.cyxbs.components.utils.extensions.logg
import com.cyxbs.pages.course.view.timeline.LocalCourseScrollContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * 边缘滚动
 *
 * 比如长按移动 item 后，item 边缘靠近 scroll 边缘时，滚轴就会自动滚动
 *
 * @author 985892345
 * @date 2025/5/4
 */
@Stable
class EdgeScroll(
  val scrollContext: LocalCourseScrollContext,
) {

  private val edgePointerMap = mutableMapOf<String, EdgePointer>()

  private var handleEdgePointer: EdgePointer? = null

  private val scrollVelocityFlow = MutableStateFlow(0F)

  fun tryEdgeScroll(
    key: String, // 区分不同的触发源，唯一即可
    screenTopY: Float, // 相对于 screen 的顶部 y 值
    height: Int, // 高度
    topMoveBoundary: Float = 40F, // 可移动的上边界距离
    bottomMoveBoundary: Float = 40F, // 可移动的下边界距离
  ) {
    val handleEdgePointer = handleEdgePointer
    if (handleEdgePointer != null) {
      if (handleEdgePointer.key == key) {
        val velocity = handleEdgePointer.getVelocity(
          screenTopY = screenTopY,
          height = height,
          topMoveBoundary = topMoveBoundary,
          bottomMoveBoundary = bottomMoveBoundary
        )
        logg("111 key = $key, velocity = $velocity")
        scrollVelocityFlow.value = velocity
        if (velocity == 0F) {
          this.handleEdgePointer = null
        }
      }
      return
    }
    val edgePointer = edgePointerMap.getOrPut(key) { EdgePointer(key) }
    val velocity = edgePointer.getVelocity(
      screenTopY = screenTopY,
      height = height,
      topMoveBoundary = topMoveBoundary,
      bottomMoveBoundary = bottomMoveBoundary
    )
    logg("222 key = $key, velocity = $velocity")
    if (velocity != 0F) {
      this.handleEdgePointer = edgePointer
      edgePointerMap.clear()
    }
  }

  inner class EdgePointer(val key: String) {
    // 是否被锁住
    // 当被锁住时需要移动一段距离才会解锁滚动
    private var lock = true

    // 上一次的中心点位置
    // 用于被锁时解锁需要的移动距离阈值判断
    private var lastCenterY = Float.NaN

    fun getVelocity(
      screenTopY: Float,
      height: Int,
      topMoveBoundary: Float, // 可移动的上边界距离
      bottomMoveBoundary: Float, // 可移动的下边界距离
    ): Float {
      val outerCoordinates = scrollContext.outerCoordinates ?: return 0F
      val topY = outerCoordinates.screenToLocal(Offset(0F, screenTopY)).y
      val bottomY = topY + height
      val centerY = topY + height / 2F
      val outerCenterY = outerCoordinates.size.height / 2F
      if (lock) {
        // 被锁住时
        if (lastCenterY.isNaN()) lastCenterY = centerY // 第一次初始化
        if (lastCenterY > outerCenterY) {
          if (centerY < lastCenterY) {
            // 在底部区域向上移动，则 lastCenterY 跟随 centerY，不进行解锁
            lastCenterY = centerY
          } else if (centerY > lastCenterY + 20) {
            // 只有在底部区域向下移动超过 20 时才解锁
            lock = false
          }
        } else {
          if (centerY > lastCenterY) {
            lastCenterY = centerY
          } else if (centerY < lastCenterY - 20) {
            lock = false
          }
        }
        return 0F
      } else {
        lastCenterY = centerY
        // 正值表示距离上边界的距离，负值表示距离下边界的距离
        val distance = if (centerY < outerCenterY) {
          topY.coerceAtLeast(0.1F)
        } else {
          (bottomY - outerCoordinates.size.height).coerceAtMost(-0.1F)
        }
        if (
          distance in 0F..topMoveBoundary && scrollContext.scrollState.canScrollBackward
          || distance in -bottomMoveBoundary..0F && scrollContext.scrollState.canScrollForward
        ) {
          val velocity = if (distance > 0) {
            -((topMoveBoundary - distance) / 4 + 2F)
          } else {
            (bottomMoveBoundary + distance) / 4 + 2F
          }.coerceIn(-12F, 12F)
          return velocity
        }
      }
      return 0F
    }
  }


  @Composable
  fun AttachCompose() {
    // 应该改成每个 EdgePosition 都可以尝试触发边缘滚动，但只有第一个成功触发将一直维持到不在滚动，然后再等待下一个 EdgePosition
    LaunchedEffect(Unit) {
      scrollVelocityFlow.collectLatest { velocity ->
        if (velocity == 0F) return@collectLatest
        try {
          scrollContext.scrollState.scroll {
            animate(
              initialValue = velocity,
              targetValue = velocity,
              animationSpec = infiniteRepeatable(tween(200)) // 无限重复动画
            ) { value, _ ->
              if (scrollBy(value) == 0F) {
                // 滚动到底或顶时，抛出异常表示动画结束
                throw CancellationException("No remaining distance")
              }
            }
          }
        } catch (e: CancellationException) {
          if (e.message == "Mutation interrupted"
            || e.message == "Current mutation had a higher priority"
            || e.message == "No remaining distance"
          ) {
            // 动画结束时重置
            handleEdgePointer = null
            scrollVelocityFlow.value = 0F // 还原初始值
          }
        }
      }
    }
  }
}
