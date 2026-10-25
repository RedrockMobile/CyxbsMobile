package com.cyxbs.pages.widget.page

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.cyxbs.components.utils.extensions.toast

/** 接收桌面真正固定小组件后的系统回调，避免在确认弹窗前过早提示。 */
class CourseWidgetPinSuccessReceiver : BroadcastReceiver() {

  /** 回调可能在应用退到后台后到达，此时使用系统文本 Toast 才能在桌面上显示。 */
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != ACTION_PIN_SUCCEEDED) return
    toast("已添加到桌面")
  }

  companion object {
    const val ACTION_PIN_SUCCEEDED = "com.cyxbs.pages.widget.action.PIN_SUCCEEDED"
  }
}
