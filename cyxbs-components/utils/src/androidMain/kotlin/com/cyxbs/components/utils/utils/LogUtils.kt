package com.cyxbs.components.utils.utils

import com.cyxbs.components.log.CyxbsLog

/** Compatibility facade for Android call sites that have not migrated to [CyxbsLog]. */
object LogUtils {
  fun v(tag: String = "tag", msg: String, throwable: Throwable? = null) {
    CyxbsLog.v(tag, msg, throwable)
  }

  fun d(tag: String = "tag", msg: String, throwable: Throwable? = null) {
    CyxbsLog.d(tag, msg, throwable)
  }

  fun i(tag: String = "tag", msg: String, throwable: Throwable? = null) {
    CyxbsLog.i(tag, msg, throwable)
  }

  fun w(tag: String = "tag", msg: String, throwable: Throwable? = null) {
    CyxbsLog.w(tag, msg, throwable)
  }

  fun e(tag: String = "tag", msg: String, throwable: Throwable? = null) {
    CyxbsLog.e(tag, msg, throwable)
  }
}
