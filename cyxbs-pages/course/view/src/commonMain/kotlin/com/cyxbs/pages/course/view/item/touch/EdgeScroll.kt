package com.cyxbs.pages.course.view.item.touch

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.IntSize
import com.cyxbs.components.utils.extensions.logg
import com.cyxbs.pages.course.view.timeline.LocalCourseScroll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlin.math.abs
import kotlin.math.sign

/**
 * 边缘滚动
 *
 * 比如长按移动 item 后，item 边缘靠近 scroll 边缘时，滚轴就会自动滚动
 *
 * @author 985892345
 * @date 2025/5/4
 */
@Stable
class EdgeScroll {

  private var scrollOuterCoordinates: LayoutCoordinates? = null

  private val list = mutableListOf<EdgePosition>()

  private val firstPosition = MutableStateFlow<EdgePosition?>(null)

  fun add(): EdgePosition {
    val position = EdgePosition()
    if (list.isEmpty()) {
      firstPosition.value = position
    }
    list.add(position)
    return position
  }

  @Composable
  fun attachCompose() {
    val outerCoordinates = LocalCourseScroll.current.outerCoordinates
    SideEffect { scrollOuterCoordinates = outerCoordinates.value }
    val scrollState = LocalCourseScroll.current.scrollState
    val moveBoundary = 40 // 移动的边界值
    LaunchedEffect(Unit) {
      firstPosition.collectLatest { position ->
        if (position == null) return@collectLatest
        var changeFlag = true // 用于给 distinctUntilChanged 强制更新
        position.distanceFlow.distinctUntilChangedBy {
          it to changeFlag
        }.collectLatest {
          if (
            abs(it) < moveBoundary
            && (it > 0 && scrollState.value.canScrollBackward || it < 0 && scrollState.value.canScrollForward)
          ) {
            val velocity = -it.sign * ((moveBoundary - abs(it)) / 4 + 2F)
            try {
              scrollState.value.scroll {
                animate(
                  initialValue = velocity,
                  targetValue = velocity,
                  animationSpec = infiniteRepeatable(tween(200))
                ) { value, _ ->
                  scrollBy(value)
                }
              }
            } catch (e: CancellationException) {
              if (e.message == "Mutation interrupted") {
                // 被其他滚动打断时就锁住
                position.lock = true
                // 如果被锁住后，则下一次需要绕开 distinctUntilChanged 重新触发 collect
                // 因为超出边界外后就只发送 ±0.1，此时就会被 distinctUntilChanged 拦截
                changeFlag = !changeFlag
              }
            }
          }
        }
      }
    }
  }

  @Stable
  inner class EdgePosition {

    val distanceFlow =
      MutableSharedFlow<Float>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    // 是否被锁住
    // 当被锁住时需要移动一段距离才会解锁滚动
    var lock = true

    // 上一次的中心点位置
    // 用于被锁时解锁需要的移动距离阈值判断
    var lastCenterY = Float.NaN

    /**
     * @param screenPosition 相对于屏幕的坐标（组件左上角）
     * @param size 组件的大小，如果不为组件而是一个触摸点时提供 IntSize.Zero 即可
     */
    fun update(screenPosition: Offset, size: IntSize) {
      Snapshot.withoutReadObservation {
        val outerCoordinates = scrollOuterCoordinates
        if (outerCoordinates != null) {
          val topY = outerCoordinates.screenToLocal(screenPosition).y
          val bottomY = topY + size.height
          val centerY = topY + size.height / 2F
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
          } else {
            lastCenterY = centerY
            // 正值表示距离上边界的距离，负值表示距离下边界的距离
            val distance = if (centerY < outerCenterY) {
              topY.coerceAtLeast(0.1F)
            } else {
              (bottomY - outerCoordinates.size.height).coerceAtMost(-0.1F)
            }
            logg("distance = $distance")
            distanceFlow.tryEmit(distance)
          }
        }
      }
    }

    // 移除，与 add 要成对调用
    fun remove() {
      list.remove(this)
      firstPosition.value = list.firstOrNull()
    }
  }
}
