package com.cyxbs.components.utils.extensions

import com.cyxbs.components.log.CyxbsLog
import com.cyxbs.components.log.LogLevel

fun log(msg: String) {
  CyxbsLog.d("ggg", msg, showLocation = true)
}

fun log(tag: String, msg: String) {
  CyxbsLog.d(tag, msg, showLocation = true)
}

fun log(level: LogLevel, tag: String, msg: String) {
  CyxbsLog.log(level, tag, msg, showLocation = true)
}

fun logg(msg: Any?) {
  log(msg.toString())
}
