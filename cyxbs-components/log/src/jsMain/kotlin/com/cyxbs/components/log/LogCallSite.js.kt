package com.cyxbs.components.log

private val loggerFrame = Regex("""\b(?:logInternal|log|[vdiwea]|eBounded)(?:_[\w$]+)?\s*[(@]""")

internal actual fun logCallSite(): String? {
  val stack = Throwable().asDynamic().stack as? String ?: return null
  val frames = stack.lineSequence().toList()
  val loggerIndex = frames.indexOfLast { loggerFrame.containsMatchIn(it) }
  if (loggerIndex < 0) return null
  return frames.asSequence().drop(loggerIndex + 1).firstNotNullOfOrNull(::sourceLocationFromFrame)
}
