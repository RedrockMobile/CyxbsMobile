package com.cyxbs.functions.code.npm.internal

/** 对 npm 包坐标使用的 URL path segment 做 UTF-8 percent encoding。 */
internal fun String.encodeNpmPathSegment(): String = buildString {
  this@encodeNpmPathSegment.encodeToByteArray().forEach { byte ->
    val value = byte.toInt() and 0xff
    val isUnreserved =
      value in 'a'.code..'z'.code ||
        value in 'A'.code..'Z'.code ||
        value in '0'.code..'9'.code ||
        value == '-'.code ||
        value == '.'.code ||
        value == '_'.code ||
        value == '~'.code
    if (isUnreserved) {
      append(value.toChar())
    } else {
      append('%')
      append(HEX[value ushr 4])
      append(HEX[value and 0x0f])
    }
  }
}

private const val HEX = "0123456789ABCDEF"
