package com.cyxbs.functions.code.language.api.bridge

import com.cyxbs.functions.code.npm.api.bridge.NpmJsService
import com.cyxbs.functions.code.npm.api.bridge.NpmJsServiceInstance
import kotlinx.serialization.Serializable

/**
 * JavaScript 动态语言包在端上声明的协议元数据。
 *
 * @param languageId 语言稳定标识，例如 `javascript`、`python`。
 * @param displayName 用于调试和教学 UI 展示的语言名称。
 * @param protocolVersion Kotlin 与 JavaScript 桥协议版本。
 */
@Serializable
data class DynamicLanguageMetadata(
  val languageId: String,
  val displayName: String,
  val protocolVersion: Int,
)

/**
 * 动态语法分析返回的一段高亮区间。
 *
 * [styleIds] 使用动态语言包给出的稳定样式标识，区间采用 UTF-16 偏移，与 Kotlin 和
 * JavaScript 字符串位置保持一致。
 */
@Serializable
data class DynamicHighlightSpan(
  val from: Int,
  val to: Int,
  val styleIds: List<String>,
)

/** 动态语言包返回的单个补全候选。 */
@Serializable
data class DynamicCompletionItem(
  val label: String,
  val displayLabel: String? = null,
  val detail: String? = null,
  val info: String? = null,
  val type: String? = null,
  val boost: Int = 0,
  val apply: String? = null,
)

/**
 * 一次补全查询的结果。
 *
 * [from] 与 [to] 表示应用候选时要替换的源码区间；无可用补全时返回 `null`。
 */
@Serializable
data class DynamicCompletionResult(
  val from: Int,
  val to: Int,
  val options: List<DynamicCompletionItem>,
)

/**
 * 由 npm JavaScript 包实现的动态语言能力。
 *
 * 业务将 `DynamicLanguageService::class`、npm 包名和版本传给 `NpmJsServiceLoader.load` 获取端上
 * 代理，不直接访问生成类或 JavaScript Runtime。Kotlin/JS 发布模块只需提供一个实现本接口的
 * object，KSP 会生成分发器。
 */
@NpmJsService
interface DynamicLanguageService : NpmJsServiceInstance {

  /** 返回当前包的语言标识与协议版本。 */
  suspend fun metadata(): DynamicLanguageMetadata

  /** 分析完整源码并返回按 UTF-16 偏移排序的高亮区间。 */
  suspend fun highlight(source: String): List<DynamicHighlightSpan>

  /**
   * 查询指定光标位置的补全候选。
   *
   * @param source 当前完整源码。
   * @param position 光标 UTF-16 偏移。
   * @param explicit 是否由用户主动触发补全。
   */
  suspend fun complete(
    source: String,
    position: Int,
    explicit: Boolean,
  ): DynamicCompletionResult?
}
