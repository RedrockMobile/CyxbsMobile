package com.cyxbs.components.log

// Wasm 公共异常 API 没有可读取的调用帧信息。
internal actual fun logCallSite(): String? = null
