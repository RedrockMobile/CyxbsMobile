package com.cyxbs.functions.code.language.java.compiler.source

/**
 * 一次编译请求中的源码文件编号。
 *
 * 编号仅在当前 [JavaSourceWorkspace] 内稳定，不应持久化或跨编译请求复用。
 */
internal data class JavaSourceFileId(val value: Int)

/**
 * AST 节点编号。
 *
 * [localId] 在单个源码文件内单调递增；与 [fileId] 组合后可作为语义 side table 的稳定键。
 */
internal data class JavaNodeId(
  val fileId: JavaSourceFileId,
  val localId: Int,
)

/**
 * Java 源码中的 UTF-16 半开区间。
 *
 * 偏移语义与 Kotlin、JavaScript 字符串以及 Lezer 节点一致，便于直接返回编辑器诊断。
 */
internal data class JavaSourceSpan(
  val fileId: JavaSourceFileId,
  val from: Int,
  val to: Int,
) {
  init {
    require(from >= 0) { "Java source span start must be non-negative." }
    require(to >= from) { "Java source span end must not precede its start." }
  }
}

/** 一份参与编译的 Java 源码及其工作区相对路径。 */
internal data class JavaSourceFile(
  val id: JavaSourceFileId,
  val path: String,
  val source: String,
) {
  init {
    require(path.isNormalizedWorkspacePath()) {
      "Java source path must be a normalized workspace-relative path: '$path'."
    }
  }
}

/**
 * Java 编译器看到的不可变工作区快照。
 *
 * 构造时校验文件编号和路径唯一，后续阶段因此可以安全地用两者建立索引。
 */
internal data class JavaSourceWorkspace(
  val files: List<JavaSourceFile>,
) {
  init {
    require(files.map(JavaSourceFile::id).distinct().size == files.size) {
      "Java source file ids must be unique."
    }
    require(files.map(JavaSourceFile::path).distinct().size == files.size) {
      "Java source file paths must be unique."
    }
  }

  /** 按编号读取源码；内部索引不一致时立即失败，避免生成指向错误文件的诊断。 */
  fun requireFile(fileId: JavaSourceFileId): JavaSourceFile {
    return files.firstOrNull { file -> file.id == fileId }
      ?: error("Java source workspace does not contain file id ${fileId.value}.")
  }

  /** 按工作区路径读取源码；调用方传入未知入口时立即失败。 */
  fun requireFile(path: String): JavaSourceFile {
    return files.firstOrNull { file -> file.path == path }
      ?: error("Java source workspace does not contain '$path'.")
  }
}

/**
 * 为单个源码文件生成 AST 节点编号。
 *
 * 本对象只允许由 CST adapter 在一次解析期间持有，避免增量缓存误把旧节点编号当作新语义结果。
 */
internal class JavaNodeIdSequence(
  private val fileId: JavaSourceFileId,
) {
  private var nextLocalId = 0

  /** 返回当前文件内下一个唯一节点编号。 */
  fun next(): JavaNodeId = JavaNodeId(fileId, nextLocalId++)
}

/** 工作区路径必须使用正斜杠，且不能包含空段、当前目录或父目录。 */
private fun String.isNormalizedWorkspacePath(): Boolean {
  if (isEmpty() || startsWith('/') || contains('\\')) return false
  return split('/').all { segment -> segment.isNotEmpty() && segment != "." && segment != ".." }
}
