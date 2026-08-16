package com.cyxbs.functions.code.language.java.completion

import com.cyxbs.functions.code.language.js.bridge.DynamicCompletionItem

/** Java 教学编辑器常用的关键字补全。 */
internal val JAVA_KEYWORDS = listOf(
  "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
  "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
  "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
  "interface", "long", "native", "new", "package", "private", "protected", "public",
  "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
  "throw", "throws", "transient", "try", "void", "volatile", "while",
)

/**
 * 不依赖端上 JDK classpath 的稳定教学类型目录。
 *
 * 这里只声明初学课程高频类型，用于补全展示和 receiver 成员提示；这些条目没有工作区源码位置，
 * 因此不会伪造定义跳转，也不参与安全重命名。
 */
internal val JAVA_BUILTIN_TYPES = listOf(
  "Object", "String", "StringBuilder", "System", "Math",
  "Boolean", "Byte", "Short", "Integer", "Long", "Float", "Double", "Character",
  "Iterable", "Collection", "List", "ArrayList", "Set", "HashSet",
  "Map", "HashMap", "Collections", "Arrays", "Scanner",
).map { typeName ->
  DynamicCompletionItem(
    label = typeName,
    detail = "Java 常用类型",
    type = "class",
    boost = 25,
    apply = typeName,
  )
}

/**
 * 按静态类型提供的常见成员目录。
 *
 * 成员集合刻意保持精简，不承诺等同某个 JDK 版本；工作区自定义类型的成员由语义索引实时补充。
 */
internal val JAVA_BUILTIN_MEMBERS: Map<String, List<DynamicCompletionItem>> = mapOf(
  "Object" to members(
    methods = listOf("equals", "hashCode", "toString", "getClass"),
  ),
  "String" to members(
    methods = listOf(
      "length", "charAt", "substring", "indexOf", "lastIndexOf", "contains", "equals",
      "isEmpty", "startsWith", "endsWith", "toLowerCase", "toUpperCase", "trim", "split",
    ),
  ),
  "StringBuilder" to members(
    methods = listOf("append", "insert", "delete", "reverse", "length", "toString"),
  ),
  "System" to members(
    methods = listOf("currentTimeMillis", "nanoTime", "arraycopy", "exit"),
    properties = listOf("out", "err", "in"),
  ),
  "PrintStream" to members(
    methods = listOf("print", "println", "printf", "flush", "close"),
  ),
  "Math" to members(
    methods = listOf("abs", "max", "min", "pow", "sqrt", "round", "ceil", "floor", "random"),
    properties = listOf("PI", "E"),
  ),
  "List" to listMembers(),
  "ArrayList" to listMembers(),
  "Collection" to collectionMembers(),
  "Set" to collectionMembers(),
  "HashSet" to collectionMembers(),
  "Map" to members(
    methods = listOf(
      "put", "putAll", "get", "getOrDefault", "remove", "containsKey", "containsValue",
      "keySet", "values", "entrySet", "size", "isEmpty", "clear",
    ),
  ),
  "HashMap" to members(
    methods = listOf(
      "put", "putAll", "get", "getOrDefault", "remove", "containsKey", "containsValue",
      "keySet", "values", "entrySet", "size", "isEmpty", "clear",
    ),
  ),
  "Scanner" to members(
    methods = listOf(
      "next", "nextLine", "nextInt", "nextLong", "nextDouble", "hasNext", "hasNextLine", "close",
    ),
  ),
  "Arrays" to members(
    methods = listOf("sort", "binarySearch", "copyOf", "equals", "fill", "asList", "toString"),
  ),
  "Collections" to members(
    methods = listOf("sort", "reverse", "shuffle", "min", "max", "frequency", "binarySearch"),
  ),
  "array" to members(properties = listOf("length")),
)

/** 将静态目录中的方法和属性转换为动态补全协议。 */
private fun members(
  methods: List<String> = emptyList(),
  properties: List<String> = emptyList(),
): List<DynamicCompletionItem> {
  return buildList {
    methods.forEach { name ->
      add(
        DynamicCompletionItem(
          label = name,
          detail = "Java 常用方法",
          type = "method",
          boost = 60,
          apply = name,
        ),
      )
    }
    properties.forEach { name ->
      add(
        DynamicCompletionItem(
          label = name,
          detail = "Java 常用成员",
          type = "property",
          boost = 60,
          apply = name,
        ),
      )
    }
  }
}

/** 集合家族共享的高频成员目录。 */
private fun collectionMembers(): List<DynamicCompletionItem> {
  return members(
    methods = listOf(
      "add", "addAll", "remove", "contains", "size", "isEmpty", "clear", "iterator", "toArray",
    ),
  )
}

/** List 在 Collection 公共成员之外提供按索引访问能力。 */
private fun listMembers(): List<DynamicCompletionItem> {
  return collectionMembers() + members(methods = listOf("get", "set", "indexOf", "lastIndexOf"))
}
