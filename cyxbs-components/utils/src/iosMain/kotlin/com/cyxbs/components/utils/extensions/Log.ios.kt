package com.cyxbs.components.utils.extensions

import platform.Foundation.NSLog

/**
 * .
 *
 * @author 985892345
 * @date 2024/1/23 10:22
 */
actual fun log(msg: String) {
  NSLog(msg)
}

actual fun log(tag: String, msg: String) {
  NSLog("【$tag】$msg")
}