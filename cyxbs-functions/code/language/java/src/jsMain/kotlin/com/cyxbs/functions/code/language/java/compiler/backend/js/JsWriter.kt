package com.cyxbs.functions.code.language.java.compiler.backend.js

import com.cyxbs.functions.code.language.java.compiler.JavaScriptSourceMapping
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceSpan

/**
 * 维护缩进和 UTF-16 行列的最小 JavaScript 文本写入器。
 *
 * 映射只记录每个有 Java 来源的生成片段起点；它不是完整 source map，但足以让阶段 0 把
 * QuickJS 的行列定位回原始 Java 区间，后续阶段可在不改 emitter 契约的前提下细化。
 */
internal class JsWriter {
  private val output = StringBuilder()
  private val recordedMappings = mutableListOf<JavaScriptSourceMapping>()
  private var indentation = 0
  private var line = 1
  private var column = 0

  /** 返回已写入源码；调用方应只在一次模块生成结束后读取。 */
  val source: String
    get() = output.toString()

  /** 返回按生成顺序记录的最小源码映射。 */
  val mappings: List<JavaScriptSourceMapping>
    get() = recordedMappings.toList()

  /** 写入不携带 Java 来源的生成文本。 */
  fun write(value: String) {
    output.append(value)
    value.forEach { character ->
      if (character == '\n') {
        line += 1
        column = 0
      } else {
        column += 1
      }
    }
  }

  /** 在当前生成位置登记 [span] 后写入对应 Java 片段。 */
  fun writeMapped(value: String, span: JavaSourceSpan) {
    recordedMappings += JavaScriptSourceMapping(
      generatedLine = line,
      generatedColumn = column,
      sourceSpan = span,
    )
    write(value)
  }

  /** 写入当前缩进和一行可选内容。 */
  fun line(value: String = "") {
    writeIndentation()
    write(value)
    write("\n")
  }

  /** 写入当前行的缩进，但不附加换行，适合由 emitter 继续写入带 source mapping 的片段。 */
  fun writeIndentation() {
    repeat(indentation) { write("  ") }
  }

  /** 在一个缩进层内执行 [block]，即使 block 抛出也恢复先前状态。 */
  inline fun indented(block: () -> Unit) {
    indentation += 1
    try {
      block()
    } finally {
      indentation -= 1
    }
  }

  /** 返回可安全嵌入 ES Module 的双引号字符串字面量。 */
  fun stringLiteral(value: String): String = buildString {
    append('"')
    value.forEach { character ->
      when (character) {
        '\\' -> append("\\\\")
        '"' -> append("\\\"")
        '\b' -> append("\\b")
        '\u000C' -> append("\\f")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        '\u2028', '\u2029' -> appendUnicodeEscape(character)
        else -> {
          if (character.code < 0x20) appendUnicodeEscape(character) else append(character)
        }
      }
    }
    append('"')
  }

  /** 以固定四位十六进制形式转义控制字符和 JS 行分隔符。 */
  private fun StringBuilder.appendUnicodeEscape(character: Char) {
    append("\\u")
    append(character.code.toString(16).padStart(4, '0'))
  }
}
