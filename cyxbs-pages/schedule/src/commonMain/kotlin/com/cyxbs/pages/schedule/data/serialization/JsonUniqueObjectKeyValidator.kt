package com.cyxbs.pages.schedule.data.serialization

import kotlinx.serialization.SerializationException

/**
 * 在 JSON 进入 kotlinx.serialization 前递归校验每个对象的字段名唯一，并拒绝未配对的 UTF-16 surrogate。
 *
 * kotlinx.serialization 的树模型只保留重复字段中的最后一个值，且不同平台对 lone surrogate 的替换字节不同，
 * 因此安全边界不能先解析为 JsonObject 再检查。本校验器直接遍历原始 token 流，对所有 key/value 字符串验证
 * surrogate，并对字段名完整反转义后比较；其余完整语法合法性仍交给调用方的 strict Json。
 */
internal object JsonUniqueObjectKeyValidator {

  /** 拒绝根值任意深度（包括数组元素）中的重复对象字段与未配对 surrogate。 */
  fun rejectDuplicates(source: String) {
    Scanner(source).scan()
  }

  /** 只消费原始文本、不构造丢失字段信息的 JSON 树。 */
  private class Scanner(private val source: String) {
    private var index = 0

    fun scan() {
      skipWhitespace()
      scanValue()
      skipWhitespace()
    }

    private fun scanValue() {
      skipWhitespace()
      when (source.getOrNull(index)) {
        '{' -> scanObject()
        '[' -> scanArray()
        '"' -> readString()
        else -> while (index < source.length && source[index] !in charArrayOf(
            ',',
            '}',
            ']'
          )
        ) index++
      }
    }

    private fun scanObject() {
      index++
      val names = mutableSetOf<String>()
      skipWhitespace()
      if (source.getOrNull(index) == '}') {
        index++
        return
      }
      while (index < source.length) {
        skipWhitespace()
        if (source.getOrNull(index) != '"') return
        val name = readString()
        if (!names.add(name)) throw SerializationException("Duplicate JSON object field: $name")
        skipWhitespace()
        if (source.getOrNull(index) != ':') return
        index++
        scanValue()
        skipWhitespace()
        when (source.getOrNull(index)) {
          ',' -> index++
          '}' -> {
            index++
            return
          }

          else -> return
        }
      }
    }

    private fun scanArray() {
      index++
      skipWhitespace()
      if (source.getOrNull(index) == ']') {
        index++
        return
      }
      while (index < source.length) {
        scanValue()
        skipWhitespace()
        when (source.getOrNull(index)) {
          ',' -> index++
          ']' -> {
            index++
            return
          }

          else -> return
        }
      }
    }

    /** 读取 key/value 字符串，并在平台 JSON parser 归一化前拒绝未配对 surrogate。 */
    private fun readString(): String {
      val result = StringBuilder()
      index++
      while (index < source.length) {
        when (val char = source[index++]) {
          '"' -> return result.toString()
          '\\' -> {
            if (index >= source.length) return result.toString()
            when (val escaped = source[index++]) {
              '"', '\\', '/' -> result.append(escaped)
              'b' -> result.append('\b')
              'f' -> result.append('')
              'n' -> result.append('\n')
              'r' -> result.append('\r')
              't' -> result.append('\t')
              'u' -> appendUnicodeEscape(result)
              else -> result.append(escaped)
            }
          }

          else -> when (char.code) {
            in HIGH_SURROGATE_RANGE -> {
              val low = source.getOrNull(index)
              if (low == null || low.code !in LOW_SURROGATE_RANGE) rejectUnpairedSurrogate()
              result.append(char).append(low)
              index++
            }

            in LOW_SURROGATE_RANGE -> rejectUnpairedSurrogate()
            else -> result.append(char)
          }
        }
      }
      return result.toString()
    }

    /** `\\u` high surrogate 只接受紧邻的第二个 `\\u` low surrogate；转义反斜杠不会进入此分支。 */
    private fun appendUnicodeEscape(result: StringBuilder) {
      if (index + 4 > source.length) return
      val code = source.substring(index, index + 4).toIntOrNull(16) ?: return
      index += 4
      when (code) {
        in HIGH_SURROGATE_RANGE -> {
          if (index + 6 > source.length || source[index] != '\\' || source[index + 1] != 'u') {
            rejectUnpairedSurrogate()
          }
          val low = source.substring(index + 2, index + 6).toIntOrNull(16)
            ?: rejectUnpairedSurrogate()
          if (low !in LOW_SURROGATE_RANGE) rejectUnpairedSurrogate()
          result.append(code.toChar()).append(low.toChar())
          index += 6
        }

        in LOW_SURROGATE_RANGE -> rejectUnpairedSurrogate()
        else -> result.append(code.toChar())
      }
    }

    private fun rejectUnpairedSurrogate(): Nothing =
      throw SerializationException("JSON string contains an unpaired UTF-16 surrogate")

    private fun skipWhitespace() {
      while (index < source.length && source[index].isWhitespace()) index++
    }

    private companion object {
      val HIGH_SURROGATE_RANGE = 0xD800..0xDBFF
      val LOW_SURROGATE_RANGE = 0xDC00..0xDFFF
    }
  }
}
