package com.cyxbs.components.log

internal actual fun Throwable.logStackFrames(): LogStackFrames {
  val stack = asDynamic().stack as? String ?: return LogStackFrames(emptyList(), truncated = true)
  val snapshot = stack.takeRawFileSnapshot()
  // JS Error.stack 的首行为异常摘要，已由公共渲染器输出。
  val frames = snapshot.value.lineSequence().drop(1).take(MAX_STACK_FRAMES + 1).toList()
  return LogStackFrames(frames.take(MAX_STACK_FRAMES), snapshot.truncated || frames.size > MAX_STACK_FRAMES)
}
