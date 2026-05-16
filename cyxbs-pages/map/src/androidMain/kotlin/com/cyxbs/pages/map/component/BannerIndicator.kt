package com.cyxbs.pages.map.component

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.viewpager.widget.ViewPager
import com.cyxbs.components.utils.extensions.dp2pxF


class BannerIndicator : LinearLayout {

    private var itemCount = 0

    private var selectedWidth = 20f

    private var dia = 10f

    private var space = 5f

    private var shadowRadius = 2f

    private var rectF = RectF()

    private lateinit var paint: Paint

    private var lastPositionOffset = 0f

    private var firstVisiblePosition = 0

    private var indicatorColor = 0xffffffff

    private var shadowColor = 0x44000000

    private var isAnimation = true

    private var isShadow = true


    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        init()
    }

    private fun init() {

        //默认值
        selectedWidth = 20f.dp2pxF
        dia = 5f.dp2pxF
        space = 20f.dp2pxF
        shadowRadius = 2f.dp2pxF
        setWillNotDraw(false)


        if (isShadow)
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)

        paint = Paint().apply {
            flags = Paint.ANTI_ALIAS_FLAG
            color = indicatorColor.toInt()
            style = Paint.Style.FILL
            if (isShadow)
                setShadowLayer(shadowRadius, shadowRadius / 2, shadowRadius / 2, shadowColor)
        }

    }


    fun setupWithViewPager(viewPager: ViewPager) {
        if (viewPager.adapter == null) {
            throw IllegalArgumentException("viewPager adapter not be null")
        }

        itemCount = viewPager.adapter!!.count

        if (isShadow) {
            layoutParams.width = ((itemCount - 1) * (space + dia) + selectedWidth + shadowRadius).toInt()
            layoutParams.height = (dia + shadowRadius).toInt()
        } else {
            layoutParams.width = ((itemCount - 1) * (space + dia) + selectedWidth).toInt()
            layoutParams.height = dia.toInt()
        }

        viewPager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels)
                if (isAnimation) {
                    firstVisiblePosition = position
                    lastPositionOffset = positionOffset
                    invalidate()
                }
            }

            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (isAnimation.not()) {
                    firstVisiblePosition = position
                    invalidate()
                }
            }

        })

    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (isInEditMode || itemCount == 0) {
            return
        }

        for (i in 0 until itemCount) {
            var left: Float
            var right: Float

            when {
                i < firstVisiblePosition -> {
                    left = i * (dia + space)
                    right = left + dia
                }
                i == firstVisiblePosition -> {
                    left = i * (dia + space)
                    right = left + dia + (selectedWidth - dia) * (1 - lastPositionOffset)
                }
                i == firstVisiblePosition + 1 -> {
                    left = (i - 1) * (space + dia) + dia + (selectedWidth - dia) * (1 - lastPositionOffset) + space
                    right = i * (space + dia) + selectedWidth
                }
                else -> {
                    left = (i - 1) * (dia + space) + (selectedWidth + space)
                    right = (i - 1) * (dia + space) + (selectedWidth + space) + dia
                }
            }

            val top = 0f
            val bottom = dia

            rectF.left = left
            rectF.top = top
            rectF.right = right
            rectF.bottom = bottom

            canvas.drawRoundRect(rectF, dia / 2, dia / 2, paint)

        }

    }

}