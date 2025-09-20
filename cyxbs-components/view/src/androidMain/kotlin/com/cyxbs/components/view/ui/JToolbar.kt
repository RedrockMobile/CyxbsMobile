package com.cyxbs.components.view.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.cyxbs.components.config.R

/**
 * 掌邮内统一的 Toolbar，如果视觉给出其他样式，请自己单独实现
 *
 * 常用：
 * [init]              初始化
 *
 * 自定义方法：
 * [withSplitLine]                  是否绘制分割线
 * [setTitleLocationAtLeft]         设置 title 是否左对齐，否则居中
 *
 * Toolbar 原有方法：
 * [setTitle]                       设置标题
 * [setSubtitle]                    设置副标题
 * [setNavigationIcon]              设置图标
 * [setNavigationOnClickListener]   设置图标的点击监听
 * [setTitleTextColor]
 * [setBackgroundColor]
 * 等其他常见方法
 *
 * ## 使用方式为：
 * ```
 * 在 XML 中合适的位置引入
 * <include
 *     layout="@layout/view_toolbar" /> // 注意这里是 view_ 不是 common_ !
 *
 *
 * 在代码处：
 * val toolbar = findViewById<JToolbar>(R.id.toolbar)
 * toolbar.init(this, "标题") // 还有其他默认参数
 * ```
 *
 * 标题居中显示的toolbar
 * Created by Jay on 2017/9/20.
 *
 * @author 985892345（修改为 kt）
 * 2023/3/1 19:54
 */
class JToolbar(context: Context, attrs: AttributeSet?) : Toolbar(context, attrs) {
  
  /**
   * 在 Activity 中初始化设置
   * @param title 标题
   * @param withSplitLine 是否加下划线
   * @param icon 图标，默认图标为返回图标
   * @param clickIcon 点击图标的监听，默认为 finishAfterTransition()
   * @param titleOnLeft title 是否左对齐，否则居中
   */
  fun init(
    activity: AppCompatActivity,
    title: String,
    withSplitLine: Boolean = true,
    @DrawableRes
    icon: Int = R.drawable.config_ic_back,
    titleOnLeft: Boolean = true,
    clickIcon: OnClickListener? = OnClickListener { activity.finishAfterTransition() }
  ) {
    this.title = title
    withSplitLine(withSplitLine)
    activity.setSupportActionBar(this@JToolbar)
    setTitleLocationAtLeft(titleOnLeft)
    if (clickIcon == null) {
      navigationIcon = null
    } else {
      setNavigationIcon(icon)
      setNavigationOnClickListener(clickIcon)
    }
  }
  
  /**
   * 设置 title 是否左对齐，否则居中
   */
  fun setTitleLocationAtLeft(isLeft: Boolean) {
    mIsTitleAtLeft = isLeft
    requestLayout()
  }
  
  /**
   * 是否绘制分割线
   */
  fun withSplitLine(withSplitLine: Boolean) {
    mWithSplitLine = withSplitLine
    invalidate()
  }

  /**
   * 添加右侧图标
   *
   * 点击事件可拿到返回的 View 进行添加
   */
  fun setRightIcon(
    @DrawableRes
    icon: Int,
    width: Int = ViewGroup.LayoutParams.WRAP_CONTENT, // 单位 px
    height: Int = ViewGroup.LayoutParams.WRAP_CONTENT, // 单位 px
  ) : ImageButton {
    if (mIbRightIcon != null) {
      throw IllegalStateException("已经设置，不允许再次调用")
    }
    return ImageButton(context).also { button ->
      mIbRightIcon = button
      button.background = AppCompatResources.getDrawable(context, icon)!!
      button.layoutParams = LayoutParams(width, height, Gravity.CENTER_VERTICAL or Gravity.END).also {
        it.marginEnd = 16.dp2pxF.toInt()
      }
      addView(button)
    }
  }

  private var mSplitLinePaint = Paint().apply {
    color = ContextCompat.getColor(context, R.color.config_default_divide_line_color)
    strokeWidth = 1.dp2pxF
  }

  private var mTitleTextView: TextView? = null
  private var mSubtitleTextView: TextView? = null
  private var mIbRightIcon: ImageButton? = null
  
  private var mIsTitleAtLeft = true
  private var mWithSplitLine = true
  
  override fun setTitle(title: CharSequence?) {
    super.setTitle(title)
    if (mTitleTextView == null && title != null) {
      for (i in 0 until childCount) {
        if (tryInitTitleTextView(getChildAt(i))) {
          break
        }
      }
    }
  }
  
  override fun setSubtitle(subtitle: CharSequence?) {
    super.setSubtitle(subtitle)
    if (mSubtitleTextView == null && subtitle != null) {
      for (i in 0 until childCount) {
        if (tryInitSubtitleTextView(getChildAt(i))) {
          break
        }
      }
    }
  }
  
  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    super.onLayout(changed, l, t, r, b)
    if (mIsTitleAtLeft) {
      reLayoutTitleToLeft(mTitleTextView)
      reLayoutTitleToLeft(mSubtitleTextView)
    } else {
      reLayoutTitleToCenter(mTitleTextView)
      reLayoutTitleToCenter(mSubtitleTextView)
    }
  }
  
  private fun reLayoutTitleToLeft(title: TextView?) {
    if (title == null) return
    val ir = getChildAt(0).right
    title.layout(ir, title.top, ir + title.measuredWidth, title.bottom)
  }
  
  private fun reLayoutTitleToCenter(title: TextView?) {
    if (title == null) return
    //note: o for old ,t for temp, l for left...
    val ol = title.left
    val width = title.measuredWidth
    val tl = (measuredWidth - width) / 2
    if (tl > ol) {
      title.layout(tl, title.top, tl + width, title.bottom)
    }
  }
  
  override fun dispatchDraw(canvas: Canvas) {
    super.dispatchDraw(canvas)
    if (mWithSplitLine) {
      val y = height - 0.5F
      canvas.drawLine(0F, y, width.toFloat(), y, mSplitLinePaint)
    }
  }
  
  private val Int.dp2pxF
    get() = context.resources.displayMetrics.density * this
  
  override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) {
    super.addView(child, index, params)
    // 经过看源码发现，存在 Toolbar 先把 mTitleTextView 放在 mHiddenViews 中的情况，所以这里只是做一个二重保障
    if (mTitleTextView == null) {
      if (!tryInitSubtitleTextView(child)) {
        if (mSubtitleTextView == null) {
          tryInitSubtitleTextView(child)
        }
      }
    }
  }
  
  private fun tryInitTitleTextView(child: View?): Boolean {
    if (child is TextView && child.text == title) {
      mTitleTextView = child
      child.paint.isFakeBoldText = true
      child.setTextColor(ContextCompat.getColor(context, R.color.config_level_two_font_color))
      return true
    }
    return false
  }
  
  private fun tryInitSubtitleTextView(child: View?): Boolean {
    if (child is TextView && child.text == subtitle) {
      mSubtitleTextView = child
      child.paint.isFakeBoldText = true
      child.setTextColor(ContextCompat.getColor(context, R.color.config_level_two_font_color))
      return true
    }
    return false
  }
}