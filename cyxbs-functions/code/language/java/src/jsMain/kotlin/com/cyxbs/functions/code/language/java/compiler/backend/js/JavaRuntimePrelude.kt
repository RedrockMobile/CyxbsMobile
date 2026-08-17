package com.cyxbs.functions.code.language.java.compiler.backend.js

import com.cyxbs.functions.code.language.js.bridge.DynamicProgramHostAbi

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

  /**
   * Java 异常的最小运行时表示与类型匹配。
   *
   * 编译器主动构造的异常保存精确类型属性；既有数组、集合与 Scanner helper 仍抛原生 Error，
   * 因此匹配器也会读取其稳定的 `java.*Exception` message 前缀，让旧运行时错误可被 catch。
   */
  val exceptionSource: String = """
    const @__j_exception_parent = {
      "java.lang.Throwable": null,
      "java.lang.Error": "java.lang.Throwable",
      "java.lang.Exception": "java.lang.Throwable",
      "java.lang.RuntimeException": "java.lang.Exception",
      "java.lang.IllegalArgumentException": "java.lang.RuntimeException",
      "java.lang.IllegalStateException": "java.lang.RuntimeException",
      "java.lang.NullPointerException": "java.lang.RuntimeException",
      "java.lang.ArithmeticException": "java.lang.RuntimeException",
      "java.lang.IndexOutOfBoundsException": "java.lang.RuntimeException",
      "java.lang.ArrayIndexOutOfBoundsException": "java.lang.IndexOutOfBoundsException",
      "java.lang.StringIndexOutOfBoundsException": "java.lang.IndexOutOfBoundsException",
      "java.lang.ClassCastException": "java.lang.RuntimeException",
      "java.lang.UnsupportedOperationException": "java.lang.RuntimeException",
      "java.lang.NegativeArraySizeException": "java.lang.RuntimeException",
      "java.lang.ArrayStoreException": "java.lang.RuntimeException",
      "java.util.NoSuchElementException": "java.lang.RuntimeException",
      "java.util.InputMismatchException": "java.util.NoSuchElementException"
    };

    /** 源码异常类在 prototype 发射阶段登记父边，catch 不依赖 JavaScript constructor.name。 */
    function @__j_register_exception_type(name, parent) {
      if (typeof name !== "string" || typeof parent !== "string" ||
        !Object.prototype.hasOwnProperty.call(@__j_exception_parent, parent)) {
        throw new Error("java.lang.IllegalStateException: invalid exception hierarchy");
      }
      @__j_exception_parent[name] = parent;
    }

    function @__j_exception_name(value) {
      if (value !== null && typeof value === "object" &&
        typeof value.@__j_exception_name === "string") {
        return value.@__j_exception_name;
      }
      if (!(value instanceof Error) || typeof value.message !== "string") return null;
      const separator = value.message.indexOf(":");
      const name = separator < 0 ? value.message : value.message.slice(0, separator);
      return Object.prototype.hasOwnProperty.call(@__j_exception_parent, name) ? name : null;
    }

    function @__j_initialize_exception(target, name, message, cause) {
      Object.defineProperty(target, "@__j_exception_name", { value: name, configurable: true });
      Object.defineProperty(target, "@__j_exception_message", { value: message, configurable: true });
      Object.defineProperty(target, "@__j_exception_cause", { value: cause, configurable: true });
      Object.defineProperty(target, "@__j_suppressed", { value: [], configurable: true });
      if (target instanceof Error) target.message = message === null ? name : name + ": " + message;
      return target;
    }

    function @__j_new_exception(name, message, cause) {
      return @__j_initialize_exception(new Error(), name, message, cause === undefined ? null : cause);
    }

    function @__j_exception_get_message(value) {
      value = @__j_non_null(value);
      if (Object.prototype.hasOwnProperty.call(value, "@__j_exception_message")) {
        return value.@__j_exception_message;
      }
      const name = @__j_exception_name(value);
      if (name === null || typeof value.message !== "string") return null;
      const prefix = name + ": ";
      return value.message.indexOf(prefix) === 0 ? value.message.slice(prefix.length) : null;
    }

    function @__j_exception_get_cause(value) {
      value = @__j_non_null(value);
      return Object.prototype.hasOwnProperty.call(value, "@__j_exception_cause")
        ? value.@__j_exception_cause : null;
    }

    function @__j_exception_to_string(value) {
      value = @__j_non_null(value);
      const name = @__j_exception_name(value);
      if (name === null) throw new Error("java.lang.ClassCastException");
      const message = @__j_exception_get_message(value);
      return message === null ? name : name + ": " + message;
    }

    function @__j_add_suppressed(primary, suppressed) {
      primary = @__j_non_null(primary);
      suppressed = @__j_non_null(suppressed);
      if (primary === suppressed) throw @__j_new_exception(
        "java.lang.IllegalArgumentException", "Self-suppression not permitted", null);
      if (!Object.prototype.hasOwnProperty.call(primary, "@__j_suppressed")) {
        Object.defineProperty(primary, "@__j_suppressed", { value: [], configurable: true });
      }
      primary.@__j_suppressed.push(suppressed);
    }

    function @__j_exception_is(value, target) {
      let current = @__j_exception_name(value);
      while (current !== null) {
        if (current === target) return true;
        current = Object.prototype.hasOwnProperty.call(@__j_exception_parent, current)
          ? @__j_exception_parent[current]
          : null;
      }
      return false;
    }

    function @__j_throw(value) {
      if (value === null) {
        throw @__j_new_exception("java.lang.NullPointerException", null, null);
      }
      throw value;
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

    // component descriptor 对数组类型递归保存，避免多维协变检查退化为 JavaScript Array 判断。
    function @__j_array_component(component) {
      return { @__j_array_component: component };
    }

    function @__j_array_component_subtype(actual, expected) {
      if (actual === expected) return true;
      if (expected === "object") {
        return actual === "object" || actual === "string" ||
          (actual !== null && typeof actual !== "string");
      }
      if (actual === null || expected === null) return false;
      if (typeof actual === "string" || typeof expected === "string") return false;
      const actualNested = actual.@__j_array_component;
      const expectedNested = expected.@__j_array_component;
      if (actualNested !== undefined || expectedNested !== undefined) {
        return actualNested !== undefined && expectedNested !== undefined &&
          @__j_array_component_subtype(actualNested, expectedNested);
      }
      // 源码 class prototype 以 Object.create(null) 创建，本身没有 isPrototypeOf 方法。
      return expected === actual || Object.prototype.isPrototypeOf.call(expected, actual);
    }

    function @__j_array_value_matches(value, component) {
      if (value === null || component === "object") return true;
      if (component === "string") return typeof value === "string";
      if (component === null || typeof component === "string") return true;
      const expectedNested = component.@__j_array_component;
      if (expectedNested !== undefined) {
        return Array.isArray(value) && Object.prototype.hasOwnProperty.call(value, "@__j_component") &&
          @__j_array_component_subtype(value.@__j_component, expectedNested);
      }
      return Object.prototype.isPrototypeOf.call(component, value);
    }

    function @__j_array_default(component) {
      if (component === "primitive:BOOLEAN") return false;
      return typeof component === "string" && component.indexOf("primitive:") === 0 ? 0 : null;
    }

    // lengths 已由生成代码以数组字面量从左到右求值一次；递归阶段不得再次执行源码表达式。
    function @__j_new_multi_array(lengths, component) {
      function allocate(depth, currentComponent) {
        const value = @__j_new_array(
          lengths[depth],
          @__j_array_default(currentComponent),
          currentComponent
        );
        if (depth + 1 < lengths.length) {
          const nested = currentComponent.@__j_array_component;
          for (let index = 0; index < value.length; index++) {
            value[index] = allocate(depth + 1, nested);
          }
        }
        return value;
      }
      return allocate(0, component);
    }

    function @__j_array_set(array, index, value) {
      index = @__j_array_index(array, index);
      const component = array.@__j_component;
      if (!@__j_array_value_matches(value, component)) {
        throw new Error("java.lang.ArrayStoreException");
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
        case "BOXED":
          if (value === null) return "null";
          if (value.@__j_box_tag === "BOOLEAN") return value.value ? "true" : "false";
          if (value.@__j_box_tag === "CHAR") return String.fromCharCode(value.value & 65535);
          if (value.@__j_box_tag === "BYTE" || value.@__j_box_tag === "SHORT" || value.@__j_box_tag === "INT") {
            return String(value.value | 0);
          }
          throw new Error("java.lang.ClassCastException");
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

  /** 精选 Java 类库运行时；仅当 IR 含 builtin operation 时由 emitter 注入。 */
  val builtinSource: String = """
    function @__j_write_stream(stream, text) {
      @__j_non_null(stream);
      const writer = stream === 0
        ? globalThis["${DynamicProgramHostAbi.WRITE_STANDARD_OUTPUT}"]
        : stream === 1 ? globalThis["${DynamicProgramHostAbi.WRITE_STANDARD_ERROR}"] : null;
      if (typeof writer !== "function") {
        throw new Error("java.lang.IllegalStateException: Java output host bridge is unavailable");
      }
      writer(text);
    }

    function @__j_print_boolean(stream, value) { @__j_write_stream(stream, value ? "true" : "false"); }
    function @__j_print_char(stream, value) { @__j_write_stream(stream, String.fromCharCode(value & 65535)); }
    function @__j_char_array_text(value) {
      value = @__j_array(value);
      if (value.@__j_component !== "primitive:CHAR") throw new Error("java.lang.ClassCastException");
      let result = "";
      for (let index = 0; index < value.length; index++) {
        result += String.fromCharCode(value[index] & 65535);
      }
      return result;
    }
    function @__j_print_char_array(stream, value) { @__j_write_stream(stream, @__j_char_array_text(value)); }
    function @__j_print_int(stream, value) { @__j_write_stream(stream, String(value | 0)); }
    function @__j_print_string(stream, value) { @__j_write_stream(stream, value === null ? "null" : value); }
    function @__j_print_object(stream, value) { @__j_write_stream(stream, @__j_string_value_of_object(value)); }
    function @__j_println(stream) { @__j_write_stream(stream, "\n"); }
    function @__j_println_boolean(stream, value) { @__j_write_stream(stream, (value ? "true" : "false") + "\n"); }
    function @__j_println_char(stream, value) { @__j_write_stream(stream, String.fromCharCode(value & 65535) + "\n"); }
    function @__j_println_char_array(stream, value) { @__j_write_stream(stream, @__j_char_array_text(value) + "\n"); }
    function @__j_println_int(stream, value) { @__j_write_stream(stream, String(value | 0) + "\n"); }
    function @__j_println_string(stream, value) { @__j_write_stream(stream, (value === null ? "null" : value) + "\n"); }
    function @__j_println_object(stream, value) { @__j_write_stream(stream, @__j_string_value_of_object(value) + "\n"); }

    function @__j_string_receiver(value) {
      value = @__j_non_null(value);
      if (typeof value !== "string") throw new Error("java.lang.ClassCastException");
      return value;
    }

    function @__j_string_argument(value) {
      value = @__j_non_null(value);
      if (typeof value !== "string") throw new Error("java.lang.ClassCastException");
      return value;
    }

    function @__j_string_length(value) { return @__j_string_receiver(value).length | 0; }
    function @__j_string_is_empty(value) { return @__j_string_receiver(value).length === 0; }
    function @__j_string_char_at(value, index) {
      value = @__j_string_receiver(value);
      index |= 0;
      if (index < 0 || index >= value.length) {
        throw new Error("java.lang.StringIndexOutOfBoundsException: " + index);
      }
      return value.charCodeAt(index) | 0;
    }

    function @__j_string_equals(value, other) {
      value = @__j_string_receiver(value);
      return typeof other === "string" && value === other;
    }

    function @__j_string_substring_range(value, begin, end) {
      value = @__j_string_receiver(value);
      begin |= 0;
      end |= 0;
      if (begin < 0 || end > value.length || begin > end) {
        throw new Error("java.lang.StringIndexOutOfBoundsException");
      }
      return value.slice(begin, end);
    }

    function @__j_string_substring_from(value, begin) {
      value = @__j_string_receiver(value);
      return @__j_string_substring_range(value, begin, value.length);
    }

    function @__j_string_index_of_code_point(value, codePoint) {
      value = @__j_string_receiver(value);
      codePoint |= 0;
      if (codePoint < 0 || codePoint > 1114111) return -1;
      return value.indexOf(String.fromCodePoint(codePoint)) | 0;
    }

    function @__j_string_index_of_string(value, searched) {
      return @__j_string_receiver(value).indexOf(@__j_string_argument(searched)) | 0;
    }

    function @__j_string_contains(value, searched) {
      return @__j_string_receiver(value).includes(@__j_string_argument(searched));
    }

    function @__j_string_starts_with(value, prefix) {
      return @__j_string_receiver(value).startsWith(@__j_string_argument(prefix));
    }

    function @__j_string_ends_with(value, suffix) {
      return @__j_string_receiver(value).endsWith(@__j_string_argument(suffix));
    }

    function @__j_math_abs_int(value) { return Math.abs(value | 0) | 0; }
    function @__j_math_min_int(left, right) { return Math.min(left | 0, right | 0) | 0; }
    function @__j_math_max_int(left, right) { return Math.max(left | 0, right | 0) | 0; }

    // wrapper 使用显式标签，禁止把普通 JS Number/Boolean 当成 Java 引用。
    const @__j_box_cache = new Map();

    function @__j_box_normalize(kind, value) {
      switch (kind) {
        case "BOOLEAN": return !!value;
        case "BYTE": return (value << 24) >> 24;
        case "SHORT": return (value << 16) >> 16;
        case "CHAR": return value & 65535;
        case "INT": return value | 0;
        default: throw new Error("java.lang.IllegalStateException: unsupported wrapper kind");
      }
    }

    function @__j_box(kind, value) {
      value = @__j_box_normalize(kind, value);
      const cached = kind === "BOOLEAN" || kind === "BYTE" ||
        ((kind === "SHORT" || kind === "INT") && value >= -128 && value <= 127) ||
        (kind === "CHAR" && value >= 0 && value <= 127);
      const key = kind + ":" + String(value);
      if (cached && @__j_box_cache.has(key)) return @__j_box_cache.get(key);
      const boxed = Object.freeze({ @__j_box_tag: kind, value: value });
      if (cached) @__j_box_cache.set(key, boxed);
      return boxed;
    }

    function @__j_unbox(value, kind) {
      value = @__j_non_null(value);
      if (value.@__j_box_tag !== kind) throw new Error("java.lang.ClassCastException");
      return value.value;
    }

    function @__j_number_int_value(value) {
      value = @__j_non_null(value);
      if (value.@__j_box_tag !== "BYTE" && value.@__j_box_tag !== "SHORT" &&
        value.@__j_box_tag !== "INT") throw new Error("java.lang.ClassCastException");
      return value.value | 0;
    }

    function @__j_box_equals(value, other) {
      value = @__j_non_null(value);
      return other !== null && value.@__j_box_tag === other.@__j_box_tag && value.value === other.value;
    }

    function @__j_box_hash(value) {
      value = @__j_non_null(value);
      if (value.@__j_box_tag === "BOOLEAN") return value.value ? 1231 : 1237;
      return value.value | 0;
    }

    function @__j_box_to_string(value) {
      value = @__j_non_null(value);
      if (value.@__j_box_tag === "BOOLEAN") return value.value ? "true" : "false";
      if (value.@__j_box_tag === "CHAR") return String.fromCharCode(value.value & 65535);
      return String(value.value | 0);
    }

    // Object 的默认 hashCode 只要求同一运行对象稳定；WeakMap 不污染用户字段或枚举结果。
    const @__j_identity_hashes = new WeakMap();
    let @__j_next_identity_hash = 1;

    function @__j_identity_hash(value) {
      if (value === 0 || value === 1) return (value + 1) | 0;
      if (value === 2) return 3;
      if ((typeof value !== "object" && typeof value !== "function") || value === null) {
        return 0;
      }
      let hash = @__j_identity_hashes.get(value);
      if (hash === undefined) {
        hash = @__j_next_identity_hash++ | 0;
        if (hash === 0) hash = @__j_next_identity_hash++ | 0;
        @__j_identity_hashes.set(value, hash);
      }
      return hash | 0;
    }

    function @__j_string_hash(value) {
      let hash = 0;
      for (let index = 0; index < value.length; index++) {
        hash = (Math.imul(hash, 31) + value.charCodeAt(index)) | 0;
      }
      return hash | 0;
    }

    /** Object.equals：源码 override 优先，精选 builtin 使用各自 Java 值语义，其他对象按身份比较。 */
    function @__j_object_equals(value, other) {
      value = @__j_non_null(value);
      const override = value[@__j_object_equals_slot];
      if (typeof override === "function") return !!override.call(value, other);
      if (typeof value === "string") return typeof other === "string" && value === other;
      if (value.@__j_box_tag !== undefined) return @__j_box_equals(value, other);
      return value === other;
    }

    /** Collection 查找允许 null，并按照查询对象.equals(已有元素) 的 Java 方向调用。 */
    function @__j_object_equals_nullable(value, other) {
      return value === null ? other === null : @__j_object_equals(value, other);
    }

    /** Object.hashCode：源码 override 优先，String/wrapper 使用 Java 算法，其余保持运行期身份稳定。 */
    function @__j_object_hash_code(value) {
      value = @__j_non_null(value);
      const override = value[@__j_object_hash_code_slot];
      if (typeof override === "function") return override.call(value) | 0;
      if (typeof value === "string") return @__j_string_hash(value);
      if (value.@__j_box_tag !== undefined) return @__j_box_hash(value);
      return @__j_identity_hash(value);
    }

    /** Object.toString：源码 override 优先，再处理精选 builtin，最终回退 Java 默认类名@十六进制哈希。 */
    function @__j_object_to_string(value) {
      value = @__j_non_null(value);
      const override = value[@__j_object_to_string_slot];
      if (typeof override === "function") return @__j_string_receiver(override.call(value));
      if (typeof value === "string") return value;
      if (value === 0 || value === 1) return "java.io.PrintStream" + String.fromCharCode(64) + String(value + 1);
      if (value === 2) return "java.io.InputStream" + String.fromCharCode(64) + "1";
      if (value.@__j_box_tag !== undefined) return @__j_box_to_string(value);
      if (value.@__j_string_builder === true) return value.value;
      switch (value.@__j_collection) {
        case "LIST": return "[" + value.values.map(item =>
          item === value ? "(this Collection)" : @__j_string_value_of_object(item)).join(", ") + "]";
        case "SET": return "[" + value.values.map(item =>
          item === value ? "(this Collection)" : @__j_string_value_of_object(item)).join(", ") + "]";
        case "KEY_SET": return "[" + value.owner.entries.map(entry =>
          entry.key === value ? "(this Collection)" : @__j_string_value_of_object(entry.key)).join(", ") + "]";
        case "MAP": return "{" + value.entries.map(entry =>
          (entry.key === value ? "(this Map)" : @__j_string_value_of_object(entry.key)) + "=" +
          (entry.value === value ? "(this Map)" : @__j_string_value_of_object(entry.value))).join(", ") + "}";
      }
      const className = value["@__j_class_name"];
      if (typeof className === "string") {
        return className + String.fromCharCode(64) + ((@__j_identity_hash(value) >>> 0).toString(16));
      }
      return "java.lang.Object" + String.fromCharCode(64) + ((@__j_identity_hash(value) >>> 0).toString(16));
    }

    /** String.valueOf(Object) 与 print(Object) 接受 null；直接 receiver 调用仍由上面的 non-null 检查抛 NPE。 */
    function @__j_string_value_of_object(value) {
      return value === null ? "null" : @__j_object_to_string(value);
    }

    function @__j_sb_receiver(value) {
      value = @__j_non_null(value);
      if (value.@__j_string_builder !== true) throw new Error("java.lang.ClassCastException");
      return value;
    }

    function @__j_sb_new() { return { @__j_string_builder: true, value: "" }; }
    function @__j_sb_new_string(value) {
      return { @__j_string_builder: true, value: @__j_string_argument(value) };
    }
    function @__j_sb_append_boolean(builder, value) {
      builder = @__j_sb_receiver(builder); builder.value += value ? "true" : "false"; return builder;
    }
    function @__j_sb_append_char(builder, value) {
      builder = @__j_sb_receiver(builder); builder.value += String.fromCharCode(value & 65535); return builder;
    }
    function @__j_sb_append_char_array(builder, value) {
      builder = @__j_sb_receiver(builder); builder.value += @__j_char_array_text(value); return builder;
    }
    function @__j_sb_append_int(builder, value) {
      builder = @__j_sb_receiver(builder); builder.value += String(value | 0); return builder;
    }
    function @__j_sb_append_string(builder, value) {
      builder = @__j_sb_receiver(builder); builder.value += value === null ? "null" : @__j_string_argument(value); return builder;
    }
    function @__j_sb_append_object(builder, value) {
      builder = @__j_sb_receiver(builder); builder.value += @__j_string_value_of_object(value); return builder;
    }
    function @__j_sb_length(builder) { return @__j_sb_receiver(builder).value.length | 0; }
    function @__j_sb_char_at(builder, index) { return @__j_string_char_at(@__j_sb_receiver(builder).value, index); }
    function @__j_sb_set_char_at(builder, index, value) {
      builder = @__j_sb_receiver(builder);
      index |= 0;
      if (index < 0 || index >= builder.value.length) throw new Error("java.lang.StringIndexOutOfBoundsException: " + index);
      builder.value = builder.value.slice(0, index) + String.fromCharCode(value & 65535) + builder.value.slice(index + 1);
    }
    function @__j_sb_reverse(builder) {
      builder = @__j_sb_receiver(builder);
      // Array.from 按 code point 反转，与 Java 8 对有效 surrogate pair 的特殊处理一致。
      builder.value = Array.from(builder.value).reverse().join("");
      return builder;
    }
    function @__j_sb_substring_from(builder, begin) {
      builder = @__j_sb_receiver(builder); return @__j_string_substring_from(builder.value, begin);
    }
    function @__j_sb_substring_range(builder, begin, end) {
      builder = @__j_sb_receiver(builder); return @__j_string_substring_range(builder.value, begin, end);
    }
    function @__j_sb_to_string(builder) { return @__j_sb_receiver(builder).value; }
  """.trimIndent().replace('@', '$')

  /**
   * 精选集合运行时，仅在 typed IR 含集合 operation 时注入。
   *
   * Set/Map 使用受控线性表而不是 JavaScript Map：这样 String、null 与缓存范围外的
   * wrapper 也能遵守 Java 值语义。查询对象会通过 Object.equals 虚槽调用用户 override；当前
   * 线性表不依赖 hashCode 分桶，也不实现 fail-fast iterator。iterator 只保存 backing
   * collection 与当前位置，不复制整个集合；迭代期间修改后的可见顺序属于受限兼容行为。
   */
  val collectionSource: String = """
    function @__j_collection_key_equals(left, right) {
      return @__j_object_equals_nullable(right, left);
    }

    function @__j_list(value) {
      value = @__j_non_null(value);
      if (value.@__j_collection !== "LIST") throw new Error("java.lang.ClassCastException");
      return value;
    }
    function @__j_list_new() { return { @__j_collection: "LIST", values: [] }; }
    function @__j_list_index(value, index) {
      value = @__j_list(value);
      index |= 0;
      if (index < 0 || index >= value.values.length) {
        throw new Error("java.lang.IndexOutOfBoundsException: " + index);
      }
      return index;
    }
    function @__j_list_size(value) { return @__j_list(value).values.length | 0; }
    function @__j_list_is_empty(value) { return @__j_list(value).values.length === 0; }
    function @__j_list_add(value, element) { @__j_list(value).values.push(element); return true; }
    function @__j_list_get(value, index) { return @__j_list(value).values[@__j_list_index(value, index)]; }
    function @__j_list_set(value, index, element) {
      value = @__j_list(value); index = @__j_list_index(value, index);
      const previous = value.values[index]; value.values[index] = element; return previous;
    }
    function @__j_list_remove_index(value, index) {
      value = @__j_list(value); index = @__j_list_index(value, index);
      return value.values.splice(index, 1)[0];
    }
    function @__j_list_index_of(value, element) {
      value = @__j_list(value);
      for (let index = 0; index < value.values.length; index++) {
        if (@__j_collection_key_equals(value.values[index], element)) return index | 0;
      }
      return -1;
    }
    function @__j_list_remove_object(value, element) {
      value = @__j_list(value); const index = @__j_list_index_of(value, element);
      if (index < 0) return false; value.values.splice(index, 1); return true;
    }
    function @__j_list_contains(value, element) { return @__j_list_index_of(value, element) >= 0; }
    function @__j_list_clear(value) { @__j_list(value).values.length = 0; }

    function @__j_map(value) {
      value = @__j_non_null(value);
      if (value.@__j_collection !== "MAP") throw new Error("java.lang.ClassCastException");
      return value;
    }
    function @__j_map_new() { return { @__j_collection: "MAP", entries: [] }; }
    function @__j_map_index_of(value, key) {
      value = @__j_map(value);
      for (let index = 0; index < value.entries.length; index++) {
        if (@__j_collection_key_equals(value.entries[index].key, key)) return index | 0;
      }
      return -1;
    }
    function @__j_map_put(value, key, element) {
      value = @__j_map(value); const index = @__j_map_index_of(value, key);
      if (index < 0) { value.entries.push({ key: key, value: element }); return null; }
      const previous = value.entries[index].value; value.entries[index].value = element; return previous;
    }
    function @__j_map_get(value, key) {
      value = @__j_map(value); const index = @__j_map_index_of(value, key);
      return index < 0 ? null : value.entries[index].value;
    }
    function @__j_map_get_or_default(value, key, fallback) {
      value = @__j_map(value); const index = @__j_map_index_of(value, key);
      return index < 0 ? fallback : value.entries[index].value;
    }
    function @__j_map_contains_key(value, key) { return @__j_map_index_of(value, key) >= 0; }
    function @__j_map_remove(value, key) {
      value = @__j_map(value); const index = @__j_map_index_of(value, key);
      return index < 0 ? null : value.entries.splice(index, 1)[0].value;
    }
    function @__j_map_size(value) { return @__j_map(value).entries.length | 0; }
    function @__j_map_is_empty(value) { return @__j_map(value).entries.length === 0; }
    function @__j_map_clear(value) { @__j_map(value).entries.length = 0; }
    function @__j_map_key_set(value) { return { @__j_collection: "KEY_SET", owner: @__j_map(value) }; }

    function @__j_set(value) {
      value = @__j_non_null(value);
      if (value.@__j_collection !== "SET" && value.@__j_collection !== "KEY_SET") {
        throw new Error("java.lang.ClassCastException");
      }
      return value;
    }
    function @__j_set_new() { return { @__j_collection: "SET", values: [] }; }
    function @__j_set_size(value) {
      value = @__j_set(value);
      return (value.@__j_collection === "KEY_SET" ? value.owner.entries.length : value.values.length) | 0;
    }
    function @__j_set_is_empty(value) { return @__j_set_size(value) === 0; }
    function @__j_set_contains(value, element) {
      value = @__j_set(value);
      if (value.@__j_collection === "KEY_SET") return @__j_map_index_of(value.owner, element) >= 0;
      return value.values.some(candidate => @__j_collection_key_equals(candidate, element));
    }
    function @__j_set_add(value, element) {
      value = @__j_set(value);
      if (value.@__j_collection === "KEY_SET") throw new Error("java.lang.UnsupportedOperationException");
      if (@__j_set_contains(value, element)) return false; value.values.push(element); return true;
    }
    function @__j_set_remove(value, element) {
      value = @__j_set(value);
      if (value.@__j_collection === "KEY_SET") {
        const index = @__j_map_index_of(value.owner, element);
        if (index < 0) return false; value.owner.entries.splice(index, 1); return true;
      }
      const index = value.values.findIndex(candidate => @__j_collection_key_equals(candidate, element));
      if (index < 0) return false; value.values.splice(index, 1); return true;
    }
    function @__j_set_clear(value) {
      value = @__j_set(value);
      if (value.@__j_collection === "KEY_SET") value.owner.entries.length = 0; else value.values.length = 0;
    }

    function @__j_iterator_new(source, kind) {
      return { @__j_collection: "ITERATOR", source: source, kind: kind, index: 0 };
    }
    function @__j_list_iterator(value) { return @__j_iterator_new(@__j_list(value), "VALUES"); }
    function @__j_set_iterator(value) {
      value = @__j_set(value);
      return value.@__j_collection === "KEY_SET"
        ? @__j_iterator_new(value.owner, "KEYS")
        : @__j_iterator_new(value, "VALUES");
    }
    function @__j_iterator(value) {
      value = @__j_non_null(value);
      if (value.@__j_collection !== "ITERATOR") throw new Error("java.lang.ClassCastException");
      return value;
    }
    function @__j_iterator_has_next(value) {
      value = @__j_iterator(value);
      const length = value.kind === "KEYS" ? value.source.entries.length : value.source.values.length;
      return value.index < length;
    }
    function @__j_iterator_next(value) {
      value = @__j_iterator(value);
      const length = value.kind === "KEYS" ? value.source.entries.length : value.source.values.length;
      if (value.index >= length) throw new Error("java.util.NoSuchElementException");
      return value.kind === "KEYS"
        ? value.source.entries[value.index++].key
        : value.source.values[value.index++];
    }
  """.trimIndent().replace('@', '$')

  /**
   * Scanner 的同步预加载运行时。
   *
   * 首个 Scanner 构造会从 host ABI 一次性取得完整 UTF-16 文本，此后所有绑定 System.in 的
   * Scanner 共享同一 cursor。本阶段不支持运行中等待新输入，也不实现 locale/radix/delimiter
   * 配置；十进制 int 与受限 Java whitespace 足以覆盖教学控制台场景。
   */
  val scannerSource: String = """
    let @__j_scanner_input_state = null;

    function @__j_scanner_input() {
      if (@__j_scanner_input_state !== null) return @__j_scanner_input_state;
      if (typeof ${DynamicProgramHostAbi.READ_STANDARD_INPUT_TEXT} !== "function") {
        throw new Error("java.lang.IllegalStateException: Java standard input host bridge is unavailable");
      }
      const text = ${DynamicProgramHostAbi.READ_STANDARD_INPUT_TEXT}();
      if (typeof text !== "string") {
        throw new Error("java.lang.IllegalStateException: Java standard input must be text");
      }
      @__j_scanner_input_state = { text: text, cursor: 0 };
      return @__j_scanner_input_state;
    }

    function @__j_scanner_new(stream) {
      if (stream === null) throw new Error("java.lang.NullPointerException");
      if (stream !== 2) {
        throw new Error("java.lang.IllegalArgumentException: Scanner only supports System.in");
      }
      @__j_scanner_input();
      return { @__j_scanner: true, closed: false };
    }

    function @__j_scanner_value(value) {
      value = @__j_non_null(value);
      if (value.@__j_scanner !== true) throw new Error("java.lang.ClassCastException");
      return value;
    }

    function @__j_scanner(value) {
      value = @__j_scanner_value(value);
      if (value.closed) throw new Error("java.lang.IllegalStateException: Scanner closed");
      return value;
    }

    /** Scanner.close 可重复调用；关闭后所有读取 API 都稳定抛 IllegalStateException。 */
    function @__j_scanner_close(value) {
      @__j_scanner_value(value).closed = true;
    }

    // 对齐 Character.isWhitespace 的教学常用子集；NBSP 等空白不会被静默当作 delimiter。
    function @__j_scanner_is_whitespace(code) {
      return code >= 9 && code <= 13 || code >= 28 && code <= 32 ||
        code === 5760 || code >= 8192 && code <= 8198 || code >= 8200 && code <= 8202 ||
        code === 8232 || code === 8233 || code === 8287 || code === 12288;
    }

    function @__j_scanner_skip_whitespace(text, cursor) {
      while (cursor < text.length && @__j_scanner_is_whitespace(text.charCodeAt(cursor))) cursor++;
      return cursor;
    }

    // peek 不修改共享 cursor，使 hasNext/hasNextInt 可安全重复调用，invalid nextInt 也不消费 token。
    function @__j_scanner_peek_token() {
      const state = @__j_scanner_input();
      const start = @__j_scanner_skip_whitespace(state.text, state.cursor);
      if (start >= state.text.length) return null;
      let end = start;
      while (end < state.text.length && !@__j_scanner_is_whitespace(state.text.charCodeAt(end))) end++;
      return { start: start, end: end, text: state.text.slice(start, end) };
    }

    function @__j_scanner_has_next(scanner) {
      @__j_scanner(scanner); return @__j_scanner_peek_token() !== null;
    }

    function @__j_scanner_next(scanner) {
      @__j_scanner(scanner);
      const token = @__j_scanner_peek_token();
      if (token === null) throw new Error("java.util.NoSuchElementException");
      @__j_scanner_input().cursor = token.end;
      return token.text;
    }

    function @__j_scanner_decimal_int(token) {
      if (!/^[+-]?[0-9]+$/.test(token)) return null;
      const value = Number(token);
      if (!Number.isFinite(value) || value < -2147483648 || value > 2147483647) return null;
      return value | 0;
    }

    function @__j_scanner_has_next_int(scanner) {
      @__j_scanner(scanner);
      const token = @__j_scanner_peek_token();
      return token !== null && @__j_scanner_decimal_int(token.text) !== null;
    }

    function @__j_scanner_next_int(scanner) {
      @__j_scanner(scanner);
      const token = @__j_scanner_peek_token();
      if (token === null) throw new Error("java.util.NoSuchElementException");
      const value = @__j_scanner_decimal_int(token.text);
      if (value === null) throw new Error("java.util.InputMismatchException");
      @__j_scanner_input().cursor = token.end;
      return value;
    }

    function @__j_scanner_has_next_line(scanner) {
      @__j_scanner(scanner);
      const state = @__j_scanner_input();
      return state.cursor < state.text.length;
    }

    function @__j_scanner_next_line(scanner) {
      @__j_scanner(scanner);
      const state = @__j_scanner_input();
      if (state.cursor >= state.text.length) throw new Error("java.util.NoSuchElementException");
      const start = state.cursor;
      let end = start;
      while (end < state.text.length) {
        const code = state.text.charCodeAt(end);
        // Java 8 行终止符同时包含 LF、CR、NEL、LS 与 PS；CRLF 仍作为一个终止符消费。
        if (code === 10 || code === 13 || code === 133 || code === 8232 || code === 8233) break;
        end++;
      }
      const result = state.text.slice(start, end);
      if (end < state.text.length) {
        if (state.text.charCodeAt(end) === 13 && end + 1 < state.text.length &&
          state.text.charCodeAt(end + 1) === 10) {
          end += 2;
        } else {
          end += 1;
        }
      }
      state.cursor = end;
      return result;
    }
  """.trimIndent().replace('@', '$')
}
