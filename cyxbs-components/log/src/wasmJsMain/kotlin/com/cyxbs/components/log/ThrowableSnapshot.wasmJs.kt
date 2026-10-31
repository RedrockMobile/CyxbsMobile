package com.cyxbs.components.log

// Wasm 没有公开的有界堆栈帧 API；保留有界异常树摘要，并标记堆栈缺口。
internal actual fun Throwable.logStackFrames(): LogStackFrames = LogStackFrames(emptyList(), truncated = true)
