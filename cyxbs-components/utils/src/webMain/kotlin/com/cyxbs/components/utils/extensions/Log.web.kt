package com.cyxbs.components.utils.extensions

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js

/**
 * .
 *
 * @author 985892345
 * @date 2024/1/23 10:22
 */
@OptIn(ExperimentalWasmJsInterop::class)
actual fun log(msg: String) {
  js("console.log(msg)")
}

actual fun log(tag: String, msg: String) {
  log("【$tag】$msg")
}