package com.cyxbs.pages.qa.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * description ： 用于解决滑动冲突的RV
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2025/8/19 16:28
 */
class QaVerticalRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : RecyclerView(context, attrs, defStyle) {
    private var startX = 0f
    private var startY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // 用于锁定已判定的方向
    private var isDragging = false
    private var isVerticalScroll = false


    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                startX = ev.x
                startY = ev.y
                isDragging = false
                isVerticalScroll = false
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - startX
                val dy = ev.y - startY

                if (!isDragging) {
                    // 只有当任一方向超过 touchSlop 时才第一次判定方向
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        isDragging = true
                        // 如果水平移动显著大于垂直,才认为是水平滚动
                        isVerticalScroll = !(abs(dx) > abs(dy) * 1.25)
                    }
                }


                if (isDragging) {
                    if (isVerticalScroll) {

                        parent?.requestDisallowInterceptTouchEvent(true)
                    } else {

                        parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {

                startX = 0f
                startY = 0f
                isDragging = false
                isVerticalScroll = false
                parent.requestDisallowInterceptTouchEvent(false)
            }
        }

        return super.dispatchTouchEvent(ev)
    }
}