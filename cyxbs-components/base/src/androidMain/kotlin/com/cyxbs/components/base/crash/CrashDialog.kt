package com.cyxbs.components.base.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.cyxbs.components.init.appTopActivity
import com.cyxbs.components.view.text.ScaleScrollTextView
import com.cyxbs.components.base.dailog.ChooseDialog
import com.cyxbs.components.utils.extensions.collectUsefulStackTrace
import com.cyxbs.components.utils.extensions.color
import com.cyxbs.components.utils.extensions.dp2px
import com.cyxbs.components.utils.extensions.setOnSingleClickListener
import com.cyxbs.components.utils.utils.Jump2QQHelper

/**
 * 用于展示崩溃日志的 dialog
 * ```
 * CrashDialog.Builder(throwable).show()
 * ```
 *
 * @author 985892345
 * 2023/3/1 10:29
 */
class CrashDialog private constructor(
  context: Context
) : ChooseDialog(context) {
  
  class Builder(
    throwable: Throwable
  ) : ChooseDialog.Builder(
    context = appTopActivity.get()!!,
    DataImpl(
      content = throwable.collectUsefulStackTrace(),
      positiveButtonText = "复制信息",
      negativeButtonText = "关闭",
      buttonWidth = 110,
      buttonHeight = 38,
      width = 320,
      height = 500
    ),
  ) {
    override fun buildInternal(): CrashDialog {
      setPositiveClick {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("掌邮崩溃记录", data.content))
        toast("复制成功！")
      }.setNegativeClick {
        dismiss()
      }
      return CrashDialog(context)
    }

    override fun show() {
      if (Looper.getMainLooper().isCurrentThread) {
        super.show()
      } else {
        Handler(Looper.getMainLooper()).post {
          super.show()
        }
      }
    }
  }
  
  private val mTvExceptionMessage = TextView(context).apply {
    layoutParams = LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply {
      leftMargin = 10.dp2px
    }
    textSize = 12F
    setTextIsSelectable(true)
  }
  
  private val mScaleScrollTextView = ScaleScrollTextView(context, null).apply {
    layoutParams = LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.MATCH_PARENT,
    ).apply {
      topMargin = 10.dp2px
    }
    setSideBackgroundColor(com.cyxbs.components.config.R.color.config_dialog_bg.color)
  }
  
  private val mLinearLayout = LinearLayout(context).apply {
    layoutParams = FrameLayout.LayoutParams(
      FrameLayout.LayoutParams.MATCH_PARENT,
      FrameLayout.LayoutParams.MATCH_PARENT,
      Gravity.CENTER
    ).apply {
      topMargin = 15.dp2px
      bottomMargin = topMargin
      leftMargin = 8.dp2px
      rightMargin = 16.dp2px
    }
    orientation = LinearLayout.VERTICAL
    addView(mTvExceptionMessage)
    addView(mScaleScrollTextView)
  }
  
  override fun createContentView(parent: FrameLayout): View {
    return mLinearLayout
  }
  
  override fun initContentView(view: View) {
    // 给 .kt 标红
    val builder = SpannableStringBuilder(data.content)
    val regex = Regex("(?<=.{1,999})\\(\\w+\\.kt:\\d+\\)")
    val result = regex.findAll(builder)
    result.forEach {
      builder.setSpan(
        ForegroundColorSpan(Color.RED),
        it.range.first,
        it.range.last + 1,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
      )
    }
    val title = "异常信息（双指支持放大缩小）\n截图请向左滑动，确保能看到前几行红色的文字结尾数字\n"
    builder.insert(0, title)
    builder.setSpan(
      ForegroundColorSpan(0xFFFF8800.toInt()),
      0,
      title.length,
      Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    mScaleScrollTextView.text = builder
    val length = data.content.indexOf('\n').let { if (it == -1) data.content.length else it }
    mTvExceptionMessage.text = data.content.substring(0, length) // 只显示第一行的 message
  }

  override fun createBottomView(parent: FrameLayout): View {
    return LinearLayout(parent.context).apply {
      layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.WRAP_CONTENT,
        FrameLayout.LayoutParams.WRAP_CONTENT,
        Gravity.CENTER,
      )
      orientation = LinearLayout.HORIZONTAL
      addView(TextView(parent.context).apply {
        text = "点击跳转QQ反馈群："
        textSize = 10F
        setTextColor(0xFFABBCDB.toInt())
      })
      addView(TextView(parent.context).apply {
        text = Jump2QQHelper.FEED_BACK_QQ_GROUP
        textSize = 10F
        setTextColor(0xFF0BCCF0.toInt())
      })
      setOnSingleClickListener {
        Jump2QQHelper.onFeedBackClick()
      }
    }
  }
}