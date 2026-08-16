package com.cyxbs.functions.code.language.java.compiler.backend.js

/**
 * 阶段 0 Java 语义所需的最小 JavaScript 运行时。
 *
 * 所有整数 helper 都把结果收敛为有符号 32 位值，避免 JavaScript Number 的浮点语义泄漏到
 * Java int 算术；long/BigInt 由后续阶段单独实现，当前后端会在生成前拒绝。
 */
internal object JavaRuntimePrelude {
  /** 供 [JavaScriptEmitter] 写入每个模块开头的辅助函数源码。 */
  val source: String = """
    function @__j_int_div(left, right) {
      left |= 0;
      right |= 0;
      if (right === 0) {
        throw new Error("java.lang.ArithmeticException: / by zero");
      }
      return (left / right) | 0;
    }

    function @__j_int_rem(left, right) {
      left |= 0;
      right |= 0;
      if (right === 0) {
        throw new Error("java.lang.ArithmeticException: / by zero");
      }
      return (left - @__j_int_div(left, right) * right) | 0;
    }
  """.trimIndent().replace('@', '$')

  /** 仅对象运行时需要的空 receiver 检查，避免改变阶段 0 纯 static 快照。 */
  val objectSource: String = """
    // 所有实例 receiver 在属性访问和调用前通过这里统一转为 Java 风格空指针错误。
    function @__j_non_null(value) {
      if (value === null) {
        throw new Error("java.lang.NullPointerException");
      }
      return value;
    }
  """.trimIndent().replace('@', '$')
}
