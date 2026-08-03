package com.cyxbs.functions.code.npm.internal

/**
 * 根据统一 npm 下载源规则生成精确版本 tarball 地址。
 *
 * [baseUrl] 已由快照解析器校验为不含 query、fragment 和用户信息的 HTTPS 基础地址。
 */
internal fun buildNpmPackageDownloadUrl(
  baseUrl: String,
  packageName: String,
  version: String,
): String {
  return buildString {
    append(baseUrl.trimEnd('/'))
    append('/')
    append(packageName.encodeNpmPathSegment())
    append('/')
    append(version.encodeNpmPathSegment())
    append(".tgz")
  }
}

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
