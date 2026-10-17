package com.cyxbs.components.utils.extensions

import com.cyxbs.components.log.CyxbsLog

/**
 * Compatibility entry point for legacy call sites.
 *
 * New code should call [CyxbsLog] directly so the severity and throwable stay explicit.
 */
fun log(msg: String) {
  CyxbsLog.d(message = msg)
}

/** Compatibility entry point for legacy tagged debug logs. */
fun log(tag: String, msg: String) {
  CyxbsLog.d(tag, msg)
}

fun logg(msg: Any?) {
  CyxbsLog.d(message = msg.toString())
}
