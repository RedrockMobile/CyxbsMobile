package com.cyxbs.components.utils.utils

import com.cyxbs.components.log.CyxbsLog

/** Compatibility facade for Android call sites that have not migrated to [CyxbsLog]. */
object LogUtils {
  fun v(tag: String = "tag", msg: String, throwable: Throwable? = null) {
    CyxbsLog.v(tag, throwable) { msg }
  }

  fun d(tag: String = "tag", msg: String, throwable: Throwable? = null) {
    CyxbsLog.d(tag, throwable) { msg }
  }

  fun i(tag: String = "tag", msg: String, throwable: Throwable? = null) {
    CyxbsLog.i(tag, throwable) { msg }
  }

  fun w(tag: String = "tag", msg: String, throwable: Throwable? = null) {
    CyxbsLog.w(tag, throwable) { msg }
  }

  fun e(tag: String = "tag", msg: String, throwable: Throwable? = null) {
    CyxbsLog.e(tag, throwable) { msg }
  }
}
