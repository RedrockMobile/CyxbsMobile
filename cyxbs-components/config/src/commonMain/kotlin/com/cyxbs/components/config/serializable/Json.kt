package com.cyxbs.components.config.serializable

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/**
 * .
 *
 * @author 985892345
 * @date 2024/12/28
 */

@OptIn(ExperimentalSerializationApi::class)
val defaultJson = Json {
  encodeDefaults = false // 不需要编码默认值
  ignoreUnknownKeys = true // 忽略未知键
  isLenient = true // 宽松模式，允许键和字符串值不带引号
  allowTrailingComma = true // 允许尾随逗号
}

/**
 * 用于在 commonMain 模块中防止数据类字段被混淆，主要目的是为了兼容安卓旧代码，让 Gson 能正常进行序列化
 *
 * 迁移至 kotlinx.serialization 后若未 Gson 必要，则可以不使用
 */
interface GsonDataBean
