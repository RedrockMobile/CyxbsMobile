package com.cyxbs.pages.sport.widget

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class RefreshState(
    val triggerOffset: Float,
    private val onRefresh: () -> Boolean,
) {
    var pullOffset by mutableFloatStateOf(0f)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    //用户手指下拉时图标跟随旋转的进程
    val progress: Float
        get() = (pullOffset / triggerOffset)

    fun consumeDrag(deltaY: Float): Float {
        if (isRefreshing) return 0f

        val oldOffset = pullOffset

        val dragDelta = if (deltaY > 0f) {
            deltaY * 0.5f
        } else {
            deltaY
        }

        pullOffset = (pullOffset + dragDelta)
            .coerceIn(0f, triggerOffset * 1.5f)
        return pullOffset - oldOffset
    }

    suspend fun release() {
        if (pullOffset <= 0f || isRefreshing) return

        if (pullOffset < triggerOffset) {
            animatePullOffsetTo(0f)
            return
        }

        isRefreshing = true
        animatePullOffsetTo(triggerOffset)

        if (!onRefresh()) {
            isRefreshing = false
            animatePullOffsetTo(0f)
        }
    }

    suspend fun finishRefresh() {
        if (!isRefreshing) return
        animatePullOffsetTo(0f)
        isRefreshing = false
    }

    suspend fun animatePullOffsetTo(target: Float) {
        animate(
            initialValue = pullOffset,
            targetValue = target,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
        ) { value, _ ->
            pullOffset = value
        }
    }

    suspend fun consumeFling(initialVelocity: Float) {
        if (isRefreshing || initialVelocity <= 0f) return

        val maxOffset = triggerOffset * 1.5f
        var oldAnimationValue = 0f

        AnimationState(
            initialValue = 0f,
            initialVelocity = initialVelocity,
        ).animateDecay(exponentialDecay(frictionMultiplier = 3f)) {
            val delta = value - oldAnimationValue
            oldAnimationValue = value

            val headerDelta = delta * 0.2f

            pullOffset = (pullOffset + headerDelta)
                .coerceIn(0f, maxOffset)

            when {
                // 达到最大下拉距离，取消惯性滑动
                pullOffset >= maxOffset -> cancelAnimation()

                // 速度低于250f并且未达刷新阈值，取消惯性滑动
                pullOffset < triggerOffset &&
                        velocity <= 250f -> cancelAnimation()
            }
        }
        release()
    }
}