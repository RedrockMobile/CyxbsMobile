package com.cyxbs.components.log

internal expect fun logCallSite(): String?

// 只保留文件名和行号，避免把构建机路径写入日志。
private val sourceLocation = Regex("""([^/\\():\s]+\.(?:kt|kts|java|swift|js|mjs)):(\d+)(?::\d+)?""")

internal fun sourceLocationFromFrame(frame: String): String? =
  sourceLocation.find(frame)?.let { "${it.groupValues[1]}:${it.groupValues[2]}" }

internal fun messageWithCallSite(message: String, callSite: String?): String =
  "${callSite ?: "Unknown:0"} -> $message"
