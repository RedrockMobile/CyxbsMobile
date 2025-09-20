package com.cyxbs.components.view.text

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * 重写 isFocused() 实现官方的跑马灯效果
 *
 * 具体可以看这篇文章：https://cloud.tencent.com/developer/article/1591880
 *
 * 经测试，在 xml 中写无效，所以使用重写方法来实现
 *
 * @author 985892345 (Guo Xiangrui)
 * @email guo985892345@foxmail.com
 * @date 2022/9/15 22:23
 */
class MarqueeTextView(
  context: Context,
  attrs: AttributeSet?
) : AppCompatTextView(context, attrs) {
  
  init {
    ellipsize = TextUtils.TruncateAt.MARQUEE
    marqueeRepeatLimit = -1
    setSingleLine()
  }
  
  override fun isFocused(): Boolean {
    return true
  }
}