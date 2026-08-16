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

  /** 数组与字符串拼接运行时；所有检查集中在这里避免 JavaScript 默认语义泄漏。 */
  val arrayAndStringSource: String = """
    function @__j_array(value) {
      return @__j_non_null(value);
    }

    function @__j_array_index(array, index) {
      array = @__j_array(array);
      index |= 0;
      if (index < 0 || index >= array.length) {
        throw new Error("java.lang.ArrayIndexOutOfBoundsException: " + index);
      }
      return index;
    }

    function @__j_new_array(length, defaultValue, component) {
      length |= 0;
      if (length < 0) {
        throw new Error("java.lang.NegativeArraySizeException: " + length);
      }
      const array = Array(length).fill(defaultValue);
      Object.defineProperty(array, "@__j_component", { value: component });
      return array;
    }

    function @__j_array_set(array, index, value) {
      index = @__j_array_index(array, index);
      const component = array.@__j_component;
      if (value !== null) {
        if (component === "string") {
          if (typeof value !== "string") throw new Error("java.lang.ArrayStoreException");
        } else if (component !== null && component !== "object" &&
          typeof component !== "string" && !component.isPrototypeOf(value)) {
          throw new Error("java.lang.ArrayStoreException");
        }
      }
      value = @__j_array_component_value(value, component);
      array[index] = value;
      return value;
    }

    // 数组 store 是 byte/short/char 的窄化边界；复合赋值先按 int 计算，再在这里收窄。
    function @__j_array_component_value(value, component) {
      switch (component) {
        case "primitive:BOOLEAN": return !!value;
        case "primitive:BYTE": return (value << 24) >> 24;
        case "primitive:SHORT": return (value << 16) >> 16;
        case "primitive:CHAR": return value & 65535;
        case "primitive:INT": return value | 0;
        default: return value;
      }
    }

    function @__j_string_part(value, kind) {
      switch (kind) {
        case "NULL": return "null";
        case "BOOLEAN": return value ? "true" : "false";
        case "CHAR": return String.fromCharCode(value & 65535);
        default: return value === null ? "null" : String(value);
      }
    }

    function @__j_string_concat(values, kinds) {
      let result = "";
      for (let index = 0; index < values.length; index++) {
        result += @__j_string_part(values[index], kinds[index]);
      }
      return result;
    }
  """.trimIndent().replace('@', '$')
}
