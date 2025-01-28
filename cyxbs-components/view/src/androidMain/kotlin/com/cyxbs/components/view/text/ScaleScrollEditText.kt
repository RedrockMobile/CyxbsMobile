package com.cyxbs.components.view.text

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView

/**
 * 一个用于带有左侧行号的文本编辑控件，并且支持双指放大缩小
 *
 * @author 985892345
 * @date 2023/10/26 21:41
 */
class ScaleScrollEditText(
  context: Context,
  attrs: AttributeSet?
) : ScaleScrollTextView(context, attrs) {

  private val mLongPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

  override fun createContentView(context: Context): TextView {
    return EditText(context).apply {
      background = null
      gravity = Gravity.TOP
      addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        }

        override fun afterTextChanged(s: Editable) {
          adjustSlideLine()
        }
      })
    }
  }

  private var mEnableFocusEditText = true

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(event: MotionEvent): Boolean {
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        mEnableFocusEditText = true
      }
      MotionEvent.ACTION_POINTER_DOWN -> {
        mEnableFocusEditText = false
      }
      MotionEvent.ACTION_UP -> {
        if (mEnableFocusEditText && event.eventTime - event.downTime < mLongPressTimeout) {
          mTvContent.requestFocus()
          context.getSystemService(InputMethodManager::class.java)
            .showSoftInput(mTvContent, 0)
        }
      }
    }
    return super.onTouchEvent(event)
  }
}