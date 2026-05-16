package com.cyxbs.pages.grades.utils.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.cyxbs.pages.grades.R
import com.cyxbs.pages.grades.utils.extension.dp2px
import kotlin.math.abs
import kotlin.properties.Delegates

/**
 * Created by roger on 2020/2/11
 */
@Suppress("NAME_SHADOWING")
class GpAGraph : View {

    constructor(ctx: Context) : this(ctx, null)

    constructor(ctx: Context, attrs: AttributeSet?) : this(ctx, attrs, 0)

    constructor(ctx: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(ctx, attrs, defStyleAttr) {

        val typeArray = ctx.obtainStyledAttributes(attrs, R.styleable.GpAGraph)
        gpaLineColor = typeArray.getColor(R.styleable.GpAGraph_gpa_line_color, Color.parseColor("#2921D1"))
        gpaDashColor = typeArray.getColor(R.styleable.GpAGraph_gpa_dash_color, Color.parseColor("#2915315B"))
        gpaTextColor = typeArray.getColor(R.styleable.GpAGraph_gpa_text_color, Color.parseColor("#A415315B"))
        gpaCircleInsideColor = typeArray.getColor(R.styleable.GpAGraph_gpa_circle_inside_color, Color.parseColor("#FFFFFFFF"))
        gpaGradientBottomColor = typeArray.getColor(R.styleable.GpAGraph_gpa_gradient_bottom_color, Color.parseColor("#66FFFFFF"))
        gpaGradientTopColor = typeArray.getColor(R.styleable.GpAGraph_gpa_gradient_top_color, Color.parseColor("#44A19EFF"))
        gpaTextSize = typeArray.getColor(R.styleable.GpAGraph_gpa_text_size, dp2px(11))
        typeArray.recycle()
        init()
    }

    private var background: Int? = null

    //默认的宽高
    private val mWidth: Int = dp2px(100)
    private val mHeight: Int = dp2px(100)

    private lateinit var path: Path
    private lateinit var pathBlue: Path

    //顶部和底部预留的高度
    private val bottomHeight = dp2px(50)
    private val topHeight = dp2px(45)

    private var segHeight: Int = 0
    private var segWidth: Int = 0

    //Paint
    private lateinit var dashPaint: Paint
    private lateinit var gpaPaint: Paint
    private lateinit var textPaint: Paint
    private lateinit var gradientPaint: Paint
    private lateinit var whitePaint: Paint

    //color and property
    private var gpaLineColor by Delegates.notNull<Int>()
    private var gpaDashColor by Delegates.notNull<Int>()
    private var gpaTextColor by Delegates.notNull<Int>()
    private var gpaCircleInsideColor by Delegates.notNull<Int>()
    private var gpaGradientBottomColor by Delegates.notNull<Int>()
    private var gpaGradientTopColor by Delegates.notNull<Int>()
    private var gpaTextSize by Delegates.notNull<Int>()


    private lateinit var linearGradient: LinearGradient

    //用户点击的是textArray中的第几个。例如用户点击"大一上"，touchPoint则为0
    private var touchPoint: Int? = null
    private val textArray = arrayListOf("大一上", "大一下", "大二上", "大二下", "大三上", "大三下", "大四上", "大四下")

    /**
     * 数据
     */
    private var originalData = arrayListOf<String>()   //原始数据
    private lateinit var mappingData: List<Float>       //经过映射后的数据：通过将array用mappingRule一一映射得到

    //映射规则
    private var mappingRule: GraphRule = DefaultMappingRule()

    private lateinit var bubble: Bitmap


    private fun init() {

        gpaPaint = Paint()
        gpaPaint.color = gpaLineColor
        gpaPaint.style = Paint.Style.STROKE
        gpaPaint.strokeWidth = dp2px(4).toFloat()

        path = Path()
        pathBlue = Path()

        dashPaint = Paint()
        dashPaint.color = gpaDashColor
        dashPaint.style = Paint.Style.STROKE
        dashPaint.strokeWidth = dp2px(1).toFloat()
        dashPaint.pathEffect = DashPathEffect(floatArrayOf(dp2px(5).toFloat(), dp2px(3).toFloat()), 0F)

        textPaint = Paint()
        textPaint.color = gpaTextColor
        textPaint.textSize = gpaTextSize.toFloat()
        textPaint.textAlign = Paint.Align.CENTER

        gradientPaint = Paint()
        gradientPaint.style = Paint.Style.FILL

        whitePaint = Paint()
        whitePaint.color = gpaCircleInsideColor
        whitePaint.style = Paint.Style.FILL
        //将原始数据转换成元素范围在区间[0,4]的数据
        mappingData = originalData.map {
            mappingRule.mappingRule(it)
        }

        bubble = BitmapFactory.decodeResource(resources, R.drawable.grades_ic_bubble)

    }

    /**
     * 设置数据
     */
    fun setData(newData: List<String>) {
        originalData = ArrayList(newData)
        mappingData = originalData.map {
            mappingRule.mappingRule(it)
        }
    }

    /**
     * 设置规则
     */
    fun setRule(newRule: GraphRule) {
        mappingRule = newRule
        mappingData = originalData.map {
            mappingRule.mappingRule(it)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(resolveSize(mWidth, widthMeasureSpec), resolveSize(mHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        path.reset()
        pathBlue.reset()
        canvas.translate(0F, (height - bottomHeight).toFloat())

        drawDashLine(canvas)
        if (!mappingData.isNullOrEmpty()) {
            drawGPAPath(canvas)
        }
        drawText(canvas)


        canvas.restore()
    }

    private fun drawGPAPath(canvas: Canvas) {
        path.moveTo(0F, -3F * segHeight)
        for (item in mappingData.withIndex()) {
            path.lineTo(segWidth * (item.index + 1F), -1 * segHeight * item.value)
        }
        path.lineTo(width.toFloat(), -1 * mappingData.last() * segHeight)
        pathBlue.addPath(path)

        path.lineTo(segWidth * 9F, 0F)
        path.lineTo(0F, 0F)
        path.close()

        gradientPaint.shader = linearGradient
        canvas.drawPath(path, gradientPaint)

        //画点
        gpaPaint.style = Paint.Style.FILL
        for (item in mappingData.withIndex()) {
            canvas.drawCircle((item.index + 1) * segWidth.toFloat(), -1 * item.value * segHeight.toFloat(), dp2px(5).toFloat(), gpaPaint)
        }
        gpaPaint.style = Paint.Style.STROKE
        canvas.drawPath(pathBlue, gpaPaint)

        //如果有touch 的点的话，画touch的点
        touchPoint?.let {
            if (mappingData.size >= it + 1) {
                //先画外面的蓝色
                gpaPaint.style = Paint.Style.FILL
                canvas.drawCircle((it + 1) * segWidth.toFloat(), -1 * mappingData[it] * segHeight.toFloat(), dp2px(8).toFloat(), gpaPaint)
                canvas.drawCircle((it + 1) * segWidth.toFloat(), -1 * mappingData[it] * segHeight.toFloat(), dp2px(4).toFloat(), whitePaint)
            }
        }

    }

    private fun drawDashLine(canvas: Canvas) {
        for (i in 0..3) {
            canvas.drawLine(0F, -1 * i * segHeight.toFloat(), width.toFloat(), -1 * i * segHeight.toFloat(), dashPaint)
        }

    }

    private fun drawText(canvas: Canvas) {

        textPaint.textSize = gpaTextSize.toFloat()
        for (item in textArray.withIndex()) {
            canvas.drawText(textArray[item.index], (item.index + 1) * segWidth.toFloat(), bottomHeight / 2F, textPaint)
        }


        touchPoint?.let {
            if (mappingData.size >= it + 1) {
                textPaint.textSize = dp2px(14).toFloat()
                canvas.drawBitmap(bubble, (it + 1) * segWidth.toFloat() - bubble.width / 2, -1 * mappingData[it] * segHeight.toFloat() - dp2px(24) - bubble.height / 2, textPaint)
                canvas.drawText(originalData[it], (it + 1) * segWidth.toFloat(), -1 * mappingData[it] * segHeight.toFloat() - dp2px(21), textPaint)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        segHeight = (h - bottomHeight - topHeight) / 4
        segWidth = w / 9

        linearGradient = LinearGradient(0F, 0F, 0F, -1F * h, gpaGradientBottomColor, gpaGradientTopColor, Shader.TileMode.REPEAT)
    }


    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {

        val event = event ?: return true
        val newPoint = (event.x / segWidth - 0.5).toInt()
        if (touchPoint != null && touchPoint == newPoint) {
            return true
        } else {
            touchPoint = (event.x / segWidth - 0.5).toInt()
            postInvalidate()
        }
        return true
    }

    /**
     * 以下是配合BottomSheet的事件分发部分
     */
    private var childNeed = false
    private var mLastX: Float? = null
    private var mLastY: Float? = null

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        val parent = parent ?: return super.dispatchTouchEvent(event)
        val event = event ?: return super.dispatchTouchEvent(event)

        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
            }
            MotionEvent.ACTION_MOVE -> {
                mLastX?.let { lastX ->
                    mLastY?.let { lastY ->
                        val deltaX = x - lastX
                        val deltaY = y - lastY
                        if (2 * abs(deltaY) - abs(deltaX) > 0.000001F && !childNeed) {
                            parent.requestDisallowInterceptTouchEvent(false)
                        } else if (2 * abs(deltaY) - abs(deltaX) <= 0.000001F) {
                            parent.requestDisallowInterceptTouchEvent(true)
                            childNeed = true
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                childNeed = false
            }
            MotionEvent.ACTION_CANCEL -> {
                childNeed = false
            }
        }
        mLastX = x
        mLastY = y
        return super.dispatchTouchEvent(event)
    }


    class DefaultMappingRule : GraphRule() {
        override fun mappingRule(old: String): Float {
            val temp = old.toFloatOrNull() ?: return 0F
            return temp / 2 + 1
        }

    }
}