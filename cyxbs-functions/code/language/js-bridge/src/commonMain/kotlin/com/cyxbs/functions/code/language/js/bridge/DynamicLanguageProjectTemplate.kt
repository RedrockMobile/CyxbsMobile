package com.cyxbs.functions.code.language.js.bridge

import kotlinx.serialization.Serializable

/** 动态语言包为新建项目提供的单个初始源码文件。 */
@Serializable
data class DynamicLanguageProjectFile(
  /** 相对于项目根目录的规范路径。 */
  val path: String,
  /** 首次创建文件时写入的完整源码。 */
  val source: String,
)

/**
 * 动态语言包提供的默认项目模板。
 *
 * 客户端只负责校验路径并把 [sourceFiles] 原样写入用户目录，不内置任何语言源码。每门语言当前
 * 提供一个适合教学和快速运行的最小模板；未来如需多种项目类型，应新增独立协议方法，不能改变
 * 本结构既有字段的含义。
 */
@Serializable
data class DynamicLanguageProjectTemplate(
  /** 创建弹窗使用的默认项目名称。 */
  val defaultProjectName: String,
  /** 创建完成后首先打开的文件，必须存在于 [sourceFiles]。 */
  val activeFilePath: String,
  /** 项目初始源码；路径必须唯一且至少包含一个文件。 */
  val sourceFiles: List<DynamicLanguageProjectFile>,
)
