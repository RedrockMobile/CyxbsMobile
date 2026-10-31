package com.cyxbs.components.log

private const val MAX_THROWABLE_CHARS = 16 * 1_024
private const val MAX_THROWABLES = 8
private const val MAX_CAUSE_DEPTH = 4
private const val MAX_SUPPRESSED = 4
internal const val MAX_STACK_FRAMES = 32

internal data class LogStackFrames(val frames: List<String>, val truncated: Boolean)

internal expect fun Throwable.logStackFrames(): LogStackFrames

/** 在遍历期间限制异常树和文本；不调用异常可覆写的 toString/printStackTrace。 */
internal fun Throwable.boundedThrowableSnapshot(): FileTextSnapshot {
  val text = StringBuilder()
  val visited = mutableListOf<Throwable>()
  var truncated = false

  fun append(value: String) {
    val remaining = MAX_THROWABLE_CHARS - text.length
    var end = minOf(value.length, remaining)
    if (end < value.length && end > 0 && value[end - 1].isHighSurrogate()) end -= 1
    text.append(value, 0, end)
    if (end < value.length) truncated = true
  }

  fun visit(error: Throwable, depth: Int, label: String) {
    if (text.length >= MAX_THROWABLE_CHARS || depth >= MAX_CAUSE_DEPTH || visited.size >= MAX_THROWABLES) {
      truncated = true
      return
    }
    if (visited.any { it === error }) {
      append("\n[circular exception]")
      truncated = true
      return
    }
    visited += error
    append(label)
    append(error::class.simpleName ?: "Throwable")
    error.message?.let { append(": "); append(it) }
    if (text.length >= MAX_THROWABLE_CHARS) {
      truncated = true
      return
    }
    val stack = error.logStackFrames()
    truncated = truncated || stack.truncated
    for (frame in stack.frames) {
      append("\n\tat ")
      append(frame)
      if (text.length >= MAX_THROWABLE_CHARS) break
    }
    if (text.length >= MAX_THROWABLE_CHARS) {
      truncated = true
      return
    }
    val suppressed = error.suppressedExceptions
    if (suppressed.size > MAX_SUPPRESSED) truncated = true
    for (index in 0 until minOf(suppressed.size, MAX_SUPPRESSED)) {
      if (text.length >= MAX_THROWABLE_CHARS || visited.size >= MAX_THROWABLES) {
        truncated = true
        return
      }
      visit(suppressed[index], depth + 1, "\nSuppressed: ")
    }
    if (text.length >= MAX_THROWABLE_CHARS || visited.size >= MAX_THROWABLES) {
      truncated = true
      return
    }
    error.cause?.let { visit(it, depth + 1, "\nCaused by: ") }
  }

  visit(this, 0, "")
  return FileTextSnapshot(text.toString(), truncated)
}
