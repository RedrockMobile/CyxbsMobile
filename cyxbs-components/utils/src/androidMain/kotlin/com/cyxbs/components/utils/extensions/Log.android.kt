package com.cyxbs.components.utils.extensions

import android.util.Log

/**
 * .
 *
 * @author 985892345
 * @date 2024/1/23 10:22
 */
actual fun log(msg: String) {
  Log.d("ggg", "(${Exception().stackTrace[2].run { "$fileName:$lineNumber" }}) -> $msg")
}

actual fun log(tag: String, msg: String) {
  Log.d(tag, "(${Exception().stackTrace[2].run { "$fileName:$lineNumber" }}) -> $msg")
}