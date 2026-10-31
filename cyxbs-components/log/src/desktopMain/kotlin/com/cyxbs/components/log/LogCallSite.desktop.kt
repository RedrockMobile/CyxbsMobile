package com.cyxbs.components.log

internal actual fun logCallSite(): String? {
  val frames = Throwable().stackTrace
  val loggerClass = CyxbsLog::class.java.name
  val loggerIndex = frames.indexOfLast { it.className == loggerClass }
  if (loggerIndex < 0) return null
  val caller = frames.asSequence()
    .drop(loggerIndex + 1)
    .firstOrNull {
      it.className != "com.cyxbs.components.utils.extensions.LogKt" &&
        it.className != "com.cyxbs.components.utils.utils.LogUtils" &&
        it.fileName != null && it.lineNumber > 0
    }
  return caller?.let {
    val fileName = it.fileName!!.substringAfterLast('/').substringAfterLast('\\')
    "$fileName:${it.lineNumber}"
  }
}
