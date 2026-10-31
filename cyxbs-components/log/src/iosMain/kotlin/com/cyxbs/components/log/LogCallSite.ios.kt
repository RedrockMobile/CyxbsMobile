package com.cyxbs.components.log

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
internal actual fun logCallSite(): String? {
  val frames = Throwable().getStackTrace()
  val loggerIndex = frames.indexOfLast { it.contains("com.cyxbs.components.log.CyxbsLog#") }
  if (loggerIndex < 0) return null
  return frames.asSequence().drop(loggerIndex + 1)
    .filterNot { it.contains("com.cyxbs.components.utils.extensions#log") }
    .firstNotNullOfOrNull(::sourceLocationFromFrame)
}
