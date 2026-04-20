package com.cyxbs.components.utils.utils

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js

actual object VibratorUtil {
  actual fun longPress() {
    vibrate()
  }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun vibrate() {
  js("if ('vibrate' in window.navigator) { window.navigator.vibrate(36); }")
}