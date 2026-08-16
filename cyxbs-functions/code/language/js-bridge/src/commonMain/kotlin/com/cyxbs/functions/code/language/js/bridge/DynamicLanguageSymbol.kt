package com.cyxbs.functions.code.language.js.bridge

import kotlinx.serialization.Serializable

/** 动态语言工作区中的一份源文件。 */
@Serializable
data class DynamicSourceFile(
  /** 工作区内唯一、使用 `/` 分隔的相对路径。 */
  val path: String,
  /** 当前未保存的完整源码也应传入，使语义结果与编辑器一致。 */
  val source: String,
)

/**
 * 一次动态语言分析使用的工作区快照。
 *
 * [files] 的路径必须唯一；语言包可按路径和源码内容复用未变更文件的语法树。
 */
@Serializable
data class DynamicLanguageWorkspace(
  val files: List<DynamicSourceFile>,
)

/**
 * 动态语言协议中的 UTF-16 半开区间。
 *
 * [from] 包含在区间内，[to] 不包含在区间内；该语义与 Kotlin 字符串及端侧编辑器位置保持一致。
 */
@Serializable
data class DynamicTextRange(
  val from: Int,
  val to: Int,
)

/** 工作区内的文件位置；[range] 使用对应文件源码的 UTF-16 偏移。 */
@Serializable
data class DynamicSourceLocation(
  val filePath: String,
  val range: DynamicTextRange,
)

/**
 * 光标位置解析到的工作区词法符号定义。
 *
 * [kind] 由语言包给出稳定的展示类型，例如 `variable`、`function` 或 `class`；客户端不应依赖
 * 某一门语言的封闭枚举。[definition] 指向工作区内实际定义标识符，可能与请求文件不同。
 */
@Serializable
data class DynamicSymbolDefinition(
  val name: String,
  val kind: String? = null,
  val definition: DynamicSourceLocation,
)

/**
 * 一个符号定义及其在工作区中的引用。
 *
 * [references] 不重复包含 [symbol] 的定义区间，并按文件路径、源码位置升序排列。
 */
@Serializable
data class DynamicSymbolReferencesResult(
  val symbol: DynamicSymbolDefinition,
  val references: List<DynamicSourceLocation>,
)

/**
 * 一次基于原始源码位置的文本替换。
 *
 * [from] 与 [to] 使用 UTF-16 半开区间，[replacement] 为替换后的完整文本；同一结果内的
 * 多个修改均以请求时的原始源码为坐标，不得逐个应用后重新解释后续位置。
 */
@Serializable
data class DynamicTextEdit(
  val from: Int,
  val to: Int,
  val replacement: String,
)

/** 将一次文本修改定位到工作区内的具体文件。 */
@Serializable
data class DynamicSourceEdit(
  val filePath: String,
  val edit: DynamicTextEdit,
)

/**
 * 一次工作区文件重命名。
 *
 * 两个路径均为工作区内使用 `/` 分隔的相对路径；调用方应与 [DynamicRenameResult.edits] 原子应用，
 * 避免源码名称与文件路径短暂不一致。
 */
@Serializable
data class DynamicFileRename(
  val oldPath: String,
  val newPath: String,
)

/**
 * 动态语言包生成的工作区重命名结果。
 *
 * [rejectionCode] 为 null 时表示重命名可安全执行；同一文件的 [edits] 按请求快照中的原始
 * 源码位置升序排列且互不重叠。[fileRenames] 与文本修改属于同一事务，调用方必须一次性更新
 * 所有涉及文件，不能只提交当前文件；非 null 时客户端不得应用任何修改，并可直接展示
 * [rejectionMessage]。拒绝码使用字符串而非枚举，便于语言包增加细分原因时保持旧客户端可解码。
 */
@Serializable
data class DynamicRenameResult(
  val symbol: DynamicSymbolDefinition,
  val edits: List<DynamicSourceEdit> = emptyList(),
  val fileRenames: List<DynamicFileRename> = emptyList(),
  val rejectionCode: String? = null,
  val rejectionMessage: String? = null,
) {

  /** 当前结果是否可以直接应用到发起请求时的源码。 */
  val isSuccess: Boolean
    get() = rejectionCode == null
}
