package com.cyxbs.pages.sport.widget

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * @Desc : 下拉刷新状态与动画管理
 * @Author : xt
 *
 * @param triggerOffset 触发刷新的下拉距离
 */
@Stable
class RefreshState(
    val triggerOffset: Float,
) {
    var pullOffset by mutableFloatStateOf(0f)
        private set


    var isRefreshing by mutableStateOf(false)
        private set

    //用户手指下拉时图标跟随旋转的进程
    val progress: Float
        get() = (pullOffset / triggerOffset)

    /**
     * 消费手指拖动距离并返回实际消耗的偏移量。
     * @param deltaY 手指本次垂直移动距离
     * @return 实际被刷新头部消费的距离
     */
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

    // 在用户松手后根据阈值开始刷新或回弹
    suspend fun release(): Boolean {
        if (pullOffset <= 0f || isRefreshing) return false

        if (pullOffset < triggerOffset) {
            animatePullOffsetTo(0f)
            return false
        }

        isRefreshing = true
        animatePullOffsetTo(triggerOffset)
        return true
    }

    // 完成刷新并将刷新头部收回
    suspend fun finishRefresh() {
        if (!isRefreshing) return
        animatePullOffsetTo(0f)
        isRefreshing = false
    }

    /**
     * 使用弹簧动画将头部移动到指定偏移量
     * @param target 目标偏移量
     */
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

    /**
     * 消费下拉惯性并判断是否需要触发刷新
     * @param initialVelocity 初始垂直速度
     * @return 是否产生有效下拉偏移
     */
    suspend fun consumeFling(initialVelocity: Float): Boolean {
        if (isRefreshing || initialVelocity <= 0f) return false

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
        return pullOffset > 0
    }
}
