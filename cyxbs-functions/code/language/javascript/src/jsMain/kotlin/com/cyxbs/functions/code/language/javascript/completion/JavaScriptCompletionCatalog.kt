package com.cyxbs.functions.code.language.javascript.completion

import com.cyxbs.functions.code.language.js.bridge.DynamicCompletionItem

/**
 * JavaScript 教学场景使用的稳定内置符号和成员目录。
 *
 * 这里只列出常见标准能力，不读取 QuickJS Runtime 的全局对象，避免宿主桥接和用户运行时代码改变
 * 静态补全结果。后续扩充成员无需修改作用域索引算法或端上协议。
 */
internal val JAVASCRIPT_GLOBALS = listOf(
  "Array", "Boolean", "JSON", "Map", "Math", "Number", "Object", "Promise", "Set", "String",
  "console", "parseFloat", "parseInt", "setInterval", "setTimeout",
).map { name ->
  DynamicCompletionItem(
    label = name,
    detail = "JavaScript 全局符号",
    type = if (name.first().isUpperCase()) COMPLETION_TYPE_CLASS else COMPLETION_TYPE_FUNCTION,
    boost = 20,
    apply = name,
  )
}

/** 无需类型推断即可确定的 JavaScript 全局 receiver。 */
internal val BUILTIN_RECEIVER_TYPES = mapOf(
  "Array" to RECEIVER_ARRAY_CONSTRUCTOR,
  "JSON" to "JSON",
  "Math" to "Math",
  "Object" to RECEIVER_OBJECT_CONSTRUCTOR,
  "Promise" to RECEIVER_PROMISE_CONSTRUCTOR,
  "console" to "console",
)

/** 常见内置 receiver 类型对应的教学成员候选。 */
internal val BUILTIN_MEMBERS = mapOf(
  RECEIVER_ARRAY to members(
    RECEIVER_ARRAY,
    "at", "concat", "every", "filter", "find", "findIndex", "forEach", "includes", "join",
    "length", "map", "pop", "push", "reduce", "reverse", "shift", "slice", "some", "sort",
    "splice", "unshift",
  ),
  RECEIVER_STRING to members(
    RECEIVER_STRING,
    "at", "charAt", "endsWith", "includes", "indexOf", "length", "replace", "slice", "split",
    "startsWith", "substring", "toLowerCase", "toUpperCase", "trim",
  ),
  RECEIVER_NUMBER to members(RECEIVER_NUMBER, "toExponential", "toFixed", "toPrecision", "toString"),
  RECEIVER_OBJECT to members(RECEIVER_OBJECT, "hasOwnProperty", "toString", "valueOf"),
  RECEIVER_FUNCTION to members(RECEIVER_FUNCTION, "apply", "bind", "call", "length", "name"),
  RECEIVER_ARRAY_CONSTRUCTOR to members(RECEIVER_ARRAY_CONSTRUCTOR, "from", "isArray", "of"),
  RECEIVER_OBJECT_CONSTRUCTOR to members(
    RECEIVER_OBJECT_CONSTRUCTOR,
    "assign", "entries", "freeze", "fromEntries", "keys", "values",
  ),
  RECEIVER_PROMISE to members(RECEIVER_PROMISE, "catch", "finally", "then"),
  RECEIVER_PROMISE_CONSTRUCTOR to members(
    RECEIVER_PROMISE_CONSTRUCTOR,
    "all", "allSettled", "any", "race", "reject", "resolve",
  ),
  "JSON" to members("JSON", "parse", "stringify"),
  "Math" to members("Math", "abs", "ceil", "floor", "max", "min", "pow", "random", "round", "sqrt"),
  "console" to members("console", "debug", "error", "info", "log", "table", "warn"),
)

/** ECMAScript 教学中常用、可安全静态提供的关键字。 */
internal val JAVASCRIPT_KEYWORDS = listOf(
  "async", "await", "break", "case", "catch", "class", "const", "continue", "debugger", "default",
  "delete", "do", "else", "export", "extends", "false", "finally", "for", "function", "if",
  "import", "in", "instanceof", "let", "new", "null", "of", "return", "super", "switch", "this",
  "throw", "true", "try", "typeof", "undefined", "var", "void", "while", "with", "yield",
)

/** 将一组成员名转换为统一的补全协议模型。 */
private fun members(
  type: String,
  vararg names: String,
): List<DynamicCompletionItem> = names.map { name ->
  DynamicCompletionItem(
    label = name,
    detail = "$type 成员",
    type = if (name == "length") COMPLETION_TYPE_PROPERTY else COMPLETION_TYPE_METHOD,
    boost = MEMBER_BOOST,
    apply = name,
  )
}
