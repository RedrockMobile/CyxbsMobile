package com.cyxbs.functions.code.language.js.bridge

import kotlinx.serialization.Serializable

/**
 * 动态程序的语言无关入口位置。
 *
 * [filePath] 指向工作区中的入口文件；[position] 是可选的 UTF-16 光标位置。需要执行某个声明的
 * 语言（例如 Java）可用它选择包含该位置的函数，按文件整体执行的语言（例如 JavaScript）
 * 可以忽略该位置。协议不暴露 JVM descriptor、Python 模块名等语言私有身份。
 */
@Serializable
data class DynamicProgramEntry(
  val filePath: String,
  val position: Int? = null,
)

/**
 * 语言包在当前工作区发现的一处可运行入口。
 *
 * [entry] 是后续编译使用的稳定入口位置；[location] 仅用于编辑器展示行号运行标记，可以为空。
 * Java 等声明式入口语言应返回限定类名或其他可区分重载的 [displayName]，JavaScript、Python 等
 * 按文件执行的语言通常只返回当前活动文件。
 */
@Serializable
data class DynamicRunTarget(
  val displayName: String,
  val entry: DynamicProgramEntry,
  val location: DynamicSourceLocation? = null,
)

/** 一次动态程序编译请求。 */
@Serializable
data class DynamicCompilationRequest(
  val workspace: DynamicLanguageWorkspace,
  val entry: DynamicProgramEntry,
)

/** 动态编译诊断严重程度。 */
@Serializable
enum class DynamicCompilationDiagnosticSeverity {
  ERROR,
  WARNING,
  INFO,
}

/** 为编译诊断补充相关声明位置或推断原因。 */
@Serializable
data class DynamicCompilationDiagnosticNote(
  val message: String,
  val location: DynamicSourceLocation? = null,
)

/**
 * 一条不依赖具体编译器实现的源码诊断。
 *
 * [code] 由语言包稳定维护，客户端可用于分类和测试；[message] 面向用户展示，不应包含
 * QuickJS、Lezer 节点或编译器内部异常类型。
 */
@Serializable
data class DynamicCompilationDiagnostic(
  val code: String,
  val message: String,
  val severity: DynamicCompilationDiagnosticSeverity,
  val location: DynamicSourceLocation? = null,
  val notes: List<DynamicCompilationDiagnosticNote> = emptyList(),
)

/** 生成 JavaScript 中一个位置到原始动态语言源码的映射。 */
@Serializable
data class DynamicGeneratedSourceMapping(
  val generatedLine: Int,
  val generatedColumn: Int,
  val sourceLocation: DynamicSourceLocation,
)

/**
 * 可交给端上 JavaScript Runtime 加载的单个 ES Module。
 *
 * [name] 是本次程序内唯一的逻辑模块名，不是设备文件路径；[sourceMappings] 可为空。
 */
@Serializable
data class DynamicExecutableModule(
  val name: String,
  val source: String,
  val sourceMappings: List<DynamicGeneratedSourceMapping> = emptyList(),
)

/**
 * 动态语言包编译出的语言无关可执行程序。
 *
 * 所有语言都必须把入口适配为 [entryExportName] 指定的 JavaScript 函数。端上会把模块装入一个
 * 全新的隔离 Runtime，再调用该函数；语言分析 Service 自己所在的 Runtime 不会执行用户代码。
 */
@Serializable
data class DynamicExecutableProgram(
  val entryModuleName: String,
  val entryExportName: String,
  val modules: List<DynamicExecutableModule>,
)

/**
 * 动态语言编译结果。
 *
 * [program] 为空表示编译失败，此时 [diagnostics] 至少包含一条 ERROR；警告可与成功程序同时返回。
 */
@Serializable
data class DynamicCompilationResult(
  val program: DynamicExecutableProgram? = null,
  val diagnostics: List<DynamicCompilationDiagnostic> = emptyList(),
)
