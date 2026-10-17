package com.cyxbs.components.log

internal actual fun Throwable.logStackFrames(): LogStackFrames {
  val frames = stackTrace
  return LogStackFrames(frames.take(MAX_STACK_FRAMES).map { it.toString() }, frames.size > MAX_STACK_FRAMES)
}
