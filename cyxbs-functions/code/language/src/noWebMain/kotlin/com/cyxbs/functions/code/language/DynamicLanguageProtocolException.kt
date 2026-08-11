package com.cyxbs.functions.code.language

/** Catalog 缺少必要字段、语言身份冲突，或包含端上无法安全使用的数据。 */
class DynamicLanguageProtocolException(
  message: String,
  cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
