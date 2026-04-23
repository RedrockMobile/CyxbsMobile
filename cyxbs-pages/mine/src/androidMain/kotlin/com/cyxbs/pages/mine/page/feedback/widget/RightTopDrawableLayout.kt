package com.cyxbs.pages.mine.page.feedback.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.res.ResourcesCompat
import com.cyxbs.pages.mine.R
import kotlin.math.pow

/**
 *@author ZhiQiang Tu
 *@time 2021/8/24  20:13
 *@signature 我们不明前路，却已在路上
 */
class RightTopDrawableLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var rightTopDrawable: Drawable? =
        ResourcesCompat.getDrawable(resources, R.drawable.mine_ic_feedback_ic_cancel, null)
    var rightTopDrawableSize: Float = 0f

    var iconClicked: ((View) -> Unit)? = null
    var contentClicked: ((View) -> Unit)? = null


    init {
        setWillNotDraw(false)

        val attrSets = context.obtainStyledAttributes(attrs, R.styleable.RightTopDrawableLayout)
        val drawable = attrSets.getDrawable(R.styleable.RightTopDrawableLayout_rightTopDrawable)
        if (drawable != null) {
            rightTopDrawable = drawable
        }
        rightTopDrawableSize =
            attrSets.getDimension(R.styleable.RightTopDrawableLayout_rightTopDrawableSize,
                dpToFloat(18f))
        attrSets.recycle()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_UP) {
            val x = event.x
            val y = event.y
            val circleX = width - rightTopDrawableSize / 2
            val circleY = rightTopDrawableSize / 2
            //如果事件在1.2倍半径区域内视为点击成功
            val clickState =
                (x - circleX).pow(2) + (y - circleY).pow(2) <= (0.5 * rightTopDrawableSize * 1.2).pow(
                    2)
            if (clickState) {
                iconClicked?.invoke(this)
            } else {
                if (x > 0 && x < width && y > 0 && y < height) {
                    contentClicked?.invoke(this)
                }
            }
        }
        return true
    }

    fun setOnContentClickListener(listener: (View) -> Unit) {
        this.contentClicked = listener
    }

    fun setOnIconClickListener(listener: (View) -> Unit) {
        this.iconClicked = listener
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        setPadding(0, (rightTopDrawableSize/6).toInt(), (rightTopDrawableSize/6).toInt(), 0)
        rightTopDrawable?.setBounds((width - rightTopDrawableSize).toInt(),
            0,
            width,
            rightTopDrawableSize.toInt())
        rightTopDrawable?.draw(canvas)
    }

    private fun dpToFloat(float: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, float,
        resources.displayMetrics
    )

}