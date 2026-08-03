package com.cyxbs.functions.code.npm.model

import kotlinx.serialization.Serializable

/**
 * 后端原子发布的 npm 精确依赖快照。
 *
 * [entries] 以入口包名为键、QuickJS 需要加载的入口 Module 名称为值；[urls] 是整个快照共用的
 * npm tarball 下载源；[packages] 在同一个快照内每个包名只能对应一个精确版本。该模型只负责 JSON
 * 传输，完整结构校验由下载器在发起网络请求前完成。
 *
 * @param releaseTime 后端快照发布时间，格式为 `yyyy.MM.dd HH:mm:ss`。
 * @param entries 可按需加载的入口包及其入口 Module。
 * @param urls 所有包共用的下载源基础地址，按声明顺序回退。
 * @param packages 快照锁定的全部 npm 包。
 */
@Serializable
data class NpmReleaseSnapshot(
  val releaseTime: String,
  val entries: Map<String, String>,
  val urls: List<String>,
  val packages: Map<String, NpmLockedPackage>,
)

/**
 * 快照中单个 npm 包的精确下载信息。
 *
 * [dependencies] 只保存依赖包名，版本统一从当前快照的 [NpmReleaseSnapshot.packages] 读取。客户端不会
 * 使用 npm registry 返回的 dependencies 字段重新解析依赖。
 */
@Serializable
data class NpmLockedPackage(
  val version: String,
  val dependencies: List<String> = emptyList(),
  val integrity: String,
)
