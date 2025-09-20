package com.cyxbs.components.utils.extensions

/**
 * .
 *
 * @author 985892345
 * @date 2024/1/23 10:22
 */

expect fun log(msg: String)

expect fun log(tag: String, msg: String)

fun logg(msg: Any?) {
  log(msg.toString())
}