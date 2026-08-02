package com.cyxbs.functions.code.language.internal

import kotlinx.serialization.Serializable

/** 动态语言包元数据的内部 JSON 传输模型。 */
@Serializable
internal data class DynamicLanguageMetadataDto(
  val languageId: String,
  val displayName: String,
  val protocolVersion: Int,
)

/** 高亮区间的内部 JSON 传输模型。 */
@Serializable
internal data class DynamicHighlightSpanDto(
  val from: Int,
  val to: Int,
  val classes: String,
)

/** 补全结果的内部 JSON 传输模型。 */
@Serializable
internal data class DynamicCompletionResultDto(
  val from: Int,
  val to: Int,
  val options: List<DynamicCompletionItemDto>,
)

/** 补全候选的内部 JSON 传输模型。 */
@Serializable
internal data class DynamicCompletionItemDto(
  val label: String,
  val displayLabel: String? = null,
  val detail: String? = null,
  val info: String? = null,
  val type: String? = null,
  val boost: Int = 0,
  val apply: String? = null,
)
