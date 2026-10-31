package com.cyxbs.components.log

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
internal actual fun Throwable.logStackFrames(): LogStackFrames {
  val frames = getStackTrace()
  return LogStackFrames(frames.take(MAX_STACK_FRAMES), frames.size > MAX_STACK_FRAMES)
}
