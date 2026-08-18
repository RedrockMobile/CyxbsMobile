package com.cyxbs.functions.code.language.java.compiler

/**
 * 生成可复现的 Java 畸形源码语料。
 *
 * 每个样本执行 1～4 次删除、插入、替换、截断或片段复制，并混入括号、注释、NUL 与损坏的
 * Unicode 转义文本。固定线性同余种子保证 Node、CI 和后续平台测试能够用样本序号精确复现。
 *
 * @param base 作为扰动起点的合法 Java 源码，不能为空。
 * @param count 需要生成的样本数，必须大于零。
 */
internal fun deterministicMalformedJavaSources(
  base: String,
  count: Int,
): List<String> {
  require(base.isNotEmpty()) { "base must not be empty." }
  require(count > 0) { "count must be positive." }
  val adversarialTokens = listOf(
    "{",
    "}",
    ")",
    "\"",
    "'",
    "/*",
    "*/",
    "//",
    "@",
    "?",
    "\u0000",
    "\\uD83D",
    "\\uDC00",
    "class",
    "<T extends",
  )
  var seed = 0x5EED1234
  return List(count) { sample ->
    var source = base
    repeat(1 + sample % 4) { mutation ->
      seed = seed * 1_103_515_245 + 12_345
      val position = (seed ushr 1) % (source.length + 1)
      val token = adversarialTokens[(sample + mutation + (seed ushr 8)) % adversarialTokens.size]
      source = when ((seed ushr 16) % 5) {
        0 -> if (source.isEmpty()) {
          token
        } else {
          val from = position.coerceAtMost(source.lastIndex)
          source.removeRange(from, from + 1)
        }
        1 -> source.substring(0, position) + token + source.substring(position)
        2 -> if (source.isEmpty()) {
          token
        } else {
          val from = position.coerceAtMost(source.lastIndex)
          source.replaceRange(from, from + 1, token)
        }
        3 -> source.substring(0, position)
        else -> if (source.isEmpty()) {
          token
        } else {
          val from = position.coerceAtMost(source.lastIndex)
          val to = (from + 1 + sample % 8).coerceAtMost(source.length)
          source.substring(0, position) + source.substring(from, to) + source.substring(position)
        }
      }
    }
    source
  }
}
