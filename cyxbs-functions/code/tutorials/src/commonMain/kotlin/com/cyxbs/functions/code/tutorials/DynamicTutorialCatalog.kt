package com.cyxbs.functions.code.tutorials

import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialService
import kotlinx.serialization.Serializable

/** 静态教程 Catalog npm 包中 `catalog.json` 的完整快照。 */
@Serializable
data class DynamicTutorialCatalog(
  val tutorials: List<DynamicTutorialInfo>,
)

/**
 * 一门可动态下载的语言教程定义。
 *
 * Catalog 只保存稳定 npm 坐标，不锁版本；具体课程清单由 [DynamicTutorialService.manifest] 在
 * 用户展开对应语言课程时惰性读取。
 */
@Serializable
data class DynamicTutorialInfo(
  val languageId: String,
  val displayName: String,
  val npmPackageName: String,
  val aliases: List<String> = emptyList(),
)
