package com.cyxbs.components.utils.extensions

/**
 * .
 *
 * @author 985892345
 * @date 2024/1/23 10:22
 */
actual fun log(msg: String) {
  println(".(${Exception().stackTrace[2].run { "$fileName:$lineNumber" }}) -> $msg")
}

actual fun log(tag: String, msg: String) {
  println("【$tag】.(${Exception().stackTrace[2].run { "$fileName:$lineNumber" }}) -> $msg")
}