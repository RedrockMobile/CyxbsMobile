package com.cyxbs.functions.code.language

import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageService
import kotlinx.serialization.Serializable

/**
 * 静态 Catalog npm 包中 `catalog.json` 的完整快照。
 *
 * Catalog 采用只追加字段的兼容策略：已有字段不得删除或改变含义，新增字段应当可选并提供默认
 * 行为。端上会忽略未知字段，使旧客户端仍可读取新版 Catalog。
 *
 * @param languages 当前可供客户端下载的语言定义。
 */
@Serializable
data class DynamicLanguageCatalog(
  val languages: List<DynamicLanguageInfo>,
)

/**
 * 一个可动态下载的语言定义。
 *
 * Catalog 只声明稳定 npm 坐标，不锁定版本；端上加载语言时按 npm 的 `latest` 策略在 Runtime
 * 创建前完成更新检查。扩展名不包含前导点，并统一使用小写。
 *
 * @param languageId 语言稳定标识，例如 `javascript`。
 * @param displayName 面向教学 UI 展示的名称。
 * @param npmPackageName 实现 [DynamicLanguageService] 的完整 npm 包名。
 * @param aliases 可用于查找语言的短名称，例如 `js`。
 * @param fileExtensions 该语言识别的文件扩展名。
 */
@Serializable
data class DynamicLanguageInfo(
  val languageId: String,
  val displayName: String,
  val npmPackageName: String,
  val aliases: List<String> = emptyList(),
  val fileExtensions: List<String> = emptyList(),
)
